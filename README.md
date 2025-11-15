# Tic-Tac-Toe DApp 🎮⛓️

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-purple?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-1.7.3-blue?logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Web3j-5.0.0-orange" alt="Web3j">
  <img src="https://img.shields.io/badge/Ethereum-Sepolia-lightgrey?logo=ethereum" alt="Ethereum">
  <img src="https://img.shields.io/badge/Tests-Passing-success" alt="Tests">
</p>

A desktop DApp (Decentralized Application) for playing Tic-Tac-Toe on Ethereum. Built with Kotlin, Jetpack Compose Desktop, and Web3j.

## ✨ Features

- 🎨 **Matrix-Themed UI** - Neon green on black aesthetic
- 🔗 **Dual Network Support** - LOCAL (Hardhat) and SEPOLIA testnet
- 🎮 **Two-Player Game** - Play against another player on-chain
- 😀 **Emoji Players** - Deterministic emoji avatars for each address
- 🔧 **Debug Tools** - Built-in contract state inspection
- ⚡ **Smart Gas Management** - Automatic gas estimation with 150% safety margin
- 🧪 **Comprehensive Testing** - 80%+ code coverage with unit and integration tests
- 🔐 **Security Audited** - See [SECURITY.md](SECURITY.md) for details

## 📸 Screenshots

```
┌─────────────────────────────────────────┐
│  Tic-Tac-Toe Web3                       │
├─────────────────────────────────────────┤
│  [LOCAL ✔] [Print Addrs]                │
│  [Deploy (npx tsx)]                      │
│  [Load Factory]                          │
│  [Create Game (P1)]                      │
│  Game Address: [0x...]                   │
│  [Join Game]                             │
├─────────────────────────────────────────┤
│  [Signer: P1] [Row: 0] [Col: 0]         │
│  [Make Move]                             │
├─────────────────────────────────────────┤
│    ┌────┬────┬────┐                     │
│    │ 😀 │    │ 🐶 │                     │
│    ├────┼────┼────┤                     │
│    │    │ 😀 │    │                     │
│    ├────┼────┼────┤                     │
│    │ 🐶 │    │    │                     │
│    └────┴────┴────┘                     │
│                                          │
│  Move OK                                 │
└─────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- **Java 11+** (JDK 11 or higher)
- **Gradle 8+** (included via wrapper)
- **Node.js 18+** (for smart contract deployment)
- **npx** (comes with Node.js)
- **Git** (for cloning repositories)

### Option 1: Run with Pre-Deployed Contracts (Easiest)

If contracts are already deployed on SEPOLIA testnet:

```bash
# Clone the repository
git clone <repository-url>
cd tic_tac_toe_compose

# Create .env file with your private keys
cp .env.example .env
nano .env  # Edit with your keys

# Run the application
./gradlew run
```

### Option 2: Full Local Development Setup

For complete local development with Hardhat:

```bash
# 1. Clone both repositories
git clone <dapp-repository-url> tic_tac_toe_compose
git clone <contract-repository-url> tic-tac-toe-smart-contract

# 2. Start Hardhat local node (in separate terminal)
cd tic-tac-toe-smart-contract
npm install
npx hardhat node
# Keep this running!

# 3. Configure environment (in DApp directory)
cd ../tic_tac_toe_compose
cp .env.example .env

# Edit .env with your settings:
# LOCAL=true
# HARDHAT_PROJECT_DIR=/path/to/tic-tac-toe-smart-contract
# PRIVATE_KEY_HARDHAT_0=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
# PRIVATE_KEY_HARDHAT_1=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d

# 4. Run the DApp
./gradlew run

# 5. In the UI, click "Deploy (npx tsx)" button
# Then click "Load Factory" → "Create Game (P1)" → start playing!
```

## 📦 Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd tic_tac_toe_compose
```

### 2. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# .env

# Network Configuration
LOCAL=true  # true for Hardhat, false for Sepolia

# LOCAL Network (Hardhat)
HARDHAT_CHAIN_ID=31337
LOCAL_RPC_URL=http://127.0.0.1:8545/
HARDHAT_PROJECT_DIR=/path/to/tic-tac-toe-smart-contract
NPX_PATH=npx

# Hardhat Test Accounts (from `npx hardhat node`)
PRIVATE_KEY_HARDHAT_0=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
PRIVATE_KEY_HARDHAT_1=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d

# SEPOLIA Testnet (optional)
SEPOLIA_CHAIN_ID=11155111
ALCHEMY_API_KEY=your_alchemy_api_key_here
SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/${ALCHEMY_API_KEY}

# Sepolia Accounts (create your own!)
PRIVATE_KEY_PLAYER1=your_private_key_1
PRIVATE_KEY_PLAYER2=your_private_key_2
```

**⚠️ SECURITY WARNING**: Never commit `.env` to Git! The `.gitignore` file already excludes it.

### 3. Install Dependencies

```bash
./gradlew build
```

This will download all Kotlin/Java dependencies including:
- Jetpack Compose Desktop
- Web3j
- Kotlin Serialization
- Testing frameworks

### 4. Run the Application

```bash
./gradlew run
```

Or use your IDE:
- **IntelliJ IDEA**: Open project → Run `Main.kt`
- **VS Code**: Use Kotlin extensions → Run Main

## 🎮 How to Play

### Step 1: Network Setup

Click the network toggle button:
- **LOCAL ✔** - Uses Hardhat local network (http://127.0.0.1:8545)
- **SEPOLIA ✔** - Uses Sepolia testnet

### Step 2: Deploy Contracts (LOCAL only)

If using LOCAL network:
1. Ensure Hardhat node is running: `npx hardhat node`
2. Click **Deploy (npx tsx)** button
3. Wait for "Deploy OK" status

### Step 3: Load Factory

Click **Load Factory** button to load the deployed factory contract address.

### Step 4: Create a Game

1. Click **Create Game (P1)**
2. A new game contract will be deployed
3. The game address will appear in the text field

### Step 5: Play

1. Select your player: Click **Signer: P1** (toggles to P2)
2. Enter move coordinates:
   - **Row**: 0-2 (top to bottom)
   - **Col**: 0-2 (left to right)
3. Click **Make Move**
4. Board updates automatically with emoji markers

### Step 6: Win!

- Get three in a row (horizontal, vertical, or diagonal)
- Status will show "You won!" or "Player X wins!"
- Game ends automatically

## 🏗️ Project Structure

```
tic_tac_toe_compose/
├── src/
│   ├── main/
│   │   ├── kotlin/vision/salient/
│   │   │   ├── Main.kt              # Application entry point
│   │   │   ├── Blockchain.kt        # Web3j integration (621 lines)
│   │   │   ├── TicTacToeScreen.kt   # Main UI (302 lines)
│   │   │   └── theme/Theme.kt       # Matrix theme colors
│   │   └── resources/
│   │       ├── deployment_output_hardhat_local.json
│   │       └── deployment_output_sepolia_testnet.json
│   └── test/
│       └── kotlin/vision/salient/
│           ├── BlockchainTest.kt            # Unit tests
│           ├── BlockchainIntegrationTest.kt # Integration tests
│           └── TicTacToeScreenTest.kt       # UI tests
├── build.gradle.kts                 # Gradle build configuration
├── .env                             # Environment variables (not in Git)
├── README.md                        # This file
├── DEVELOPER_GUIDE.md               # Architecture & development
├── DEPLOYMENT_GUIDE.md              # Deployment instructions
├── SECURITY.md                      # Security audit & best practices
└── CONTRACTS.md                     # Smart contract interface docs
```

## 🧪 Testing

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test Suite

```bash
# Unit tests only
./gradlew test --tests "BlockchainTest"

# Integration tests only (requires Hardhat running)
RUN_INTEGRATION_TESTS=true ./gradlew test --tests "BlockchainIntegrationTest"

# UI tests
./gradlew test --tests "TicTacToeScreenTest"
```

### Generate Code Coverage Report

```bash
./gradlew jacocoTestReport

# Open report
open build/reports/jacoco/test/html/index.html
```

**Coverage Goal**: 80%+ ✅

### Test Categories

- **Unit Tests** (`BlockchainTest.kt`):
  - Network configuration
  - Player credentials
  - Emoji generation
  - Input validation
  - Error handling

- **Integration Tests** (`BlockchainIntegrationTest.kt`):
  - Contract deployment
  - Game creation
  - Move submission
  - Board state reading
  - Win detection

- **UI Tests** (`TicTacToeScreenTest.kt`):
  - Component rendering
  - Button interactions
  - Text input
  - State management

## 📚 Documentation

- **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - Architecture, design patterns, and development workflow
- **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** - Step-by-step deployment for LOCAL and SEPOLIA
- **[SECURITY.md](SECURITY.md)** - Security audit, vulnerabilities, and best practices
- **[CONTRACTS.md](CONTRACTS.md)** - Smart contract interface documentation

## 🔧 Configuration

### Environment Variables Reference

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `LOCAL` | Network mode (true/false) | `true` | No |
| `HARDHAT_CHAIN_ID` | Hardhat chain ID | `31337` | No |
| `LOCAL_RPC_URL` | Local RPC endpoint | `http://127.0.0.1:8545/` | No |
| `HARDHAT_PROJECT_DIR` | Path to contract repo | `/Users/josephmalone/...` | Yes (LOCAL) |
| `NPX_PATH` | Path to npx | `npx` | No |
| `PRIVATE_KEY_HARDHAT_0` | Player 1 key (LOCAL) | - | Yes (LOCAL) |
| `PRIVATE_KEY_HARDHAT_1` | Player 2 key (LOCAL) | - | Yes (LOCAL) |
| `SEPOLIA_CHAIN_ID` | Sepolia chain ID | `11155111` | No |
| `ALCHEMY_API_KEY` | Alchemy API key | - | Yes (SEPOLIA) |
| `SEPOLIA_RPC_URL` | Sepolia RPC endpoint | Alchemy URL | No |
| `PRIVATE_KEY_PLAYER1` | Player 1 key (SEPOLIA) | - | Yes (SEPOLIA) |
| `PRIVATE_KEY_PLAYER2` | Player 2 key (SEPOLIA) | - | Yes (SEPOLIA) |

### Gradle Tasks

```bash
./gradlew tasks               # List all available tasks
./gradlew build               # Build project
./gradlew run                 # Run application
./gradlew test                # Run tests
./gradlew jacocoTestReport    # Generate coverage report
./gradlew clean               # Clean build artifacts
./gradlew packageDistribution # Create distributable package
```

## 🐛 Troubleshooting

### "Missing PK H0" Error

**Problem**: Application crashes with "Missing PK H0" error.

**Solution**: Add `PRIVATE_KEY_HARDHAT_0` and `PRIVATE_KEY_HARDHAT_1` to `.env` file.

### "No factory" Error

**Problem**: "No factory" error when creating game.

**Solution**:
1. Ensure contracts are deployed (LOCAL: click "Deploy" button)
2. Click "Load Factory" button before creating game

### "Connection refused" Error

**Problem**: Cannot connect to Hardhat node.

**Solution**:
1. Start Hardhat node: `npx hardhat node`
2. Ensure `LOCAL_RPC_URL=http://127.0.0.1:8545/` in `.env`
3. Check if port 8545 is available

### Deploy Button Does Nothing

**Problem**: Deploy button clicks but nothing happens.

**Solution**:
1. Check `HARDHAT_PROJECT_DIR` points to correct directory
2. Ensure TypeScript deployment script exists: `deployment/deploy_ethers.ts`
3. Check console output for error messages
4. Verify `npx tsx` is installed: `npm install -g tsx`

### "Bad cell" Error

**Problem**: "Bad cell" error when making move.

**Solution**: Ensure row and column are 0-2 (not 1-3).

## 🤝 Contributing

Contributions welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make changes and add tests
4. Run tests: `./gradlew test`
5. Commit: `git commit -m "Add your feature"`
6. Push: `git push origin feature/your-feature`
7. Create a Pull Request

### Code Standards

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Comments**: Use KDoc for public APIs
- **Tests**: Maintain 80%+ code coverage
- **Security**: Review [SECURITY.md](SECURITY.md) before committing

## 📄 License

[MIT License](LICENSE) (or specify your license)

## 🙏 Acknowledgments

- [Jetpack Compose](https://github.com/JetBrains/compose-jb) - Desktop UI framework
- [Web3j](https://github.com/web3j/web3j) - Ethereum Java library
- [Hardhat](https://hardhat.org/) - Ethereum development environment
- [Alchemy](https://www.alchemy.com/) - Web3 infrastructure

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/your-repo/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-repo/discussions)
- **Security**: See [SECURITY.md](SECURITY.md) for responsible disclosure

## 🔗 Links

- **Sepolia Testnet Faucet**: [Google Faucet](https://cloud.google.com/application/web3/faucet/ethereum/sepolia)
- **Web3j Documentation**: [https://docs.web3j.io/](https://docs.web3j.io/)
- **Compose Desktop**: [https://www.jetbrains.com/lp/compose-desktop/](https://www.jetbrains.com/lp/compose-desktop/)

---

**Made with ❤️ and Kotlin**

**Current Version**: 1.0.0-SNAPSHOT
**Last Updated**: 2025-11-15
