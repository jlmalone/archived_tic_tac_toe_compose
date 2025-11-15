# Entity-Relationship Diagrams
## Tic-Tac-Toe Smart Contract Database Schema

**Version**: 1.0
**Last Updated**: 2025-11-15

---

## Table of Contents
1. [High-Level Architecture](#high-level-architecture)
2. [Detailed Entity-Relationship Diagram](#detailed-entity-relationship-diagram)
3. [Contract Inheritance Hierarchy](#contract-inheritance-hierarchy)
4. [State Machine Diagram](#state-machine-diagram)
5. [Data Flow Diagrams](#data-flow-diagrams)
6. [Storage Layout](#storage-layout)

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ETHEREUM BLOCKCHAIN                       │
│                        (Persistent Storage)                      │
└─────────────────────────────────────────────────────────────────┘
                                  ▲
                                  │ JSON-RPC
                                  │ (eth_call, eth_sendRawTransaction)
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Web3j Client Library                     │
│                    (Query Engine & Transaction Manager)          │
└─────────────────────────────────────────────────────────────────┘
                                  ▲
                                  │ Kotlin API
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Blockchain.kt (DAO Layer)                   │
│  - Connection Management                                         │
│  - ABI Encoding/Decoding                                         │
│  - Transaction Signing                                           │
│  - Receipt Polling                                               │
└─────────────────────────────────────────────────────────────────┘
                                  ▲
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│               TicTacToeScreen.kt (Application Layer)             │
│  - User Interface                                                │
│  - Game State Management                                         │
│  - Player Interaction                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Detailed Entity-Relationship Diagram

### Full Schema with Relationships

```
┌────────────────────────────────────────────────────────────────────┐
│                      FACTORY CONTRACT                              │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Contract Type: Singleton Factory (EIP-1167)                       │
│  Address (Hardhat): 0xA51c1fc2f0D1a1b8494Ed1FE312d7C3a78Ed91C0     │
│  Address (Sepolia): 0xa0B53DbDb0052403E38BBC31f01367aC6782118E     │
│  ──────────────────────────────────────────────────────────────    │
│  📊 State Variables:                                               │
│    • gameImplementation: address  (immutable reference)            │
│    • games: address[] (dynamic array of deployed games)            │
│  ──────────────────────────────────────────────────────────────    │
│  📝 Functions:                                                     │
│    • createGame() → address                                        │
│    • getGames() → address[]                                        │
│  ──────────────────────────────────────────────────────────────    │
│  📢 Events:                                                        │
│    • GameCreated(address indexed gameAddress)                      │
└──────────────────────┬─────────────────────────────────────────────┘
                       │
                       │ Creates (1:N relationship)
                       │ Pattern: Clone Proxy (EIP-1167)
                       ▼
┌────────────────────────────────────────────────────────────────────┐
│                       GAME CONTRACT                                │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Contract Type: Minimal Proxy Clone                                │
│  Address: Dynamic (one per game instance)                          │
│  Implementation: 0xB7f8BC63... (Hardhat) / 0x340AC014... (Sepolia)│
│  ──────────────────────────────────────────────────────────────    │
│  📊 State Variables:                                               │
│  ┌──────────────────────────────────────────────────────────┐     │
│  │  board: address[3][3]                                    │     │
│  │  ┌─────────┬─────────┬─────────┐                        │     │
│  │  │ [0][0]  │ [0][1]  │ [0][2]  │  Row 0                 │     │
│  │  ├─────────┼─────────┼─────────┤                        │     │
│  │  │ [1][0]  │ [1][1]  │ [1][2]  │  Row 1                 │     │
│  │  ├─────────┼─────────┼─────────┤                        │     │
│  │  │ [2][0]  │ [2][1]  │ [2][2]  │  Row 2                 │     │
│  │  └─────────┴─────────┴─────────┘                        │     │
│  │  Each cell: address (0x0 = empty, player address = owned)│    │
│  └──────────────────────────────────────────────────────────┘     │
│    • gameEnded: bool                                               │
│    • winner: address (0x0 = draw/no winner yet)                   │
│    • lastPlayer: address                                           │
│    • player1: address (game creator)                               │
│    • player2: address (second player, set on first opponent move) │
│  ──────────────────────────────────────────────────────────────    │
│  📝 Functions:                                                     │
│    • makeMove(uint8 row, uint8 col)                               │
│    • getBoardState() → address[3][3]                               │
│    • gameEnded() → bool (public getter)                            │
│    • winner() → address (public getter)                            │
│    • lastPlayer() → address (public getter)                        │
│  ──────────────────────────────────────────────────────────────    │
│  📢 Events:                                                        │
│    • MoveMade(address indexed player, uint8 row, uint8 col)       │
│    • GameEnded(address indexed winner, bool isDraw)                │
└──────────────────────┬─────────────────────────────────────────────┘
                       │
                       │ References (N:1 relationship)
                       │ Foreign Key: board[i][j] → Player.address
                       │              lastPlayer → Player.address
                       │              winner → Player.address
                       ▼
┌────────────────────────────────────────────────────────────────────┐
│                    PLAYER (EXTERNAL ACCOUNT)                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  Entity Type: Ethereum Externally Owned Account (EOA)              │
│  Storage: Client-side only (private keys)                          │
│  ──────────────────────────────────────────────────────────────    │
│  📊 Attributes:                                                    │
│    • address: bytes20 (PRIMARY KEY)                                │
│    • privateKey: bytes32 (off-chain, never transmitted)            │
│    • nonce: uint256 (managed by Ethereum network)                  │
│    • balance: uint256 (ETH balance for gas fees)                   │
│  ──────────────────────────────────────────────────────────────    │
│  🔐 Authentication:                                                │
│    • Private Key → ECDSA Signature → Transaction Authorization     │
│    • Address derived from: keccak256(publicKey)[12:]               │
│  ──────────────────────────────────────────────────────────────    │
│  💰 Gas Costs:                                                     │
│    • Required for all state-changing operations                    │
│    • Read operations (view/pure) are free                          │
└────────────────────────────────────────────────────────────────────┘
```

---

## Contract Inheritance Hierarchy

```
┌──────────────────────────┐
│   EIP-1167 Standard      │
│   (Minimal Proxy)        │
└────────────┬─────────────┘
             │
             │ implements
             ▼
┌──────────────────────────┐
│   Factory Contract       │
│  ──────────────────────  │
│  + createGame()          │
│  + games[]               │
└────────────┬─────────────┘
             │
             │ creates clones of
             ▼
┌──────────────────────────┐
│ Game Implementation      │
│  ──────────────────────  │
│  + board[3][3]           │
│  + makeMove()            │
│  + getBoardState()       │
│  + _checkWinner()        │
└──────────────────────────┘
```

---

## State Machine Diagram

### Game Lifecycle States

```
┌────────────────────┐
│   NOT CREATED      │  Initial state (game doesn't exist)
└─────────┬──────────┘
          │
          │ Factory.createGame()
          │ Emits: GameCreated(address)
          ▼
┌────────────────────┐
│   CREATED          │  Game deployed, board is empty
│   gameEnded=false  │  winner=0x0, lastPlayer=0x0
│   board=0x0[3][3]  │
└─────────┬──────────┘
          │
          │ Player1.makeMove(row, col)
          │ Emits: MoveMade(player, row, col)
          ▼
┌────────────────────┐
│   IN PROGRESS      │  Players alternating moves
│   gameEnded=false  │  lastPlayer != 0x0
│   board has moves  │  winner=0x0
└─────────┬──────────┘
          │
          │ Three possible transitions:
          │
          ├──────────────────────┬──────────────────────┐
          ▼                      ▼                      ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│  PLAYER1 WINS   │   │  PLAYER2 WINS   │   │      DRAW       │
│  gameEnded=true │   │  gameEnded=true │   │  gameEnded=true │
│  winner=Player1 │   │  winner=Player2 │   │  winner=0x0     │
└─────────────────┘   └─────────────────┘   └─────────────────┘
          │                      │                      │
          └──────────────────────┴──────────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │   FINALIZED     │  Terminal state
                        │  gameEnded=true │  No further moves allowed
                        │  Immutable      │
                        └─────────────────┘
```

---

## Data Flow Diagrams

### Read Operation Flow (eth_call)

```
┌──────────────┐
│   UI Layer   │  User clicks "Refresh Board"
└──────┬───────┘
       │
       │ Blockchain.getBoardState()
       ▼
┌──────────────────┐
│  Blockchain.kt   │  1. Encode function call
└──────┬───────────┘     Function("getBoardState", [], [...])
       │                 FunctionEncoder.encode(fn)
       │
       │ 2. Create eth_call transaction
       ▼
┌────────────────────┐
│     Web3j          │  Transaction.createEthCallTransaction(
│  (HTTP Service)    │    from: player_address,
└──────┬─────────────┘    to: game_address,
       │                   data: encoded_function
       │                 )
       │
       │ 3. JSON-RPC: eth_call
       ▼
┌──────────────────────┐
│  Ethereum Network    │  Execute function in EVM simulation
│  (Read-only EVM)     │  No state change, no gas cost
└──────┬───────────────┘
       │
       │ 4. Return ABI-encoded result
       │    0x[576 hex chars] = 9 words × 32 bytes
       ▼
┌────────────────────┐
│  Blockchain.kt     │  5. Decode response
└──────┬─────────────┘     - Try DynamicArray decoder
       │                   - Fallback: manual 3×3 slice (lines 559-567)
       │
       │ 6. Return List<List<String>>
       ▼
┌──────────────┐
│  UI Layer    │  Display 3×3 grid with emoji markers
└──────────────┘
```

### Write Operation Flow (eth_sendRawTransaction)

```
┌──────────────┐
│   UI Layer   │  User enters row=1, col=2, clicks "Make Move"
└──────┬───────┘
       │
       │ Blockchain.makeMove(playerIdx=0, row=1, col=2)
       ▼
┌──────────────────┐
│  Blockchain.kt   │  1. Encode function with parameters
└──────┬───────────┘     Function("makeMove", [Uint8(1), Uint8(2)], [])
       │                 data = FunctionEncoder.encode(fn)
       │
       │ 2. Get transaction parameters
       ▼
┌────────────────────┐
│  sendTransaction() │  a. Get nonce (ethGetTransactionCount)
│  (lines 374-407)   │  b. Calculate gas price (market × 1.5)
└──────┬─────────────┘  c. Estimate gas limit (estimate × 1.5)
       │                d. Create RawTransaction
       │
       │ 3. Sign transaction with private key
       ▼
┌──────────────────┐
│  Credentials     │  TransactionEncoder.signMessage(
│  (Private Key)   │    rawTx, chainId, credentials
└──────┬───────────┘  ) → signed hex string
       │
       │ 4. Send raw transaction
       ▼
┌────────────────────┐
│     Web3j          │  ethSendRawTransaction(signedHex)
│  (HTTP Service)    │  Returns: transaction hash
└──────┬─────────────┘
       │
       │ 5. Transaction enters mempool
       ▼
┌──────────────────────┐
│  Ethereum Network    │  Miner/validator includes tx in block
│  (Block Production)  │  State changes applied
└──────┬───────────────┘  Events emitted: MoveMade(...)
       │
       │ 6. Wait for receipt (120 retries × 2 sec)
       ▼
┌────────────────────┐
│  waitForReceipt()  │  Poll ethGetTransactionReceipt()
│  (lines 409-418)   │  Until receipt.isPresent == true
└──────┬─────────────┘
       │
       │ 7. Return transaction hash
       ▼
┌──────────────┐
│  UI Layer    │  Display "Move OK" and refresh board
└──────────────┘
```

---

## Storage Layout

### Factory Contract Storage Slots

```
Slot 0: gameImplementation (address, 20 bytes)
Slot 1: games.length (uint256, 32 bytes)
Slot 2+: games[0], games[1], ... (address, 20 bytes each)
         Storage: keccak256(1) + index
```

### Game Contract Storage Slots (Proxy Pattern)

```
Proxy Implementation:
  Slot 0: Implementation address (points to game logic)

Delegated Storage (in proxy contract's storage):
  Slot 0: board[0][0] (address, 20 bytes)
  Slot 1: board[0][1]
  Slot 2: board[0][2]
  Slot 3: board[1][0]
  Slot 4: board[1][1]
  Slot 5: board[1][2]
  Slot 6: board[2][0]
  Slot 7: board[2][1]
  Slot 8: board[2][2]
  Slot 9: gameEnded (bool, 1 byte)
  Slot 10: winner (address, 20 bytes)
  Slot 11: lastPlayer (address, 20 bytes)
  Slot 12: player1 (address, 20 bytes)
  Slot 13: player2 (address, 20 bytes)
```

### Memory Layout During getBoardState() Call

```
ABI-encoded return data (static array):
Offset   | Data
---------|-------------------------------------------------------
0x00     | [12 bytes 0x00][20 bytes board[0][0]]
0x20     | [12 bytes 0x00][20 bytes board[0][1]]
0x40     | [12 bytes 0x00][20 bytes board[0][2]]
0x60     | [12 bytes 0x00][20 bytes board[1][0]]
0x80     | [12 bytes 0x00][20 bytes board[1][1]]
0xA0     | [12 bytes 0x00][20 bytes board[1][2]]
0xC0     | [12 bytes 0x00][20 bytes board[2][0]]
0xE0     | [12 bytes 0x00][20 bytes board[2][1]]
0x100    | [12 bytes 0x00][20 bytes board[2][2]]

Total: 288 bytes (9 words × 32 bytes/word)
```

---

## Indexing Strategy (Events)

### Event Signatures (Topics)

```
Event Topic Calculation:
  topic[0] = keccak256("GameCreated(address)")
  topic[1] = indexed gameAddress (if indexed)

For efficient querying:
  eth_getLogs({
    fromBlock: 0,
    toBlock: "latest",
    address: factoryAddress,
    topics: [keccak256("GameCreated(address)")]
  })
```

### Index Performance

| Query Type | Indexed | Non-Indexed |
|------------|---------|-------------|
| Find games by factory | O(log n) | O(n) full scan |
| Find player's moves | O(log n) | O(n) full scan |
| Get game winner | O(log n) | O(n) full scan |

**Recommendation**: Always index addresses in events for efficient filtering.

---

## Relationship Cardinality

| Relationship | Cardinality | Enforcement |
|--------------|-------------|-------------|
| Factory → Game | 1:N | Factory maintains `games[]` array |
| Game → Player1 | N:1 | `player1` address stored in Game |
| Game → Player2 | N:1 | `player2` address stored in Game |
| Game → Cell Owner | 9:N | `board[i][j]` addresses |
| Player → Games | 1:N | No direct index (query via events) |

---

## Visual Board State Example

### Empty Board (Initial State)
```
board[3][3] = [
  [0x0000000000000000000000000000000000000000, 0x0..., 0x0...],
  [0x0000000000000000000000000000000000000000, 0x0..., 0x0...],
  [0x0000000000000000000000000000000000000000, 0x0..., 0x0...]
]
```

### Mid-Game State
```
Player1: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 → Emoji: 😀
Player2: 0x70997970C51812dc3A010C7d01b50e0d17dc79C8 → Emoji: 🐶

board[3][3] = [
  [0xf39F..., 0x0000..., 0x7099...],  →  😀 |   | 🐶
  [0x0000..., 0xf39F..., 0x0000...],  →    | 😀 |
  [0x7099..., 0x0000..., 0x0000...]   →  🐶 |   |
]

lastPlayer = 0x7099... (Player2)
gameEnded = false
winner = 0x0000...
```

### Finished Game (Player1 Wins)
```
board[3][3] = [
  [0xf39F..., 0x0000..., 0x7099...],  →  😀 |   | 🐶
  [0x0000..., 0xf39F..., 0x0000...],  →    | 😀 |
  [0x7099..., 0x0000..., 0xf39F...]   →  🐶 |   | 😀
]

lastPlayer = 0xf39F... (Player1)
gameEnded = true
winner = 0xf39F... (Player1 diagonal win)
```

---

## Next Steps

- [Query Patterns](./QUERY_PATTERNS.md) - 50+ example queries
- [Migration Guide](./MIGRATION_GUIDE.md) - Deployment procedures
- [Performance Tuning](./PERFORMANCE.md) - Gas optimization strategies
