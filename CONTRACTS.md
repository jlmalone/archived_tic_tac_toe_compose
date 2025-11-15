# Smart Contract Interface Documentation

## Overview

The Tic-Tac-Toe DApp interacts with two main smart contracts deployed on Ethereum:

1. **TicTacToeFactory** - Creates new game instances
2. **TicTacToeGame** - Individual game logic and state

## Contract Addresses

### LOCAL (Hardhat)
- Factory: `0x4A679253410272dd5232B3Ff7cF5dbB88f295319`
- Implementation: `0xa85233C63b9Ee964Add6F2cffe00Fd84eb32338f`
- RPC: `http://127.0.0.1:8545/`
- Chain ID: `31337`

### SEPOLIA Testnet
- Factory: `0xa0B53DbDb0052403E38BBC31f01367aC6782118E`
- Implementation: `0x340AC014d800Ac398Af239Cebc3a376eb71B0353`
- RPC: `https://eth-sepolia.g.alchemy.com/v2/{API_KEY}`
- Chain ID: `11155111`

---

## TicTacToeFactory Contract

### Purpose
Creates new TicTacToeGame instances using the factory pattern.

### Interface

```solidity
contract TicTacToeFactory {
    event GameCreated(address indexed gameAddress);

    function createGame() external returns (address);
}
```

### Functions

#### `createGame()`
Creates a new game instance.

**Signature**: `function createGame() external returns (address)`

**Parameters**: None

**Returns**: `address` - Address of the newly created game contract

**Events**: Emits `GameCreated(address indexed gameAddress)`

**Gas**: ~200,000-300,000 gas

**Usage**:
```kotlin
val gameAddress = Blockchain.createGameByPlayer(playerIndex)
```

---

## TicTacToeGame Contract

### Purpose
Manages individual Tic-Tac-Toe game state and logic.

### State Variables

```solidity
contract TicTacToeGame {
    address[3][3] public board;      // 3x3 board of player addresses
    address public lastPlayer;       // Address of last player who moved
    address public winner;           // Address of winner (zero if none)
    bool public gameEnded;           // True if game has ended
}
```

### Interface

```solidity
contract TicTacToeGame {
    function makeMove(uint8 row, uint8 col) external;
    function getBoardState() external view returns (address[3][3] memory);
    function gameEnded() external view returns (bool);
    function winner() external view returns (address);
    function lastPlayer() external view returns (address);
}
```

---

### Functions

#### `makeMove(uint8 row, uint8 col)`
Submit a move to the board.

**Signature**: `function makeMove(uint8 row, uint8 col) external`

**Parameters**:
- `row` (uint8): Row index 0-2
- `col` (uint8): Column index 0-2

**Returns**: None (transaction)

**Reverts if**:
- Cell is already occupied
- Row or col out of bounds (> 2)
- Game has already ended
- Wrong player's turn

**Gas**: ~50,000-100,000 gas

**Usage**:
```kotlin
val txHash = Blockchain.makeMove(playerIndex, row, col)
```

---

#### `getBoardState()`
Returns the current 3x3 board state.

**Signature**: `function getBoardState() external view returns (address[3][3] memory)`

**Parameters**: None

**Returns**: `address[3][3]` - 3x3 array of addresses
- `0x0000...0000` for empty cells
- Player's address for occupied cells

**Gas**: ~3,000-5,000 gas (view function)

**ABI Encoding**: Returns static address[3][3] array (9 × 32 bytes)

**Usage**:
```kotlin
val board: List<List<String>> = Blockchain.getBoardState()
// Returns: [[row0], [row1], [row2]]
```

**Example Response**:
```
[
  ["0x0000...0000", "0xf39Fd6e51...", "0x0000...0000"],
  ["0x0000...0000", "0x0000...0000", "0x70997970C..."],
  ["0x0000...0000", "0x0000...0000", "0x0000...0000"]
]
```

---

#### `gameEnded()`
Check if the game has ended.

**Signature**: `function gameEnded() external view returns (bool)`

**Parameters**: None

**Returns**: `bool` - True if game ended (win or draw)

**Gas**: ~2,000 gas (view function)

**Usage**:
```kotlin
val ended: Boolean = Blockchain.readBool("gameEnded")
```

---

#### `winner()`
Get the winner's address.

**Signature**: `function winner() external view returns (address)`

**Parameters**: None

**Returns**: `address`
- Winner's address if there's a winner
- `0x0000...0000` if no winner (draw or game not ended)

**Gas**: ~2,000 gas (view function)

**Usage**:
```kotlin
val winnerAddr: String = Blockchain.readAddress("winner")
```

---

#### `lastPlayer()`
Get the address of the last player who moved.

**Signature**: `function lastPlayer() external view returns (address)`

**Parameters**: None

**Returns**: `address` - Last player's address

**Gas**: ~2,000 gas (view function)

**Usage**:
```kotlin
val lastPlayerAddr: String = Blockchain.readAddress("lastPlayer")
```

---

## Game Flow

### 1. Create Game
```kotlin
// Via factory
Blockchain.setCurrentGameAddress(null)
val gameAddr = Blockchain.createGameByPlayer(0)
// gameAddr is now set as currentGame automatically
```

### 2. Make Moves
```kotlin
// Player 0's turn
Blockchain.makeMove(0, row = 0, col = 0)

// Player 1's turn
Blockchain.makeMove(1, row = 1, col = 1)

// Continue alternating...
```

### 3. Check Win Condition
```kotlin
val ended = Blockchain.readBool("gameEnded")
if (ended) {
    val winner = Blockchain.readAddress("winner")
    if (winner == ZERO_ADDRESS) {
        println("Draw!")
    } else {
        println("Winner: $winner")
    }
}
```

---

## Board Representation

### Coordinate System

```
     Col 0   Col 1   Col 2
Row 0  [ 0 ]  [ 1 ]  [ 2 ]
Row 1  [ 3 ]  [ 4 ]  [ 5 ]
Row 2  [ 6 ]  [ 7 ]  [ 8 ]
```

### Example Game

```kotlin
// Starting board (all empty)
[
  ["0x00...", "0x00...", "0x00..."],
  ["0x00...", "0x00...", "0x00..."],
  ["0x00...", "0x00...", "0x00..."]
]

// After P1 moves to (0, 0)
[
  ["0xf39F...", "0x00...", "0x00..."],  // P1 at top-left
  ["0x00...", "0x00...", "0x00..."],
  ["0x00...", "0x00...", "0x00..."]
]

// After P2 moves to (1, 1)
[
  ["0xf39F...", "0x00...", "0x00..."],
  ["0x00...", "0x7099...", "0x00..."],  // P2 at center
  ["0x00...", "0x00...", "0x00..."]
]
```

---

## Win Conditions

### Horizontal Wins
- Row 0: (0,0), (0,1), (0,2)
- Row 1: (1,0), (1,1), (1,2)
- Row 2: (2,0), (2,1), (2,2)

### Vertical Wins
- Col 0: (0,0), (1,0), (2,0)
- Col 1: (0,1), (1,1), (2,1)
- Col 2: (0,2), (1,2), (2,2)

### Diagonal Wins
- Main diagonal: (0,0), (1,1), (2,2)
- Anti-diagonal: (0,2), (1,1), (2,0)

---

## Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Cell already occupied" | Cell has player address | Choose empty cell |
| "Invalid cell" | Row/col out of bounds | Use 0-2 range |
| "Wrong player" | Not your turn | Wait for other player |
| "Game already ended" | Game is over | Start new game |
| "No game set" | No currentGameAddress | Join or create game |

---

## Gas Costs

| Operation | Estimated Gas | USD (@ 25 gwei, ETH=$2000) |
|-----------|---------------|---------------------------|
| Create Game | 250,000 | ~$12.50 |
| Make Move (first) | 80,000 | ~$4.00 |
| Make Move (subsequent) | 50,000 | ~$2.50 |
| Get Board State | 3,000 (view) | FREE |
| Check Game Ended | 2,000 (view) | FREE |

**Note**: Gas costs vary based on network congestion.

---

## Security Considerations

### Access Control
- ✅ Players can only make moves for their own address
- ✅ Moves alternate between players automatically
- ✅ Cannot overwrite occupied cells

### Reentrancy
- ⚠️ **Verify**: Contract should have reentrancy guards
- ⚠️ **Recommendation**: Add `nonReentrant` modifier to `makeMove()`

### Integer Overflow
- ✅ Solidity 0.8.0+ has built-in overflow protection
- ✅ Row/col are uint8, safe for 0-2 range

---

## ABI Encoding Notes

### `getBoardState()` Return Value

The contract returns `address[3][3]` as a **static array**:

**Raw ABI**: 9 × 32-byte words (288 bytes total)
```
0x + [64 hex chars per address] × 9
```

**Decoding Strategy** (Blockchain.kt:515-569):
1. Try dynamic array decoding first
2. Fallback to manual static array slicing

**Example Raw Response**:
```
0x
0000000000000000000000000000000000000000000000000000000000000000  // [0][0]
000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266  // [0][1]
...
```

---

## Testing the Contracts

### Local Testing
```bash
# Start Hardhat node
cd tic-tac-toe-smart-contract
npx hardhat node

# Deploy contracts
npx tsx deployment/deploy_ethers.ts --local

# Run integration tests
cd ../tic_tac_toe_compose
RUN_INTEGRATION_TESTS=true ./gradlew test --tests "BlockchainIntegrationTest"
```

### Testnet Testing
```bash
# Deploy to Sepolia
cd tic-tac-toe-smart-contract
npx tsx deployment/deploy_ethers.ts --sepolia

# Update DApp .env
cd ../tic_tac_toe_compose
nano .env  # Set LOCAL=false

# Run DApp
./gradlew run
```

---

## Contract Source Repository

**Note**: Smart contracts are stored in a separate repository:
- Path: `/Users/josephmalone/tic-tac-toe-smart-contract`
- Deployment Script: `deployment/deploy_ethers.ts`
- Language: Solidity + TypeScript (Hardhat)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-15
