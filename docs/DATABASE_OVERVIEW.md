# Database Schema Documentation
## Tic-Tac-Toe Ethereum Smart Contract Database

**Version**: 1.0
**Last Updated**: 2025-11-15
**Blockchain**: Ethereum (Hardhat Local + Sepolia Testnet)

---

## Executive Summary

This project uses **Ethereum blockchain smart contracts** as its persistent data layer instead of traditional relational databases. All game state, player data, and game logic are stored on-chain through Solidity smart contracts, providing:

- **Immutability**: Game history cannot be altered
- **Transparency**: All moves are publicly verifiable
- **Decentralization**: No central database server required
- **Cryptographic Security**: Player authentication via private keys

---

## System Architecture

### Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | Ethereum Blockchain | Persistent storage layer |
| **Schema Definition** | Solidity Smart Contracts | Data structure & business logic |
| **Query Engine** | Web3j (JSON-RPC) | Read operations (eth_call) |
| **Write Operations** | Web3j (Raw Transactions) | State mutations (eth_sendRawTransaction) |
| **Client Application** | Kotlin + Compose Desktop | User interface |
| **Transaction Signing** | web3j Credentials | Authentication & authorization |

### Deployment Environments

#### 1. Local Development (Hardhat)
- **RPC URL**: `http://127.0.0.1:8545/`
- **Chain ID**: `31337`
- **Factory Address**: `0xA51c1fc2f0D1a1b8494Ed1FE312d7C3a78Ed91C0`
- **Game Implementation**: `0xB7f8BC63BbcaD18155201308C8f3540b07f84F5e`
- **Purpose**: Local testing with instant block mining
- **Faucet**: Hardhat provides pre-funded test accounts

#### 2. Sepolia Testnet
- **RPC URL**: `https://eth-sepolia.g.alchemy.com/v2/[API_KEY]`
- **Chain ID**: `11155111`
- **Factory Address**: `0xa0B53DbDb0052403E38BBC31f01367aC6782118E`
- **Game Implementation**: `0x340AC014d800Ac398Af239Cebc3a376eb71B0353`
- **Purpose**: Public testnet for realistic blockchain conditions
- **Faucet**: https://cloud.google.com/application/web3/faucet/ethereum/sepolia

---

## Database Schema Overview

### Entity Relationship Model

```
┌──────────────────────┐
│   Factory Contract   │  (Singleton)
│  ──────────────────  │
│  - factoryAddress    │
│  - implementation    │
└──────────┬───────────┘
           │ creates ↓ (1:N)
┌──────────────────────┐
│   Game Contract      │  (Multiple instances)
│  ──────────────────  │
│  - gameAddress       │  PK
│  - board[3][3]       │  address[][] - Player ownership grid
│  - gameEnded         │  bool - Game completion flag
│  - winner            │  address - Winning player or 0x0
│  - lastPlayer        │  address - Last move's player
└──────────┬───────────┘
           │ references ↓ (N:1)
┌──────────────────────┐
│   Player (External)  │  (Ethereum Accounts)
│  ──────────────────  │
│  - address           │  PK - Ethereum address
│  - privateKey        │  Off-chain (client-side only)
│  - nonce             │  Managed by Ethereum network
└──────────────────────┘
```

---

## Core Contracts (Tables)

### 1. Factory Contract
**Purpose**: Game instance factory pattern
**Pattern**: Singleton/Registry
**Location**: See deployment addresses above

#### State Variables (Columns)
| Variable | Solidity Type | Description | Index |
|----------|---------------|-------------|-------|
| `gameImplementation` | `address` | Address of game logic contract | N/A |
| `games[]` | `address[]` | Registry of all created games | Array index |

#### Methods (Stored Procedures)
| Method | Parameters | Returns | Gas Cost | Description |
|--------|-----------|---------|----------|-------------|
| `createGame()` | None | `address` | ~200,000 | Deploy new game instance via Clone pattern |

#### Events (Indexes)
| Event | Parameters | Indexed Fields | Purpose |
|-------|-----------|----------------|---------|
| `GameCreated` | `address indexed gameAddress` | `gameAddress` | Enable efficient game discovery |

---

### 2. Game Contract
**Purpose**: Individual tic-tac-toe game state
**Pattern**: Minimal Clone Proxy (EIP-1167)
**Instances**: One per game created

#### State Variables (Columns)

| Variable | Solidity Type | Kotlin Equivalent | Description | Default Value | Constraints |
|----------|---------------|-------------------|-------------|---------------|-------------|
| `board` | `address[3][3]` | `List<List<String>>` | 3x3 grid of cell owners | `[[0x0,...], ...]` | Immutable after cell claimed |
| `gameEnded` | `bool` | `Boolean` | Game completion status | `false` | Set to `true` on win/draw |
| `winner` | `address` | `String` | Winner's address | `0x0000...0000` | `0x0` = draw, address = winner |
| `lastPlayer` | `address` | `String` | Last player to move | `0x0000...0000` | Updated on each move |
| `player1` | `address` | `String` | First player (creator) | Set at creation | Cannot change |
| `player2` | `address` | `String` | Second player | `0x0` until joined | Set on first move |

#### Methods (Operations)

| Method | Parameters | Access Control | Gas Cost | State Changes | Reverts If |
|--------|-----------|----------------|----------|---------------|------------|
| `makeMove` | `uint8 row, uint8 col` | Any externally owned account | ~80,000 | Updates `board`, `lastPlayer`, checks win condition | Cell occupied, out of bounds, game ended, wrong turn |
| `getBoardState` | None | Public view | 0 (read-only) | None | Contract not initialized |
| `gameEnded` | None | Public view | 0 (read-only) | None | None |
| `winner` | None | Public view | 0 (read-only) | None | None |
| `lastPlayer` | None | Public view | 0 (read-only) | None | None |

#### Events (Indexes)

| Event | Parameters | Indexed Fields | Emitted When | Use Case |
|-------|-----------|----------------|--------------|----------|
| `MoveMade` | `address indexed player, uint8 row, uint8 col` | `player` | Each valid move | Transaction history, game replay |
| `GameEnded` | `address indexed winner, bool isDraw` | `winner` | Game completion | Trigger UI updates, settle bets |

---

## Data Types & Encoding

### Address Type (20 bytes)
- **Format**: `0x` + 40 hex characters
- **Example**: `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`
- **Zero Address**: `0x0000000000000000000000000000000000000000`
- **Use Cases**: Player identification, cell ownership, winner

### Board State Encoding
The `address[3][3]` board is stored as:
```
Storage Layout (9 x 32-byte words):
Word 0: [12 bytes padding][20 bytes address] - board[0][0]
Word 1: [12 bytes padding][20 bytes address] - board[0][1]
Word 2: [12 bytes padding][20 bytes address] - board[0][2]
Word 3: [12 bytes padding][20 bytes address] - board[1][0]
Word 4: [12 bytes padding][20 bytes address] - board[1][1]
Word 5: [12 bytes padding][20 bytes address] - board[1][2]
Word 6: [12 bytes padding][20 bytes address] - board[2][0]
Word 7: [12 bytes padding][20 bytes address] - board[2][1]
Word 8: [12 bytes padding][20 bytes address] - board[2][2]
```

#### RPC Response Decoding
When calling `getBoardState()` via `eth_call`:
```
Raw response: 0x[576 hex chars = 9 words × 64 chars/word]

Decoding logic (Blockchain.kt:559-567):
for i in 0..8:
    word = raw[2 + i*64 : 2 + (i+1)*64]  // Extract 64-char word
    address = "0x" + word[-40:]           // Take last 40 chars
```

---

## Constraints & Validation Rules

### Business Logic Constraints
1. **Cell Occupancy**: Once a cell is claimed, it cannot be overwritten
2. **Turn Order**: Players must alternate turns (enforced by `lastPlayer`)
3. **Game Completion**: No moves allowed after `gameEnded == true`
4. **Bounds Checking**: `row` and `col` must be in range `[0, 2]`
5. **Win Detection**: Automatically checked after each move

### Network Constraints
1. **Gas Limit**: Maximum 30,000,000 per block (Ethereum mainnet)
2. **Block Time**: ~12 seconds (mainnet), ~2 seconds (Hardhat local)
3. **Transaction Throughput**: ~15-30 TPS (mainnet)
4. **Finality**: 12-32 confirmations recommended for mainnet

### Security Constraints
1. **Reentrancy**: Not applicable (no ETH transfers in game logic)
2. **Integer Overflow**: Solidity 0.8+ has built-in protection
3. **Access Control**: No privileged roles (permissionless game)
4. **Front-Running**: Possible but game-theoretically irrelevant

---

## Comparison to Traditional Databases

| Feature | Ethereum Blockchain | PostgreSQL |
|---------|-------------------|-----------|
| **ACID Properties** | ✅ Atomic, ✅ Consistent, ❌ Isolated (public), ✅ Durable | ✅ Full ACID |
| **Transaction Cost** | ~$0.50-$50 (gas fees) | ~$0 (server resources) |
| **Query Speed** | ~2-15 seconds (block time) | ~1-50ms |
| **Scalability** | ~15-30 TPS (mainnet) | ~10,000+ TPS |
| **Data Immutability** | ✅ Perfect | ❌ Can be modified |
| **Audit Trail** | ✅ Cryptographic proof | Requires separate logging |
| **Decentralization** | ✅ Globally distributed | ❌ Centralized server |
| **Backup/Recovery** | ✅ Automatic (replicated) | Manual backup required |

---

## References

- [Ethereum Yellow Paper](https://ethereum.github.io/yellowpaper/paper.pdf) - Formal specification
- [EIP-1167: Minimal Proxy Contract](https://eips.ethereum.org/EIPS/eip-1167) - Clone pattern
- [Web3j Documentation](https://docs.web3j.io/) - Java/Kotlin blockchain library
- [Solidity Documentation](https://docs.soliditylang.org/) - Smart contract language
- [Alchemy RPC Endpoints](https://www.alchemy.com/) - Sepolia testnet provider

---

**Next Documents**:
- [Schema Diagrams](./SCHEMA_DIAGRAMS.md) - Visual ER diagrams
- [Query Patterns](./QUERY_PATTERNS.md) - 50+ example queries
- [Migration Guide](./MIGRATION_GUIDE.md) - Deployment & upgrades
- [Performance Tuning](./PERFORMANCE.md) - Gas optimization
- [Integration Guide](./INTEGRATION.md) - Application usage patterns
