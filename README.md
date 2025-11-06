# Tic-Tac-Toe Compose

A blockchain-powered Tic-Tac-Toe desktop application built with Jetpack Compose and Ethereum smart contracts.

![Matrix Theme](https://img.shields.io/badge/Theme-Matrix-00FF00?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=flat-square)
![Compose](https://img.shields.io/badge/Compose-1.7.3-4285F4?style=flat-square)
![Web3j](https://img.shields.io/badge/Web3j-5.0.0-F16822?style=flat-square)

## 🎮 Overview

This is a desktop UI application that interfaces with Ethereum smart contracts to play Tic-Tac-Toe on the blockchain. Players can compete in a trustless, decentralized game where moves are recorded immutably on-chain.

### Features

- 🖥️ **Desktop Application**: Built with Jetpack Compose for a modern, reactive UI
- 🔗 **Blockchain Integration**: Uses Web3j to interact with Ethereum smart contracts
- 🌐 **Dual Network Support**: Play on local Hardhat network or Sepolia testnet
- 🎨 **Matrix Theme**: Retro green-on-black aesthetic
- 👥 **Two Player Support**: Switch between two player accounts
- 🎯 **Real-time Board Updates**: Live blockchain state synchronization
- 🐶 **Emoji Players**: Each address gets a unique emoji representation

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 17+**
- **Gradle 8.0+** (or use the included wrapper)
- **Git** (for version control)
- **Node.js & npm** (for running Hardhat deployment scripts)
- **[tic-tac-toe-smart-contract](https://github.com/yourusername/tic-tac-toe-smart-contract)** repository cloned locally

### For Sepolia Testnet:
- Alchemy API key (get free tier at [alchemy.com](https://www.alchemy.com/))
- Test ETH from [Sepolia faucet](https://cloud.google.com/application/web3/faucet/ethereum/sepolia)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/jlmalone/tic_tac_toe_compose.git
cd tic_tac_toe_compose
```

### 2. Configure Environment

Copy the example environment file and edit with your values:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:

```bash
# Essential configuration
LOCAL=true
HARDHAT_PROJECT_DIR=/path/to/your/tic-tac-toe-smart-contract

# For local development, the default Hardhat keys are pre-filled
# For Sepolia testnet, you'll need to add your own keys
```

**⚠️ SECURITY WARNING**: Never commit your `.env` file with real private keys! The `.gitignore` already excludes it.

### 3. Deploy Smart Contracts

#### Option A: Local Hardhat Network

1. Start a Hardhat node in your smart contract project:
   ```bash
   cd /path/to/tic-tac-toe-smart-contract
   npx hardhat node
   ```

2. Deploy contracts (or use the "Deploy" button in the app):
   ```bash
   npx tsx deployment/deploy_ethers.ts --local
   ```

3. Copy the deployment output to this project:
   ```bash
   cp deployment_output_hardhat_local.json /path/to/tic_tac_toe_compose/src/main/resources/
   ```

#### Option B: Sepolia Testnet

1. Set `LOCAL=false` in your `.env` file
2. Add your Alchemy API key and private keys to `.env`
3. Deploy to Sepolia:
   ```bash
   cd /path/to/tic-tac-toe-smart-contract
   npx tsx deployment/deploy_ethers.ts --sepolia
   ```
4. Copy the deployment output:
   ```bash
   cp deployment_output_sepolia_testnet.json /path/to/tic_tac_toe_compose/src/main/resources/
   ```

### 4. Build and Run

```bash
./gradlew run
```

Or build a distributable package:

```bash
./gradlew package
```

The application will appear in `build/compose/binaries/main/`.

## 🎯 How to Play

### Starting a Game

1. **Switch Network** (if needed): Click "LOCAL ✔" or "SEPOLIA ✔" to toggle between networks
2. **Load Factory**: Click "Load Factory" to load the smart contract factory address
3. **Create Game**: Click "Create Game (P1)" to deploy a new game instance
4. **Join Game**: The game address will auto-populate. Click "Join Game" to confirm

### Making Moves

1. **Select Player**: Click "Signer: P1" or "Signer: P2" to switch between players
2. **Choose Position**: Enter row (0-2) and column (0-2)
3. **Submit Move**: Click "Make Move" to submit to the blockchain
4. **Wait for Confirmation**: The transaction will be confirmed and the board will update

### Debug Controls

- **🔄 Refresh Board**: Manually refresh the board state from the contract
- **🏁 gameEnded**: Check if the game has ended
- **👤 lastPlayer**: View the last player who made a move
- **🏆 winner**: Check the winner (or 0x0000... for draw/ongoing)
- **📍 currentGame**: Display the current game address
- **🏭 factoryAddr**: Display the factory contract address

## 🏗️ Project Structure

```
tic_tac_toe_compose/
├── src/main/kotlin/vision/salient/
│   ├── Blockchain.kt          # Web3 integration layer
│   ├── Main.kt                # Application entry point
│   ├── TicTacToeScreen.kt     # Main UI composable
│   └── theme/
│       └── Theme.kt           # Matrix color theme
├── src/main/resources/
│   ├── deployment_output_hardhat_local.json
│   └── deployment_output_sepolia_testnet.json
├── build.gradle.kts           # Build configuration
├── settings.gradle.kts        # Gradle settings
├── .env.example               # Environment template
└── README.md                  # This file
```

## 🛠️ Architecture

### Components

#### Blockchain.kt
The core Web3 service object that handles:
- Network configuration and switching
- Smart contract interaction via Web3j
- Transaction management and gas pricing
- Board state decoding
- Player credential management

#### TicTacToeScreen.kt
The main UI component featuring:
- Reactive state management with Compose
- Coroutine-based async operations
- Real-time board visualization
- Player controls and move submission
- Debug tools for contract inspection

#### Theme.kt
Matrix-inspired visual theme:
- Green (#00FF00) primary color
- Black (#000000) background
- High contrast for retro aesthetic

### Smart Contract Integration

The application interfaces with two smart contracts:

1. **Factory Contract**: Deploys new game instances
2. **Game Contract**: Manages individual Tic-Tac-Toe games

Each game stores:
- 3×3 board of Ethereum addresses (0x0 = empty, address = claimed by player)
- Current turn and game state
- Winner (if game ended)

## 🔧 Configuration

### Environment Variables

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `LOCAL` | Yes | Use local network (true) or Sepolia (false) | `true` |
| `HARDHAT_PROJECT_DIR` | Yes | Path to smart contract project | `~/tic-tac-toe-smart-contract` |
| `LOCAL_RPC_URL` | No | Local RPC endpoint | `http://127.0.0.1:8545/` |
| `HARDHAT_CHAIN_ID` | No | Local chain ID | `31337` |
| `PRIVATE_KEY_HARDHAT_0` | Yes* | Player 1 key (local) | Hardhat default |
| `PRIVATE_KEY_HARDHAT_1` | Yes* | Player 2 key (local) | Hardhat default |
| `ALCHEMY_API_KEY` | Yes** | Alchemy API key | - |
| `SEPOLIA_RPC_URL` | No | Sepolia RPC endpoint | Alchemy URL |
| `SEPOLIA_CHAIN_ID` | No | Sepolia chain ID | `11155111` |
| `PRIVATE_KEY_PLAYER1` | Yes** | Player 1 key (Sepolia) | - |
| `PRIVATE_KEY_PLAYER2` | Yes** | Player 2 key (Sepolia) | - |
| `NPX_PATH` | No | Custom npx path | `npx` |

\* Required for local development
\** Required for Sepolia testnet

## 🧪 Testing

Run the test suite:

```bash
./gradlew test
```

Run with coverage:

```bash
./gradlew test jacocoTestReport
```

Coverage reports will be generated in `build/reports/jacoco/test/html/`.

## 📦 Building Distributions

### macOS (DMG)
```bash
./gradlew packageDmg
```

### Windows (MSI)
```bash
./gradlew packageMsi
```

### Linux (DEB)
```bash
./gradlew packageDeb
```

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

### Development Workflow

1. **Fork the repository**
2. **Create a feature branch**:
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Make your changes**:
   - Follow Kotlin coding conventions
   - Add tests for new functionality
   - Update documentation as needed
4. **Commit your changes**:
   ```bash
   git commit -m "Add amazing feature"
   ```
5. **Push to your fork**:
   ```bash
   git push origin feature/amazing-feature
   ```
6. **Open a Pull Request**

### Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and concise
- Prefer immutability where possible

### Commit Messages

- Use present tense ("Add feature" not "Added feature")
- Use imperative mood ("Move cursor to..." not "Moves cursor to...")
- First line should be 50 characters or less
- Reference issues and pull requests when relevant

## 🐛 Troubleshooting

### Common Issues

#### "Missing PRIVATE_KEY_HARDHAT_0 in .env"
- Ensure you've copied `.env.example` to `.env`
- Check that all required environment variables are set

#### "Could not find deployment_output_hardhat_local.json"
- Run the deployment script in your smart contract project
- Copy the output JSON to `src/main/resources/`

#### "Transaction underpriced"
- The app automatically retries with higher gas
- If persistent, check your network conditions

#### Board not updating
- Click "🔄 Refresh Board" to manually sync
- Check that transactions are confirming on the blockchain

#### "No game address set"
- Click "Load Factory" first
- Create a new game or paste an existing game address

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔗 Related Projects

- [tic-tac-toe-smart-contract](https://github.com/yourusername/tic-tac-toe-smart-contract) - Solidity smart contracts

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/jlmalone/tic_tac_toe_compose/issues)
- **Discussions**: [GitHub Discussions](https://github.com/jlmalone/tic_tac_toe_compose/discussions)

## 🙏 Acknowledgments

- Jetpack Compose team for the excellent UI framework
- Web3j team for Ethereum integration tools
- Hardhat for local development environment

---

**Made with ❤️ and ☕ by the blockchain community**
