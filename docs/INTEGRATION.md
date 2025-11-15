# Integration Guide
## Application Integration Patterns, Query Usage & Concurrency Handling

**Version**: 1.0
**Last Updated**: 2025-11-15

---

## Table of Contents
1. [Application Architecture](#application-architecture)
2. [Common Query Patterns](#common-query-patterns)
3. [Transaction Boundaries](#transaction-boundaries)
4. [Concurrency Handling](#concurrency-handling)
5. [Error Handling](#error-handling)
6. [Best Practices](#best-practices)

---

## Application Architecture

### Three-Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          TicTacToeScreen.kt (Compose UI)            │   │
│  │  - User input handling                               │   │
│  │  - State management (board, status, players)         │   │
│  │  - UI rendering (3×3 grid, buttons, text)           │   │
│  │  - Coroutine scope for async operations             │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ Kotlin suspend functions
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     BUSINESS LOGIC LAYER                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Blockchain.kt (DAO Object)              │   │
│  │  - Contract interaction (read/write)                 │   │
│  │  - ABI encoding/decoding                             │   │
│  │  - Transaction signing & submission                  │   │
│  │  - Receipt polling & confirmation                    │   │
│  │  - Network management (local ↔ Sepolia)            │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ JSON-RPC over HTTP
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Ethereum Blockchain (Smart Contracts)      │   │
│  │  - Factory Contract (game creation)                  │   │
│  │  - Game Contract Instances (game state)              │   │
│  │  - Player Accounts (authentication)                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

### Component Responsibilities

#### Presentation Layer (TicTacToeScreen.kt)

**File**: `src/main/kotlin/vision/salient/TicTacToeScreen.kt:42-301`

**Responsibilities**:
1. **State Management**:
   ```kotlin
   var status by remember { mutableStateOf<String?>(null) }
   var factoryAddr by remember { mutableStateOf<String?>(null) }
   var gameAddr by remember { mutableStateOf<String?>(null) }
   var board by remember { mutableStateOf<List<List<String>>?>(null) }
   var currentPlayerIdx by remember { mutableStateOf(0) }
   ```

2. **User Input Handling**:
   ```kotlin
   OutlinedTextField(
       value = gameAddr ?: "",
       onValueChange = { gameAddr = it },
       label = { Text("Game Address") }
   )
   ```

3. **Async Operation Launching**:
   ```kotlin
   scope.launch {
       status = "Creating game…"
       val addr = Blockchain.createGameByPlayer(0)
       gameAddr = addr
       status = addr ?: "Create failed"
   }
   ```

4. **UI Rendering**:
   ```kotlin
   board?.let { rows ->
       Column {
           rows.forEach { row ->
               Row {
                   row.forEach { owner ->
                       Card { /* Cell display */ }
                   }
               }
           }
       }
   }
   ```

---

#### Business Logic Layer (Blockchain.kt)

**File**: `src/main/kotlin/vision/salient/Blockchain.kt:39-621`

**Responsibilities**:
1. **Configuration Management**:
   ```kotlin
   private val dotenv = dotenv { ignoreIfMissing = true }
   private var localFlag = dotenv["LOCAL"]?.toBoolean() ?: true

   fun applyLocal(flag: Boolean) {
       localFlag = flag
       deploymentInfo = loadDeploymentInfo()
       httpService = HttpService(RPC_URL)
       web3j = Web3j.build(httpService)
   }
   ```

2. **Contract Interaction**:
   ```kotlin
   suspend fun getBoardState(): List<List<String>> {
       val fn = Function("getBoardState", ...)
       val callData = FunctionEncoder.encode(fn)
       val raw = web3j.ethCall(...).send().result
       return decode(raw)
   }
   ```

3. **Transaction Management**:
   ```kotlin
   private suspend fun sendTransaction(...): String {
       val nonce = web3j.ethGetTransactionCount(...).send()
       val gasPrice = calculateGasPrice()
       val gasLimit = estimateGas()
       val signed = TransactionEncoder.signMessage(...)
       return web3j.ethSendRawTransaction(signed).send().transactionHash
   }
   ```

4. **Error Handling**:
   ```kotlin
   try {
       val decoded = FunctionReturnDecoder.decode(...)
   } catch (decodeErr: Exception) {
       // Fallback to static array parsing
   }
   ```

---

## Common Query Patterns

### Pattern 1: Read-Modify-Write (Game Move)

**Use Case**: Player makes a move

**Flow**:
```kotlin
// 1. READ: Get current board state
val board = Blockchain.getBoardState()

// 2. VALIDATE: Check if cell is empty
if (board[row][col] != ZERO_ADDRESS) {
    status = "Cell already occupied"
    return
}

// 3. WRITE: Submit move transaction
try {
    val txHash = Blockchain.makeMove(currentPlayerIdx, row, col)
    status = "Move submitted: $txHash"

    // 4. WAIT: Poll for confirmation
    // (handled internally by sendTransaction + waitForReceipt)

    // 5. RE-READ: Refresh board state
    val updatedBoard = Blockchain.getBoardState()
    board = updatedBoard

    // 6. CHECK: Verify game status
    val ended = Blockchain.readBool("gameEnded")
    if (ended) {
        val winner = Blockchain.readAddress("winner")
        status = if (winner == ZERO_ADDRESS) "Draw!" else "Winner: $winner"
    }
} catch (e: Exception) {
    status = "Move failed: ${e.message}"
}
```

**Transaction Boundary**:
- **Start**: `makeMove()` call
- **End**: Receipt confirmed (240 sec timeout)
- **Atomicity**: Enforced by blockchain (all-or-nothing)

---

### Pattern 2: Create-and-Monitor (Game Creation)

**Use Case**: User creates new game instance

**Flow**:
```kotlin
// 1. VALIDATE: Ensure factory is loaded
val factory = Blockchain.getFactoryAddress()
if (factory == null) {
    status = "Factory not deployed. Click 'Load Factory'"
    return
}

// 2. CREATE: Deploy new game via factory
status = "Creating game…"
try {
    val gameAddress = withContext(Dispatchers.IO) {
        Blockchain.createGameByPlayer(playerIdx = 0)
    }

    if (gameAddress == null) {
        status = "Game creation failed (no address returned)"
        return
    }

    // 3. REGISTER: Set as current game
    Blockchain.setCurrentGameAddress(gameAddress)
    gameAddr = gameAddress

    // 4. LOAD: Fetch initial board state
    board = withContext(Dispatchers.IO) {
        Blockchain.getBoardState()
    }

    status = "Game created: $gameAddress"
} catch (e: Exception) {
    status = "Creation error: ${e.message}"
}
```

**Transaction Boundary**:
- **Start**: `createGameByPlayer()` call
- **End**: GameCreated event parsed, address extracted
- **Side Effects**: `currentGameAddress` updated in Blockchain.kt

---

### Pattern 3: Batch Read (Game History)

**Use Case**: Display game history with move replay

**Flow**:
```kotlin
suspend fun loadGameHistory(gameAddress: String): GameHistory =
    withContext(Dispatchers.IO) {
        // 1. SET CONTEXT: Switch to target game
        Blockchain.setCurrentGameAddress(gameAddress)

        // 2. PARALLEL READS: Fetch all game data simultaneously
        val deferredBoard = async { Blockchain.getBoardState() }
        val deferredEnded = async { Blockchain.readBool("gameEnded") }
        val deferredWinner = async {
            if (deferredEnded.await())
                Blockchain.readAddress("winner")
            else null
        }
        val deferredMoves = async { getGameMoves() }

        // 3. AWAIT ALL: Collect results
        GameHistory(
            address = gameAddress,
            board = deferredBoard.await(),
            gameEnded = deferredEnded.await(),
            winner = deferredWinner.await(),
            moves = deferredMoves.await()
        )
    }

data class GameHistory(
    val address: String,
    val board: List<List<String>>,
    val gameEnded: Boolean,
    val winner: String?,
    val moves: List<MoveEvent>
)
```

**Performance**:
- Sequential: 4 RPC calls × 200ms = 800ms
- Parallel: max(200ms) = 200ms (4× speedup)

---

### Pattern 4: Reactive UI Updates (LaunchedEffect)

**Use Case**: Auto-refresh board when game address changes

**Flow** (TicTacToeScreen.kt:62-68):
```kotlin
LaunchedEffect(gameAddr) {
    if (!gameAddr.isNullOrBlank()) {
        status = "Loading board…"
        board = try {
            withContext(Dispatchers.IO) {
                Blockchain.getBoardState()
            }
        } catch (_: Exception) {
            null
        }
        status = board?.let { "Board loaded" } ?: "Failed to load board"
    }
}
```

**Trigger**: `gameAddr` state changes (set by user or createGame)
**Effect**: Automatically loads board for new game

---

### Pattern 5: Optimistic UI Updates

**Use Case**: Show move immediately, revert on failure

**Flow**:
```kotlin
suspend fun makeMoveOptimistic(playerIdx: Int, row: Int, col: Int) {
    // 1. OPTIMISTIC UPDATE: Immediately update UI
    val playerAddr = Blockchain.getPlayerCredentials(playerIdx).address
    val optimisticBoard = board!!.toMutableList().map { it.toMutableList() }
    optimisticBoard[row][col] = playerAddr
    board = optimisticBoard

    status = "Submitting move…"

    // 2. SUBMIT TRANSACTION: Send to blockchain
    try {
        Blockchain.makeMove(playerIdx, row, col)

        // 3. CONFIRM: Refresh from chain
        board = Blockchain.getBoardState()
        status = "Move confirmed"
    } catch (e: Exception) {
        // 4. REVERT: Restore previous state
        board = Blockchain.getBoardState()  // Fetch true state
        status = "Move failed: ${e.message}"
    }
}
```

**Benefits**:
- Instant UI feedback (no 2-15 second wait)
- Better UX for fast interactions

**Risks**:
- Transaction may fail (cell already taken, wrong turn)
- Must revert optimistic update on failure

---

## Transaction Boundaries

### ACID Properties in Blockchain Context

| Property | Traditional DB | Ethereum Blockchain |
|----------|---------------|---------------------|
| **Atomicity** | ✅ Rollback on error | ✅ Revert on error (all state changes undone) |
| **Consistency** | ✅ Constraints enforced | ✅ Solidity require() statements |
| **Isolation** | ⚠️ Depends on isolation level | ❌ No isolation (public mempool) |
| **Durability** | ✅ Write-ahead log | ✅ Immutable block history |

### Isolation Anomalies

#### Problem: Front-Running

**Scenario**: Player A and Player B both try to claim cell [1,1]

```
Timeline:
  T1: Player A submits move(1,1) with gas price 20 gwei
  T2: Player B sees A's transaction in mempool
  T3: Player B submits move(1,1) with gas price 50 gwei (higher)
  T4: Miner includes B's transaction first (higher fee)
  T5: Miner includes A's transaction → REVERTS (cell occupied)

Result: Player A paid gas for failed transaction
```

**Mitigation**:
```solidity
// In smart contract
function makeMove(uint8 row, uint8 col, bytes32 commitment) external {
    // Commit-reveal scheme prevents front-running
    require(keccak256(abi.encode(row, col, msg.sender)) == commitment,
            "Invalid commitment");
    // ... rest of logic
}
```

**Kotlin Implementation**:
```kotlin
fun generateCommitment(row: Int, col: Int, player: String): ByteArray {
    val encoded = FunctionEncoder.encodeConstructor(
        listOf(Uint8(row.toLong()), Uint8(col.toLong()), Address(player))
    )
    return Hash.sha3(encoded.toByteArray())
}
```

---

#### Problem: Race Conditions

**Scenario**: Two players alternate moves too quickly

```
Player 1 Move Sequence:
  T1: Read lastPlayer = 0x0 (empty)
  T2: Submit move(0,0) [Transaction pending...]
  T3: Transaction confirmed, lastPlayer = Player1

Player 2 Move Sequence:
  T1.5: Read lastPlayer = 0x0 (still empty, P1's tx not confirmed)
  T2.5: Submit move(1,1) [Should be valid, but...]
  T3.5: Transaction confirmed AFTER P1 → lastPlayer was already Player1
  T4: Contract checks: lastPlayer == msg.sender? NO → REVERT

Result: Player 2's transaction reverted due to stale read
```

**Solution**: Client-side validation with retry

```kotlin
suspend fun makeMoveWithRetry(playerIdx: Int, row: Int, col: Int, maxRetries: Int = 3) {
    repeat(maxRetries) { attempt ->
        try {
            // Fresh read before each attempt
            val currentTurn = getCurrentTurn()
            val myAddress = Blockchain.getPlayerCredentials(playerIdx).address

            if (currentTurn.lowercase() != myAddress.lowercase()) {
                delay(2000)  // Wait for other player's turn to complete
                return@repeat  // Retry
            }

            Blockchain.makeMove(playerIdx, row, col)
            return  // Success
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(1000 * (attempt + 1))  // Exponential backoff
        }
    }
}
```

---

### Transaction Lifecycle

```
┌───────────────────────────────────────────────────────────────┐
│                    TRANSACTION LIFECYCLE                       │
└───────────────────────────────────────────────────────────────┘

1. CONSTRUCTION (Client-side)
   ├─ Encode function call (ABI encoding)
   ├─ Get nonce (ethGetTransactionCount)
   ├─ Estimate gas limit (ethEstimateGas)
   ├─ Fetch gas price (ethGasPrice)
   └─ Build RawTransaction object

2. SIGNING (Client-side)
   ├─ Load private key from credentials
   ├─ Sign transaction with ECDSA (secp256k1)
   └─ Produce signed transaction bytes

3. BROADCAST (Client → Node)
   ├─ Send via ethSendRawTransaction RPC
   ├─ Node validates signature & nonce
   └─ Returns transaction hash (not yet mined)

4. MEMPOOL (Pending)
   ├─ Transaction sits in mempool
   ├─ Miners/validators see transaction
   └─ Sorted by gas price (fee market)

5. INCLUSION (Block Production)
   ├─ Miner selects high-fee transactions
   ├─ Executes transaction in EVM
   ├─ If success: Include in block
   └─ If revert: Exclude (or include to claim gas)

6. PROPAGATION (Network)
   ├─ Block broadcast to all nodes
   ├─ Nodes validate block
   └─ Block added to chain

7. CONFIRMATION (Finality)
   ├─ Receipt available (ethGetTransactionReceipt)
   ├─ Wait N confirmations (1+ blocks after)
   └─ Consider finalized (12+ blocks for safety)

8. STATE UPDATE (Permanent)
   └─ Contract state changes committed to blockchain
```

**Timing** (Sepolia Testnet):
- Steps 1-3: ~100-500ms (client-side)
- Step 4: ~2-10 seconds (mempool wait)
- Step 5: ~12 seconds (block time)
- Step 6: ~1-2 seconds (propagation)
- Step 7: ~12-144 seconds (1-12 confirmations)

---

## Concurrency Handling

### Concurrency Model: Coroutines

**Framework**: Kotlin Coroutines
**Dispatcher**: `Dispatchers.IO` for blocking I/O (RPC calls)

```kotlin
// All blockchain operations run on IO dispatcher
suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
    // Blocking web3j call runs on background thread pool
    web3j.ethCall(...).send().result
}
```

---

### Pattern 1: Sequential Operations

**Use Case**: Operations depend on previous results

```kotlin
suspend fun createAndJoinGame(): String? = withContext(Dispatchers.IO) {
    // MUST be sequential: need address before joining
    val gameAddress = Blockchain.createGameByPlayer(0)
        ?: return@withContext null

    Blockchain.setCurrentGameAddress(gameAddress)

    val board = Blockchain.getBoardState()

    println("Created and joined game: $gameAddress")
    gameAddress
}
```

---

### Pattern 2: Parallel Operations

**Use Case**: Independent reads (no dependencies)

```kotlin
suspend fun loadGameDetails(gameAddress: String): GameDetails =
    withContext(Dispatchers.IO) {
        Blockchain.setCurrentGameAddress(gameAddress)

        // Launch all reads in parallel
        val board = async { Blockchain.getBoardState() }
        val ended = async { Blockchain.readBool("gameEnded") }
        val winner = async { Blockchain.readAddress("winner") }
        val lastPlayer = async { Blockchain.readAddress("lastPlayer") }

        // Await all results
        GameDetails(
            address = gameAddress,
            board = board.await(),
            gameEnded = ended.await(),
            winner = winner.await(),
            lastPlayer = lastPlayer.await()
        )
    }

data class GameDetails(
    val address: String,
    val board: List<List<String>>,
    val gameEnded: Boolean,
    val winner: String,
    val lastPlayer: String
)
```

**Performance**: 4× speedup (4 parallel calls vs 4 sequential)

---

### Pattern 3: Mutex for Shared State

**Use Case**: Prevent concurrent transaction submissions

```kotlin
object TransactionLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.withLock {
            return block()
        }
    }
}

// Usage: Ensure only one transaction at a time per player
suspend fun makeMoveThreadSafe(playerIdx: Int, row: Int, col: Int): String {
    return TransactionLock.withLock {
        Blockchain.makeMove(playerIdx, row, col)
    }
}
```

**Prevents**: Double-spending nonce (two transactions with same nonce)

---

### Nonce Management (Critical for Concurrency)

**Problem**: Concurrent transactions from same account use same nonce

```kotlin
// WRONG: Race condition
suspend fun submitTwoMoves() {
    launch { Blockchain.makeMove(0, 0, 0) }  // Gets nonce 5
    launch { Blockchain.makeMove(0, 1, 1) }  // Also gets nonce 5 → CONFLICT
}

// Both transactions will have nonce 5, one will be rejected
```

**Solution 1**: Sequential submission

```kotlin
suspend fun submitTwoMoves() {
    Blockchain.makeMove(0, 0, 0)  // Nonce 5, wait for confirmation
    Blockchain.makeMove(0, 1, 1)  // Nonce 6 (after 0,0 confirmed)
}
```

**Solution 2**: Manual nonce tracking

```kotlin
object NonceManager {
    private val nonces = mutableMapOf<String, BigInteger>()
    private val mutex = Mutex()

    suspend fun getNextNonce(address: String): BigInteger = mutex.withLock {
        val current = nonces[address] ?: Blockchain.getNonce(address)
        nonces[address] = current + BigInteger.ONE
        current
    }

    suspend fun refresh(address: String) = mutex.withLock {
        nonces[address] = Blockchain.getNonce(address)
    }
}

// Modified sendTransaction
private suspend fun sendTransaction(...): String {
    val nonce = NonceManager.getNextNonce(from.address)
    // ... rest of logic
}
```

---

## Error Handling

### Error Categories

#### Category 1: Network Errors

**Causes**: RPC timeout, connection refused, DNS failure

**Example**:
```kotlin
try {
    val board = Blockchain.getBoardState()
} catch (e: java.net.ConnectException) {
    status = "⚠️ Connection failed. Check network/RPC endpoint."
} catch (e: java.net.SocketTimeoutException) {
    status = "⏱️ Request timed out. Try again."
}
```

**Mitigation**:
```kotlin
suspend fun <T> retryOnNetworkError(
    maxAttempts: Int = 3,
    delayMs: Long = 1000,
    block: suspend () -> T
): T {
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            when (e) {
                is java.net.ConnectException,
                is java.net.SocketTimeoutException -> {
                    println("Network error (attempt ${attempt + 1}), retrying...")
                    delay(delayMs * (attempt + 1))
                }
                else -> throw e
            }
        }
    }
    return block()  // Last attempt (throws if fails)
}
```

---

#### Category 2: Contract Revert Errors

**Causes**: Invalid move, wrong turn, game ended

**Example** (TicTacToeScreen.kt:212-215):
```kotlin
catch (e: Exception) {
    val msg = e.localizedMessage ?: e.message ?: "Unknown error"
    status = when {
        msg.contains("revert", true) -> "Invalid move: out of turn / cell taken"
        msg.contains("0x", true) -> "Contract returned invalid data"
        else -> msg
    }
}
```

**Contract Error Messages**:
```solidity
require(!gameEnded, "Game already ended");
require(board[row][col] == address(0), "Cell occupied");
require(msg.sender != lastPlayer, "Not your turn");
```

---

#### Category 3: Decoding Errors

**Causes**: ABI mismatch, contract bug, network issue

**Example** (Blockchain.kt:540-567):
```kotlin
try {
    val decoded = FunctionReturnDecoder.decode(raw, dynFn.outputParameters)
    // ... process decoded data
} catch (decodeErr: Exception) {
    println("…dynamic decode failed → static 3×3 fallback (${decodeErr.message})")

    // Fallback decoding for static arrays
    val flat = (0 until 9).map { i ->
        val word = raw.drop(2).substring(i*64, i*64 + 64)
        "0x" + word.takeLast(40)
    }
    // ... process flat data
}
```

---

#### Category 4: Gas Estimation Failures

**Causes**: Contract will revert, invalid parameters

**Example**:
```kotlin
try {
    val gasLimit = web3j.ethEstimateGas(...).send().amountUsed
} catch (e: Exception) {
    // Estimation failed → transaction would revert
    status = "❌ Transaction will fail. Check move validity."
    return
}
```

---

## Best Practices

### 1. Always Use `withContext(Dispatchers.IO)`

```kotlin
// ✅ CORRECT
suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
    web3j.ethCall(...).send().result
}

// ❌ WRONG (blocks main thread)
suspend fun getBoardState(): List<List<String>> {
    return web3j.ethCall(...).send().result  // Blocking call on UI thread
}
```

---

### 2. Handle Null Results Gracefully

```kotlin
// ✅ CORRECT
val factory = Blockchain.getFactoryAddress()
if (factory == null) {
    status = "Factory not deployed. Run deployment first."
    return
}

// ❌ WRONG (crashes on null)
val factory = Blockchain.getFactoryAddress()!!  // NullPointerException
```

---

### 3. Provide User Feedback for Long Operations

```kotlin
// ✅ CORRECT
scope.launch {
    status = "Creating game… (may take 15 seconds)"
    val addr = Blockchain.createGameByPlayer(0)
    status = if (addr != null) "Game created: $addr" else "Failed"
}

// ❌ WRONG (silent 15-second freeze)
scope.launch {
    val addr = Blockchain.createGameByPlayer(0)
}
```

---

### 4. Validate Input Before Submitting Transaction

```kotlin
// ✅ CORRECT
val r = rowInput.toIntOrNull() ?: -1
val c = colInput.toIntOrNull() ?: -1
if (r !in 0..2 || c !in 0..2) {
    status = "Invalid cell coordinates"
    return@launch
}
Blockchain.makeMove(playerIdx, r, c)

// ❌ WRONG (wastes gas on invalid move)
Blockchain.makeMove(playerIdx, 9, 9)  // Reverts, user pays gas
```

---

### 5. Cache Immutable Data

```kotlin
// ✅ CORRECT
val factoryAddress = remember {
    Blockchain.getFactoryAddress()  // Computed once
}

// ❌ WRONG (re-fetches every recomposition)
val factoryAddress = Blockchain.getFactoryAddress()
```

---

### 6. Use Structured Concurrency

```kotlin
// ✅ CORRECT (scoped to UI lifecycle)
val scope = rememberCoroutineScope()
scope.launch {
    Blockchain.makeMove(...)
}

// ❌ WRONG (leaks coroutine if UI destroyed)
GlobalScope.launch {
    Blockchain.makeMove(...)
}
```

---

### 7. Log Transaction Hashes for Debugging

```kotlin
// ✅ CORRECT
try {
    val txHash = Blockchain.makeMove(playerIdx, row, col)
    println("✅ Move submitted: $txHash")
    println("   View on Etherscan: https://sepolia.etherscan.io/tx/$txHash")
} catch (e: Exception) {
    println("❌ Move failed: ${e.message}")
}
```

---

## Integration Checklist

### Pre-Integration

- [ ] `.env` file configured with RPC URLs and private keys
- [ ] Deployment JSON files in `src/main/resources/`
- [ ] Web3j dependency in `build.gradle.kts`
- [ ] Dotenv-kotlin dependency in `build.gradle.kts`

### Development

- [ ] All blockchain calls wrapped in `withContext(Dispatchers.IO)`
- [ ] Error handling for network failures
- [ ] Error handling for contract reverts
- [ ] User feedback for long-running operations (>1 sec)
- [ ] Input validation before transaction submission
- [ ] Transaction hash logging for debugging

### Testing

- [ ] Test on Hardhat local network (instant confirmations)
- [ ] Test on Sepolia testnet (realistic timing)
- [ ] Test network switching (local ↔ Sepolia)
- [ ] Test concurrent moves (race conditions)
- [ ] Test invalid moves (reverts)
- [ ] Test gas exhaustion scenarios

### Production

- [ ] Multi-RPC failover configured
- [ ] Private keys stored securely (not in code)
- [ ] Gas price monitoring
- [ ] Transaction failure alerts
- [ ] User wallet integration (MetaMask, WalletConnect)
- [ ] Event-based state updates (WebSocket subscriptions)

---

## Next Steps

- [Backup & Recovery](./BACKUP_RECOVERY.md) - Key management and disaster recovery
- [Database Overview](./DATABASE_OVERVIEW.md) - Return to main documentation
