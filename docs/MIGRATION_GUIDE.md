# Migration & Deployment Guide
## Contract Deployment, Versioning & Upgrade Strategies

**Version**: 1.0
**Last Updated**: 2025-11-15

---

## Table of Contents
1. [Deployment Strategy](#deployment-strategy)
2. [Version History](#version-history)
3. [Rollback Procedures](#rollback-procedures)
4. [Data Migration Scripts](#data-migration-scripts)
5. [Upgrade Patterns](#upgrade-patterns)
6. [Environment Management](#environment-management)

---

## Deployment Strategy

### Overview

This project uses a **Clone Proxy Pattern (EIP-1167)** for efficient game contract deployment:

1. **Factory Contract**: Deployed once per network (singleton)
2. **Implementation Contract**: Game logic deployed once
3. **Proxy Instances**: Minimal clones created per game (~96 gas vs ~200k gas)

### Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    DEPLOYMENT WORKFLOW                       │
└─────────────────────────────────────────────────────────────┘

Step 1: Deploy Implementation Contract
  ↓
  Contract: GameImplementation.sol
  Gas Cost: ~1,500,000
  Address: 0xB7f8BC63... (Hardhat) / 0x340AC014... (Sepolia)
  ↓

Step 2: Deploy Factory Contract
  ↓
  Contract: Factory.sol
  Constructor Arg: gameImplementationAddress
  Gas Cost: ~500,000
  Address: 0xA51c1fc2... (Hardhat) / 0xa0B53DbDb... (Sepolia)
  ↓

Step 3: Verify Contracts on Etherscan (Sepolia only)
  ↓
  Tool: hardhat-verify plugin
  ↓

Step 4: Update Deployment JSON
  ↓
  Files:
    - deployment_output_hardhat_local.json
    - deployment_output_sepolia_testnet.json
  ↓

Step 5: Client Loads Deployment Addresses
  ↓
  Blockchain.kt loads addresses from resources/
  ↓

Step 6: Users Create Game Instances
  ↓
  Factory.createGame() → New proxy clone (~200k gas)
  Emits: GameCreated(address)
```

---

## Deployment Commands

### Automated Deployment (Current System)

**Location**: External Hardhat project (referenced in `HARDHAT_PROJECT_DIR`)
**Trigger**: Blockchain.kt:239-268

```kotlin
// Kotlin trigger (Blockchain.kt)
fun runDeploy(): Boolean {
    val flag = if (isLocal) "--local" else "--sepolia"
    val rawNpx = dotenv["NPX_PATH"]?.ifBlank { null } ?: "npx"

    var cmdList = listOf(rawNpx, "tsx", "deployment/deploy_ethers.ts", flag)
    var pb = ProcessBuilder(cmdList).directory(File(HARDHAT_PROJECT_DIR))

    val p = pb.inheritIO().start()
    return p.waitFor() == 0
}
```

**Expected Script Behavior**:
```bash
# In tic-tac-toe-smart-contract project
cd $HARDHAT_PROJECT_DIR

# Local deployment
npx tsx deployment/deploy_ethers.ts --local
# Output: Creates deployment_output_hardhat_local.json

# Sepolia deployment
npx tsx deployment/deploy_ethers.ts --sepolia
# Output: Creates deployment_output_sepolia_testnet.json
```

---

### Manual Deployment (Hardhat Example)

**Hypothetical Deployment Script** (`deploy_ethers.ts`):

```typescript
import { ethers } from "hardhat";
import * as fs from "fs";

async function main() {
    const [deployer] = await ethers.getSigners();
    console.log("Deploying contracts with account:", deployer.address);

    // Step 1: Deploy Implementation
    const GameImpl = await ethers.getContractFactory("TicTacToeGame");
    const gameImpl = await GameImpl.deploy();
    await gameImpl.waitForDeployment();
    const gameImplAddress = await gameImpl.getAddress();
    console.log("GameImplementation deployed to:", gameImplAddress);

    // Step 2: Deploy Factory
    const Factory = await ethers.getContractFactory("GameFactory");
    const factory = await Factory.deploy(gameImplAddress);
    await factory.waitForDeployment();
    const factoryAddress = await factory.getAddress();
    console.log("Factory deployed to:", factoryAddress);

    // Step 3: Save deployment addresses
    const output = {
        gameImplementationAddress: gameImplAddress,
        factoryAddress: factoryAddress
    };

    const network = process.argv.includes("--sepolia") ? "sepolia_testnet" : "hardhat_local";
    fs.writeFileSync(
        `deployment_output_${network}.json`,
        JSON.stringify(output, null, 2)
    );

    console.log(`✅ Deployment complete! Saved to deployment_output_${network}.json`);
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
```

**Hardhat Config** (`hardhat.config.ts`):

```typescript
import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";
import * as dotenv from "dotenv";

dotenv.config();

const config: HardhatUserConfig = {
    solidity: {
        version: "0.8.20",
        settings: {
            optimizer: {
                enabled: true,
                runs: 200
            }
        }
    },
    networks: {
        hardhat: {
            chainId: 31337
        },
        sepolia: {
            url: process.env.SEPOLIA_RPC_URL || "",
            accounts: process.env.PRIVATE_KEY_PLAYER1 ? [process.env.PRIVATE_KEY_PLAYER1] : [],
            chainId: 11155111
        }
    },
    etherscan: {
        apiKey: process.env.ETHERSCAN_API_KEY
    }
};

export default config;
```

---

## Version History

### Version 1.0.0 (Current)

**Deployment Date**: 2024-11-15 (estimated)
**Contracts**:
- `GameImplementation.sol` - Core game logic
- `GameFactory.sol` - Clone factory pattern

**Features**:
- 3×3 tic-tac-toe game
- Two-player support
- Automatic win detection
- Move validation
- Event emission (MoveMade, GameEnded)

**Addresses**:

| Network | Factory | Implementation |
|---------|---------|----------------|
| Hardhat Local | `0xA51c1fc2f0D1a1b8494Ed1FE312d7C3a78Ed91C0` | `0xB7f8BC63BbcaD18155201308C8f3540b07f84F5e` |
| Sepolia Testnet | `0xa0B53DbDb0052403E38BBC31f01367aC6782118E` | `0x340AC014d800Ac398Af239Cebc3a376eb71B0353` |

**Known Issues**:
- No timeouts for inactive games
- No on-chain player matchmaking
- Board state decoding requires static array fallback (Blockchain.kt:554-567)

**Gas Costs**:
- Create game: ~200,000 gas
- Make move: ~80,000 gas
- Read board: 0 gas (view function)

---

### Version 0.1.0 (Hypothetical Historical Version)

**Deployment Date**: 2024-10-01
**Contracts**:
- `TicTacToeGameV0.sol` - Single monolithic contract (no factory)

**Breaking Changes from v0.1.0 → v1.0.0**:
1. **Factory Pattern**: Games now deployed via factory instead of direct deployment
2. **Clone Proxy**: 97% gas reduction for game creation
3. **Event Changes**: Added `GameCreated` event on factory
4. **ABI Changes**: `createGame()` moved to factory contract

**Migration Required**:
- ✅ Users must redeploy to new factory
- ✅ Old games remain playable at original addresses
- ❌ No automatic state migration (games are independent)

---

## Rollback Procedures

### Scenario 1: Buggy Implementation Deployed

**Problem**: New implementation contract has critical bug
**Impact**: New games created after deployment are affected
**Solution**: Deploy fixed implementation + new factory

**Steps**:

```bash
# 1. Deploy fixed implementation
npx hardhat run scripts/deploy_impl_v1.1.ts --network sepolia

# 2. Deploy new factory pointing to fixed implementation
npx hardhat run scripts/deploy_factory_v1.1.ts --network sepolia

# 3. Update deployment JSON
echo '{
  "gameImplementationAddress": "0xNEW_IMPL_ADDRESS",
  "factoryAddress": "0xNEW_FACTORY_ADDRESS"
}' > deployment_output_sepolia_testnet.json

# 4. Rebuild Kotlin app with updated JSON
./gradlew build

# 5. Communicate to users: Use new factory address for new games
```

**Old Games**: Continue to function normally (immutable bytecode)
**New Games**: Use fixed implementation

**Downtime**: ~0 seconds (parallel deployment)

---

### Scenario 2: Factory Contract Compromise

**Problem**: Factory contract has access control bug
**Impact**: Unauthorized contracts could be deployed
**Solution**: Deploy new factory, deprecate old one

**Steps**:

```bash
# 1. Deploy new factory with fix
npx hardhat run scripts/deploy_factory_secure.ts --network sepolia

# 2. Verify on Etherscan
npx hardhat verify --network sepolia 0xNEW_FACTORY_ADDRESS 0xIMPL_ADDRESS

# 3. Update client config
# In .env:
# FACTORY_ADDRESS_SEPOLIA=0xNEW_FACTORY_ADDRESS

# 4. Announce deprecation
echo "Old factory deprecated. Use new address: 0xNEW_FACTORY_ADDRESS"
```

**Migration**: Users manually switch to new factory in UI
**Data Loss**: None (old games unaffected)

---

### Scenario 3: Emergency Network Issues

**Problem**: Sepolia testnet congestion or outage
**Impact**: Transactions timing out, high gas prices
**Solution**: Temporarily switch to Hardhat local or alternative testnet

**Steps**:

```kotlin
// In Blockchain.kt or UI
Blockchain.applyLocal(true)  // Switch to local Hardhat node

// OR: Add new network support
fun applyNetwork(network: String) {
    when (network) {
        "local" -> { /* existing logic */ }
        "sepolia" -> { /* existing logic */ }
        "goerli" -> {
            // New testnet fallback
            val rpcUrl = "https://goerli.infura.io/v3/YOUR_KEY"
            httpService = HttpService(rpcUrl)
            web3j = Web3j.build(httpService)
        }
    }
}
```

**Recovery Time**: Immediate (configuration change)

---

## Data Migration Scripts

### Migration 1: Export Game History

**Use Case**: Backup all game data before network migration

```kotlin
suspend fun exportAllGames(outputFile: File) = withContext(Dispatchers.IO) {
    val allGames = getAllCreatedGames()
    val gameData = mutableListOf<GameExport>()

    allGames.forEach { gameAddr ->
        setCurrentGameAddress(gameAddr)

        val board = getBoardState()
        val ended = readBool("gameEnded")
        val winner = if (ended) readAddress("winner") else null
        val moves = getGameMoves()
        val creationBlock = getGameCreationBlock(gameAddr)

        gameData.add(GameExport(
            address = gameAddr,
            board = board,
            gameEnded = ended,
            winner = winner,
            moves = moves,
            creationBlock = creationBlock
        ))
    }

    // Serialize to JSON
    val json = Json.encodeToString(gameData)
    outputFile.writeText(json)
    println("Exported ${gameData.size} games to ${outputFile.absolutePath}")
}

@Serializable
data class GameExport(
    val address: String,
    val board: List<List<String>>,
    val gameEnded: Boolean,
    val winner: String?,
    val moves: List<MoveEvent>,
    val creationBlock: Long?
)
```

**Usage**:
```kotlin
scope.launch {
    exportAllGames(File("game_backup_sepolia_2025-11-15.json"))
}
```

---

### Migration 2: Clone Game State to New Network

**Use Case**: Manually recreate completed games on new network

```kotlin
suspend fun cloneGameToNewNetwork(
    sourceGameAddress: String,
    targetNetwork: Blockchain
): String? = withContext(Dispatchers.IO) {
    // 1. Read source game state
    setCurrentGameAddress(sourceGameAddress)
    val moves = getGameMoves().sortedBy { it.blockNumber }

    // 2. Create new game on target network
    targetNetwork.applyLocal(true)  // or false for Sepolia
    val newGameAddress = targetNetwork.createGameByPlayer(0)
        ?: error("Failed to create game on target network")

    // 3. Replay moves
    moves.forEach { move ->
        val playerIdx = if (move.player.lowercase() ==
            getPlayerCredentials(0).address.lowercase()) 0 else 1

        targetNetwork.makeMove(playerIdx, move.row, move.col)
    }

    println("✅ Cloned game from $sourceGameAddress to $newGameAddress")
    newGameAddress
}
```

**Limitations**:
- Requires replaying all moves (gas cost: moves × 80k)
- Timestamps will differ
- Block numbers will differ
- Only feasible for small number of games

---

### Migration 3: Batch Export Events

**Use Case**: Archive all events to off-chain database (PostgreSQL, MongoDB, etc.)

```kotlin
suspend fun archiveEventsToDatabase(db: Database) = withContext(Dispatchers.IO) {
    val factory = getFactoryAddress() ?: error("No factory")

    // Get all GameCreated events
    val gameCreatedEvents = getAllEvents(factory, fromBlock = 0)
        .filter { it.topics[0] == EventEncoder.encode(
            Event("GameCreated", listOf(object : TypeReference<Address>() {}))
        )}

    db.batchInsert("game_created_events", gameCreatedEvents.map {
        mapOf(
            "game_address" to ("0x" + it.topics[1].takeLast(40)),
            "block_number" to it.blockNumber.toLong(),
            "transaction_hash" to it.transactionHash,
            "timestamp" to getBlockTimestamp(it.blockNumber.toLong())
        )
    })

    // Get all MoveMade events for each game
    gameCreatedEvents.forEach { createEvent ->
        val gameAddr = "0x" + createEvent.topics[1].takeLast(40)
        setCurrentGameAddress(gameAddr)

        val moveMadeEvents = getGameMoves()
        db.batchInsert("move_made_events", moveMadeEvents.map {
            mapOf(
                "game_address" to gameAddr,
                "player" to it.player,
                "row" to it.row,
                "col" to it.col,
                "block_number" to it.blockNumber
            )
        })
    }

    println("✅ Archived events to database")
}
```

---

## Upgrade Patterns

### Pattern 1: Transparent Proxy (Not Currently Used)

**Description**: Allows upgrading implementation while preserving state
**Pros**: Seamless upgrades, state preservation
**Cons**: Complex, gas overhead, centralization risk

**Implementation**:
```solidity
// TransparentUpgradeableProxy.sol (OpenZeppelin)
contract GameProxy is TransparentUpgradeableProxy {
    constructor(
        address _logic,
        address admin_,
        bytes memory _data
    ) TransparentUpgradeableProxy(_logic, admin_, _data) {}
}

// Upgrade function (admin only)
function upgradeTo(address newImplementation) external onlyAdmin {
    _upgradeTo(newImplementation);
}
```

**Migration Script**:
```typescript
// Upgrade existing games
const proxy = await ethers.getContractAt("GameProxy", proxyAddress);
const newImpl = await deployNewImplementation();
await proxy.upgradeTo(await newImpl.getAddress());
```

**Applicability**: Not used in current design (games are immutable after creation)

---

### Pattern 2: Factory Versioning (Current Pattern)

**Description**: Deploy new factory for each version, deprecate old factory
**Pros**: Simple, no state migration, old games unaffected
**Cons**: Fragmented game instances across factories

**Current Implementation**:
```solidity
// Factory V1
contract GameFactoryV1 {
    address public immutable implementationV1;

    function createGame() external returns (address) {
        return Clones.clone(implementationV1);
    }
}

// Factory V2 (new features)
contract GameFactoryV2 {
    address public immutable implementationV2;

    function createGameWithBet(uint256 betAmount) external payable returns (address) {
        // New feature: betting support
        require(msg.value == betAmount, "Incorrect bet");
        address game = Clones.clone(implementationV2);
        // Initialize bet logic
        return game;
    }
}
```

**Deployment Addresses**:
```json
{
  "v1": {
    "factoryAddress": "0xOLD_FACTORY",
    "implementationAddress": "0xOLD_IMPL"
  },
  "v2": {
    "factoryAddress": "0xNEW_FACTORY",
    "implementationAddress": "0xNEW_IMPL"
  }
}
```

**Client Support**:
```kotlin
enum class FactoryVersion { V1, V2 }

fun getFactoryAddress(version: FactoryVersion): String? {
    return when (version) {
        FactoryVersion.V1 -> deploymentInfo?.factoryAddressV1
        FactoryVersion.V2 -> deploymentInfo?.factoryAddressV2
    }
}
```

---

### Pattern 3: Registry Pattern

**Description**: Central registry tracks all game versions
**Pros**: Single source of truth, version discovery
**Cons**: Additional gas cost, registry must be trusted

**Example**:
```solidity
contract GameRegistry {
    struct GameVersion {
        address factory;
        address implementation;
        string version;
        bool deprecated;
    }

    GameVersion[] public versions;

    function registerVersion(
        address factory,
        address implementation,
        string memory version
    ) external onlyOwner {
        versions.push(GameVersion(factory, implementation, version, false));
    }

    function getLatestFactory() external view returns (address) {
        for (uint i = versions.length; i > 0; i--) {
            if (!versions[i-1].deprecated) {
                return versions[i-1].factory;
            }
        }
        revert("No active version");
    }
}
```

---

## Environment Management

### Configuration Files

**Location**: Project root (`.env` - gitignored)

```bash
# .env (template)

# ──────────────────────────────────────
# Network Selection
# ──────────────────────────────────────
LOCAL=true  # true = Hardhat local, false = Sepolia

# ──────────────────────────────────────
# RPC Endpoints
# ──────────────────────────────────────
LOCAL_RPC_URL=http://127.0.0.1:8545/
SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_API_KEY
ALCHEMY_API_KEY=your_alchemy_api_key_here

# ──────────────────────────────────────
# Chain IDs
# ──────────────────────────────────────
HARDHAT_CHAIN_ID=31337
SEPOLIA_CHAIN_ID=11155111

# ──────────────────────────────────────
# Private Keys (NEVER COMMIT TO GIT)
# ──────────────────────────────────────
# Hardhat local (pre-funded test accounts)
PRIVATE_KEY_HARDHAT_0=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
PRIVATE_KEY_HARDHAT_1=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d

# Sepolia testnet (MUST FUND WITH TEST ETH)
PRIVATE_KEY_PLAYER1=0x...your_private_key_1...
PRIVATE_KEY_PLAYER2=0x...your_private_key_2...

# ──────────────────────────────────────
# External Projects
# ──────────────────────────────────────
HARDHAT_PROJECT_DIR=/path/to/tic-tac-toe-smart-contract
NPX_PATH=npx  # or /usr/local/bin/npx
```

### Deployment Address Files

**Location**: `src/main/resources/`

**File 1**: `deployment_output_hardhat_local.json`
```json
{
  "gameImplementationAddress": "0xB7f8BC63BbcaD18155201308C8f3540b07f84F5e",
  "factoryAddress": "0xA51c1fc2f0D1a1b8494Ed1FE312d7C3a78Ed91C0"
}
```

**File 2**: `deployment_output_sepolia_testnet.json`
```json
{
  "gameImplementationAddress": "0x340AC014d800Ac398Af239Cebc3a376eb71B0353",
  "factoryAddress": "0xa0B53DbDb0052403E38BBC31f01367aC6782118E"
}
```

**Loading Logic** (Blockchain.kt:96-104):
```kotlin
private fun loadDeploymentInfo(): DeploymentAddresses? {
    val path = if(isLocal)
        "deployment_output_hardhat_local.json"
    else
        "deployment_output_sepolia_testnet.json"

    val stream = this::class.java.classLoader.getResourceAsStream(path)
        ?: return null

    return Json { ignoreUnknownKeys = true }
        .decodeFromString<DeploymentAddresses>(stream.reader().readText())
}
```

---

### Network Switching Workflow

```
User Action: Toggle "LOCAL ↔ SEPOLIA" button
  ↓
TicTacToeScreen.kt:82 → Blockchain.applyLocal(!isLocal)
  ↓
Blockchain.kt:47-57 → applyLocal(flag: Boolean)
  ↓
  1. Set localFlag = flag
  2. Reload deployment addresses from JSON
  3. Reset currentGameAddress = null
  4. Rebuild Web3j HTTP client
  5. Print new network info
  ↓
UI State Reset:
  - factoryAddr = null
  - gameAddr = null
  - board = null
  - currentPlayerIdx = 0
  ↓
User loads factory → Creates/joins game on new network
```

---

## Pre-Deployment Checklist

### Local Deployment (Hardhat)

- [ ] Hardhat node running (`npx hardhat node`)
- [ ] `.env` has `LOCAL=true`
- [ ] `.env` has `PRIVATE_KEY_HARDHAT_0` and `PRIVATE_KEY_HARDHAT_1`
- [ ] `HARDHAT_PROJECT_DIR` points to smart contract repo
- [ ] Run deployment: `npx tsx deployment/deploy_ethers.ts --local`
- [ ] Verify `deployment_output_hardhat_local.json` created
- [ ] Copy JSON to `src/main/resources/`
- [ ] Rebuild Kotlin app: `./gradlew build`
- [ ] Test game creation in UI

---

### Sepolia Deployment

- [ ] Sepolia testnet accounts funded with ETH (use faucet)
- [ ] `.env` has `LOCAL=false`
- [ ] `.env` has `ALCHEMY_API_KEY`
- [ ] `.env` has `PRIVATE_KEY_PLAYER1` and `PRIVATE_KEY_PLAYER2`
- [ ] Run deployment: `npx tsx deployment/deploy_ethers.ts --sepolia`
- [ ] Verify `deployment_output_sepolia_testnet.json` created
- [ ] Copy JSON to `src/main/resources/`
- [ ] Verify contracts on Etherscan: `npx hardhat verify --network sepolia <ADDRESS>`
- [ ] Rebuild Kotlin app: `./gradlew build`
- [ ] Test game creation in UI
- [ ] Monitor gas costs and transaction times

---

## Post-Deployment Monitoring

### Health Checks

```kotlin
suspend fun verifyDeployment(): DeploymentHealth = withContext(Dispatchers.IO) {
    val factory = getFactoryAddress() ?: return@withContext DeploymentHealth(
        factoryDeployed = false,
        implementationDeployed = false,
        errors = listOf("Factory address not found")
    )

    val factoryCode = getContractCode(factory)
    val factoryDeployed = factoryCode != "0x"

    val impl = deploymentInfo?.gameImplementationAddress
        ?: return@withContext DeploymentHealth(
            factoryDeployed = factoryDeployed,
            implementationDeployed = false,
            errors = listOf("Implementation address not found")
        )

    val implCode = getContractCode(impl)
    val implDeployed = implCode != "0x"

    // Test game creation
    val errors = mutableListOf<String>()
    try {
        val testGame = createGameByPlayer(0)
        if (testGame == null) errors.add("Game creation returned null")
    } catch (e: Exception) {
        errors.add("Game creation failed: ${e.message}")
    }

    DeploymentHealth(
        factoryDeployed = factoryDeployed,
        implementationDeployed = implDeployed,
        errors = errors
    )
}

data class DeploymentHealth(
    val factoryDeployed: Boolean,
    val implementationDeployed: Boolean,
    val errors: List<String>
)
```

---

## Next Documents

- [Performance Tuning](./PERFORMANCE.md) - Gas optimization strategies
- [Integration Guide](./INTEGRATION.md) - Application integration patterns
- [Backup & Recovery](./BACKUP_RECOVERY.md) - Key management and data recovery
