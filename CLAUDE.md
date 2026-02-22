# CLAUDE.md — tic_tac_toe_compose

> **WARNING: THIS PROJECT IS IN A BROKEN STATE.** Last commit: "Totally broken state". Investigation and fix is a priority. See ecosystem roadmap.

## Tic-Tac-Toe Blockchain Ecosystem

This project is part of a 6-project ecosystem. **Flagship:** [tic-tac-toe-eth-react](~/WebstormProjects/tic-tac-toe-eth-react/) ([ECOSYSTEM.md](~/WebstormProjects/tic-tac-toe-eth-react/ECOSYSTEM.md))

| Project | Path | Chain | Status |
|---------|------|-------|--------|
| **tic-tac-toe-eth-react** (flagship) | `~/WebstormProjects/tic-tac-toe-eth-react/` | ETH Sepolia | Live |
| tic-tac-toe-smart-contract | `~/tic-tac-toe-smart-contract/` | ETH Sepolia | Deployed |
| tic_tac_toe_android | `~/StudioProjects/tic_tac_toe_android/` | ETH Sepolia | Working |
| tic_tac_toe_ios_ethereum | `~/ios_code/tic_tac_toe_ios_ethereum/` | ETH Sepolia | Working |
| **tic_tac_toe_compose** (this repo) | `~/IdeaProjects/tic_tac_toe_compose/` | ETH Sepolia | **BROKEN** |
| tic-tac-toe-sol | `~/RustroverProjects/tic-tac-toe-sol/` | Solana Devnet | Deployed |

**Shared Addresses (Sepolia):** Factory `0xa0B53DbDb0052403E38BBC31f01367aC6782118E` / Game `0x340AC014d800Ac398Af239Cebc3a376eb71B0353`

---

## Project Overview

Kotlin Compose Desktop client for the Tic-Tac-Toe smart contract system. Shares Web3j library with the Android client — a future KMP unification is planned.

## Tech Stack

- **Kotlin 2.1.0**
- **Jetbrains Compose 1.7.3** (Desktop)
- **Web3j 5.0.0** for Ethereum interaction
- **dotenv-kotlin 6.4.1** for env config
- **kotlinx-serialization-json**
- **kotlinx-coroutines 1.8.1**

## Priority: FIX THIS PROJECT

The project is broken. Likely causes to investigate:
1. Dependency version conflicts (Kotlin 2.1.0 + Compose 1.7.3 compatibility)
2. Compose Desktop API changes
3. Web3j integration breakage
4. Missing or outdated deployment config

## Build & Run

```bash
./gradlew run        # Run desktop app
./gradlew build      # Build (currently fails)
```

## Future: KMP Code Sharing

The Android and Compose Desktop projects both use Web3j and share similar game logic. A KMP (Kotlin Multiplatform) refactor would unify the shared code into a common module.
