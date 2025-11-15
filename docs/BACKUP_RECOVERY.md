# Backup & Recovery Guide
## Private Key Management, State Recovery & Disaster Recovery Procedures

**Version**: 1.0
**Last Updated**: 2025-11-15

---

## Table of Contents
1. [Private Key Security](#private-key-security)
2. [State Backup Procedures](#state-backup-procedures)
3. [Recovery Procedures](#recovery-procedures)
4. [Disaster Recovery Scenarios](#disaster-recovery-scenarios)
5. [Security Best Practices](#security-best-practices)
6. [Incident Response](#incident-response)

---

## Private Key Security

### Understanding Private Keys

**What is a Private Key?**
- 256-bit (32-byte) random number
- Controls access to Ethereum account
- **Loss = permanent loss of funds and game access**
- **Compromise = attacker gains full control**

**Example**:
```
Private Key (Hex):
0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80

Derived Public Address:
0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
```

---

### Storage Options (Security Ranking)

| Method | Security | Convenience | Cost | Use Case |
|--------|----------|-------------|------|----------|
| **Hardware Wallet** (Ledger/Trezor) | ⭐⭐⭐⭐⭐ | ⭐⭐ | $50-150 | Production, mainnet |
| **Encrypted File** (GPG/AES) | ⭐⭐⭐⭐ | ⭐⭐⭐ | Free | Development backup |
| **Password Manager** (1Password) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | $3-8/mo | Development primary |
| **Environment Variable** (.env) | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Free | Local testing only |
| **Paper Wallet** (BIP-39 mnemonic) | ⭐⭐⭐⭐⭐ | ⭐ | Free | Long-term cold storage |
| **Keystore File** (encrypted JSON) | ⭐⭐⭐ | ⭐⭐⭐ | Free | Compatible with MetaMask |
| **Plaintext File** | ⭐ (NEVER) | ⭐⭐⭐⭐⭐ | Free | ❌ Never use |

---

### Current Implementation Analysis

**File**: `.env` (gitignored, plaintext on disk)

```bash
# ⚠️ SECURITY RISK: Plaintext storage
PRIVATE_KEY_HARDHAT_0=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
PRIVATE_KEY_HARDHAT_1=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
PRIVATE_KEY_PLAYER1=0x...
PRIVATE_KEY_PLAYER2=0x...
```

**Vulnerabilities**:
1. ❌ Readable by any process with file access
2. ❌ Visible in text editor history
3. ❌ Exposed if `.gitignore` fails
4. ❌ No encryption at rest
5. ❌ No audit trail (who accessed?)

**Acceptable For**:
- ✅ Hardhat local testing (pre-funded test accounts)
- ✅ Sepolia testnet (minimal value)

**NOT Acceptable For**:
- ❌ Mainnet (real ETH value)
- ❌ Production applications
- ❌ Shared development machines

---

### Recommended: Hardware Wallet Integration

**Ledger Integration (Future Enhancement)**:

```kotlin
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import java.math.BigInteger

class LedgerCredentials(
    private val ledgerPath: String = "44'/60'/0'/0/0"
) {
    fun getAddress(): String {
        // Query Ledger device via USB HID
        val address = LedgerHID.getAddress(ledgerPath)
        return address
    }

    fun signTransaction(rawTx: RawTransaction, chainId: Long): Sign.SignatureData {
        // User confirms on Ledger device
        val signature = LedgerHID.signTransaction(ledgerPath, rawTx, chainId)
        return signature
    }
}

// Usage in Blockchain.kt
fun getPlayerCredentials(index: Int): Credentials {
    return if (USE_HARDWARE_WALLET) {
        LedgerCredentials("44'/60'/0'/0/$index")
    } else {
        Credentials.create(dotenv["PRIVATE_KEY_PLAYER$index"])
    }
}
```

**Benefits**:
- Private key never leaves device
- User must physically approve each transaction
- Protected from malware/keyloggers
- Industry standard for high-value accounts

---

### Intermediate: Encrypted Keystore

**MetaMask-Compatible Keystore (JSON)**:

```kotlin
import org.web3j.crypto.WalletUtils
import java.io.File

fun createEncryptedWallet(password: String, outputDir: File): String {
    // Generates new random private key and encrypts it
    val fileName = WalletUtils.generateNewWalletFile(
        password,
        outputDir,
        false  // useFullScrypt = false for faster unlock
    )

    println("Wallet created: $outputDir/$fileName")
    return fileName
}

fun loadEncryptedWallet(password: String, keystoreFile: File): Credentials {
    return WalletUtils.loadCredentials(password, keystoreFile)
}

// Usage
fun getPlayerCredentials(index: Int): Credentials {
    val keystoreFile = File("wallets/player$index.json")

    return if (keystoreFile.exists()) {
        // Prompt user for password (GUI dialog or CLI input)
        val password = promptForPassword()
        loadEncryptedWallet(password, keystoreFile)
    } else {
        // Fallback to .env
        Credentials.create(dotenv["PRIVATE_KEY_PLAYER$index"])
    }
}
```

**Keystore Format** (UTC/JSON):
```json
{
  "address": "f39fd6e51aad88f6f4ce6ab8827279cfffb92266",
  "crypto": {
    "cipher": "aes-128-ctr",
    "ciphertext": "7b9a2c...",
    "cipherparams": {
      "iv": "83dbcc..."
    },
    "kdf": "scrypt",
    "kdfparams": {
      "dklen": 32,
      "n": 262144,
      "p": 1,
      "r": 8,
      "salt": "ab0e1f..."
    },
    "mac": "2e9d6b..."
  },
  "id": "uuid-here",
  "version": 3
}
```

**Benefits**:
- Compatible with MetaMask, MyEtherWallet, MyCrypto
- Password-protected (PBKDF2/scrypt)
- Industry standard (Ethereum Keystore v3)
- Portable across wallets

---

### BIP-39 Mnemonic Phrase (Recommended for Backup)

**Mnemonic to Private Key Derivation**:

```kotlin
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.Bip32ECKeyPair

fun mnemonicToCredentials(
    mnemonic: String,
    accountIndex: Int = 0
): Credentials {
    // 1. Mnemonic → Seed (512-bit)
    val seed = MnemonicUtils.generateSeed(mnemonic, "")  // No passphrase

    // 2. Seed → Master Key Pair
    val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)

    // 3. Derive child key using BIP-44 path
    // Path: m/44'/60'/0'/0/{accountIndex}
    val path = intArrayOf(
        44 or Bip32ECKeyPair.HARDENED_BIT,   // Purpose: BIP-44
        60 or Bip32ECKeyPair.HARDENED_BIT,   // Coin Type: Ethereum
        0 or Bip32ECKeyPair.HARDENED_BIT,    // Account: 0
        0,                                     // Chain: External
        accountIndex                          // Address Index
    )

    val childKeypair = Bip32ECKeyPair.deriveKeyPair(masterKeypair, path)

    // 4. Create Web3j Credentials
    return Credentials.create(childKeypair)
}

// Generate new mnemonic
fun generateMnemonic(): String {
    val initialEntropy = ByteArray(16)  // 128-bit → 12 words
    // OR: ByteArray(32) for 24 words
    java.security.SecureRandom().nextBytes(initialEntropy)

    return MnemonicUtils.generateMnemonic(initialEntropy)
}

// Example usage
val mnemonic = generateMnemonic()
println("🔑 BACKUP THIS PHRASE (write on paper):")
println(mnemonic)
// Output: "witch collapse practice feed shame open despair creek road again ice least"

val player1 = mnemonicToCredentials(mnemonic, 0)
val player2 = mnemonicToCredentials(mnemonic, 1)
```

**Mnemonic Backup Checklist**:
- [ ] Write on paper (not digital)
- [ ] Store in fireproof/waterproof safe
- [ ] Use metal backup (Cryptosteel, Billfodl)
- [ ] Never take photo of mnemonic
- [ ] Never enter into computer (except when restoring)
- [ ] Test recovery before funding account

---

## State Backup Procedures

### What to Backup

| Data Type | Backup Frequency | Storage Location | Recovery Priority |
|-----------|-----------------|------------------|-------------------|
| **Private Keys** | Once (never changes) | Offline, encrypted | 🔥 Critical |
| **Deployment Addresses** | Per deployment | Git repo + docs | 🔴 High |
| **Game History** | Daily/weekly | Off-chain DB | 🟡 Medium |
| **RPC Endpoints** | Per config change | `.env` + docs | 🟢 Low |
| **Contract Source** | Per version | Git repo | 🟡 Medium |

---

### Backup 1: Private Keys (Offline)

**Method 1: Encrypted Archive**

```bash
# 1. Create backup file
cat > keys_backup.txt <<EOF
PRIVATE_KEY_PLAYER1=0x...
PRIVATE_KEY_PLAYER2=0x...
EOF

# 2. Encrypt with GPG (symmetric)
gpg --symmetric --cipher-algo AES256 keys_backup.txt
# Prompts for passphrase (use strong password!)

# Output: keys_backup.txt.gpg (encrypted)

# 3. Delete plaintext
shred -u keys_backup.txt  # Securely delete original

# 4. Store encrypted file in:
#    - Password manager (as file attachment)
#    - USB drive (in fireproof safe)
#    - Separate cloud storage (Google Drive, Dropbox)

# To restore:
gpg --decrypt keys_backup.txt.gpg > keys_backup.txt
```

**Method 2: Paper Wallet (BIP-39)**

```
┌─────────────────────────────────────────────────────┐
│         ETHEREUM WALLET RECOVERY PHRASE             │
│  ─────────────────────────────────────────────────  │
│                                                     │
│   1. witch        7. despair     13. [N/A]         │
│   2. collapse     8. creek       14. [N/A]         │
│   3. practice     9. road        15. [N/A]         │
│   4. feed        10. again       16. [N/A]         │
│   5. shame       11. ice         17. [N/A]         │
│   6. open        12. least       18. [N/A]         │
│                                                     │
│  Date Created: 2025-11-15                          │
│  Derivation Path: m/44'/60'/0'/0/N                 │
│  ─────────────────────────────────────────────────  │
│  ⚠️  KEEP SECRET - Full access to funds            │
│  ⚠️  Store in fireproof safe                       │
└─────────────────────────────────────────────────────┘
```

**Print on acid-free paper, laminate, store in safe.**

---

### Backup 2: Contract Deployment Addresses

**File**: `deployment_addresses_backup.json`

```json
{
  "backupDate": "2025-11-15T12:00:00Z",
  "networks": {
    "hardhat_local": {
      "rpcUrl": "http://127.0.0.1:8545/",
      "chainId": 31337,
      "factoryAddress": "0xA51c1fc2f0D1a1b8494Ed1FE312d7C3a78Ed91C0",
      "gameImplementationAddress": "0xB7f8BC63BbcaD18155201308C8f3540b07f84F5e",
      "deploymentBlock": 1,
      "deploymentTxHash": "0x..."
    },
    "sepolia_testnet": {
      "rpcUrl": "https://eth-sepolia.g.alchemy.com/v2/...",
      "chainId": 11155111,
      "factoryAddress": "0xa0B53DbDb0052403E38BBC31f01367aC6782118E",
      "gameImplementationAddress": "0x340AC014d800Ac398Af239Cebc3a376eb71B0353",
      "deploymentBlock": 4567890,
      "deploymentTxHash": "0x...",
      "etherscanLink": "https://sepolia.etherscan.io/address/0xa0B53DbDb..."
    }
  }
}
```

**Storage**:
- Primary: Git repository (committed to version control)
- Secondary: Documentation wiki/Confluence
- Tertiary: Cloud storage (Google Drive, Dropbox)

**Recovery**: Copy JSON file to `src/main/resources/`

---

### Backup 3: Full Game State Export

**Script**: `export_all_games.kt`

```kotlin
suspend fun exportAllGames(outputFile: File) = withContext(Dispatchers.IO) {
    val export = GameDatabaseExport(
        exportDate = Instant.now().toString(),
        network = if (Blockchain.isLocal) "hardhat_local" else "sepolia_testnet",
        factoryAddress = Blockchain.getFactoryAddress() ?: error("No factory"),
        games = mutableListOf()
    )

    val allGameAddresses = getAllCreatedGames()

    allGameAddresses.forEach { gameAddr ->
        Blockchain.setCurrentGameAddress(gameAddr)

        val board = Blockchain.getBoardState()
        val ended = Blockchain.readBool("gameEnded")
        val winner = if (ended) Blockchain.readAddress("winner") else null
        val moves = getGameMoves()
        val creationBlock = getGameCreationBlock(gameAddr)
        val creationTime = creationBlock?.let { getBlockTimestamp(it) }

        export.games.add(GameExportData(
            address = gameAddr,
            board = board,
            gameEnded = ended,
            winner = winner,
            moves = moves.map { MoveExportData(it.player, it.row, it.col, it.blockNumber) },
            creationBlock = creationBlock,
            creationTimestamp = creationTime
        ))
    }

    // Write to JSON
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }.encodeToString(export)

    outputFile.writeText(json)
    println("✅ Exported ${export.games.size} games to ${outputFile.absolutePath}")
}

@Serializable
data class GameDatabaseExport(
    val exportDate: String,
    val network: String,
    val factoryAddress: String,
    val games: List<GameExportData>
)

@Serializable
data class GameExportData(
    val address: String,
    val board: List<List<String>>,
    val gameEnded: Boolean,
    val winner: String?,
    val moves: List<MoveExportData>,
    val creationBlock: Long?,
    val creationTimestamp: Long?
)

@Serializable
data class MoveExportData(
    val player: String,
    val row: Int,
    val col: Int,
    val blockNumber: Long
)
```

**Usage**:
```kotlin
scope.launch {
    exportAllGames(File("game_backup_${System.currentTimeMillis()}.json"))
}
```

**Backup Schedule**:
- Development: Weekly
- Production: Daily (automated cron job)
- Pre-migration: Immediately before network changes

**Storage**:
- Primary: AWS S3 / Google Cloud Storage
- Secondary: Local RAID array
- Tertiary: External hard drive (offline)

---

## Recovery Procedures

### Recovery 1: Lost Private Key (No Backup)

**Status**: ❌ **UNRECOVERABLE**

**Impact**:
- Permanent loss of account access
- Cannot create new games with that account
- Cannot make moves in existing games
- ETH balance locked forever

**Prevention**: ALWAYS backup private keys before use

---

### Recovery 2: Lost Private Key (Mnemonic Backup Available)

**Status**: ✅ **RECOVERABLE**

**Steps**:
```kotlin
// 1. Restore from mnemonic
val mnemonic = "witch collapse practice feed shame open despair creek road again ice least"
val player1Restored = mnemonicToCredentials(mnemonic, 0)
val player2Restored = mnemonicToCredentials(mnemonic, 1)

println("Restored Player1: ${player1Restored.address}")
println("Restored Player2: ${player2Restored.address}")

// 2. Update .env file
File(".env").appendText("""
PRIVATE_KEY_PLAYER1=${player1Restored.ecKeyPair.privateKey.toString(16)}
PRIVATE_KEY_PLAYER2=${player2Restored.ecKeyPair.privateKey.toString(16)}
""")

// 3. Reload Blockchain.kt
Blockchain.applyLocal(Blockchain.isLocal)  // Refresh credentials
```

**Verification**:
```kotlin
// Verify addresses match
val expectedAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
val restoredAddress = player1Restored.address

require(restoredAddress.lowercase() == expectedAddress.lowercase()) {
    "❌ Address mismatch! Backup may be corrupted."
}

println("✅ Private key successfully restored")
```

---

### Recovery 3: Corrupted Deployment JSON

**Status**: ✅ **RECOVERABLE** (from blockchain)

**Steps**:
```kotlin
suspend fun recoverDeploymentAddresses(factoryTxHash: String): DeploymentAddresses =
    withContext(Dispatchers.IO) {
        // 1. Get transaction receipt
        val receipt = web3j.ethGetTransactionReceipt(factoryTxHash)
            .send()
            .transactionReceipt
            .orElseThrow { error("Transaction not found") }

        // 2. Factory address = receipt.contractAddress
        val factoryAddress = receipt.contractAddress

        // 3. Query factory for implementation address
        Blockchain.setCurrentGameAddress(factoryAddress)

        val fn = Function(
            "implementation",
            emptyList(),
            listOf(object : TypeReference<Address>() {})
        )
        val callData = FunctionEncoder.encode(fn)

        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                "0x0000000000000000000000000000000000000000",
                factoryAddress,
                callData
            ),
            DefaultBlockParameterName.LATEST
        ).send().result

        val implAddress = "0x" + raw.takeLast(40)

        // 4. Save to JSON
        val deployment = DeploymentAddresses(
            factoryAddress = factoryAddress,
            gameImplementationAddress = implAddress
        )

        val json = Json.encodeToString(deployment)
        val outputFile = if (Blockchain.isLocal)
            "deployment_output_hardhat_local.json"
        else
            "deployment_output_sepolia_testnet.json"

        File("src/main/resources/$outputFile").writeText(json)

        println("✅ Recovered deployment addresses:")
        println("   Factory: $factoryAddress")
        println("   Implementation: $implAddress")

        deployment
    }
```

**Required Input**: Transaction hash of factory deployment

---

### Recovery 4: Database Corruption (Lost Game History)

**Status**: ✅ **RECOVERABLE** (blockchain is source of truth)

**Steps**:
```kotlin
suspend fun rebuildDatabase() = withContext(Dispatchers.IO) {
    println("🔄 Rebuilding database from blockchain...")

    // 1. Clear local cache
    // (No local DB in current implementation, skip)

    // 2. Re-scan all GameCreated events
    val allGames = getAllCreatedGames()
    println("Found ${allGames.size} games")

    // 3. Reconstruct each game's state
    val reconstructedGames = allGames.mapIndexed { index, gameAddr ->
        println("Reconstructing game ${index + 1}/${allGames.size}: $gameAddr")

        Blockchain.setCurrentGameAddress(gameAddr)

        val board = Blockchain.getBoardState()
        val ended = Blockchain.readBool("gameEnded")
        val winner = if (ended) Blockchain.readAddress("winner") else null
        val moves = getGameMoves()

        GameState(gameAddr, board, ended, winner, moves)
    }

    println("✅ Database rebuilt: ${reconstructedGames.size} games")
    reconstructedGames
}
```

**Recovery Time**:
- 10 games: ~30 seconds
- 100 games: ~5 minutes
- 1000 games: ~30 minutes

---

## Disaster Recovery Scenarios

### Scenario 1: Developer Laptop Stolen

**Impact**:
- Private keys potentially compromised
- Source code lost (if not in Git)
- Local configuration lost

**Recovery Steps**:

1. **Immediately**: Transfer funds from compromised accounts
   ```kotlin
   // Use backup mnemonic to restore keys on new machine
   val compromised = mnemonicToCredentials(mnemonic, 0)

   // Create new account
   val newMnemonic = generateMnemonic()
   val safe = mnemonicToCredentials(newMnemonic, 0)

   // Transfer ETH to new account
   val txHash = sendEth(
       from = compromised,
       to = safe.address,
       amount = compromised.balance - gasBuffer
   )
   ```

2. **Same day**: Rotate all API keys
   - Alchemy API key
   - Infura API key
   - Etherscan API key

3. **Within 24 hours**: Audit all transactions
   ```kotlin
   val recentTxs = getRecentTransactions(compromised.address, last24Hours)
   recentTxs.forEach { tx ->
       if (!isAuthorized(tx)) {
           println("🚨 UNAUTHORIZED TX: ${tx.hash}")
           // Report to authorities if value is significant
       }
   }
   ```

4. **Within 1 week**: Update all documentation with new addresses

---

### Scenario 2: RPC Provider (Alchemy) Shutdown

**Impact**:
- Cannot connect to Sepolia network
- All transactions blocked
- Read operations fail

**Recovery Steps**:

```kotlin
// Immediate failover (< 5 minutes)
fun switchToBackupRPC() {
    val backupEndpoints = listOf(
        "https://sepolia.infura.io/v3/YOUR_INFURA_KEY",
        "https://rpc.sepolia.org",
        "https://eth-sepolia.public.blastapi.io"
    )

    for (endpoint in backupEndpoints) {
        try {
            val testWeb3j = Web3j.build(HttpService(endpoint))
            testWeb3j.ethBlockNumber().send()  // Health check

            // Success: Update global Web3j instance
            Blockchain.httpService = HttpService(endpoint)
            Blockchain.web3j = Web3j.build(Blockchain.httpService)

            println("✅ Switched to backup RPC: $endpoint")
            return
        } catch (e: Exception) {
            println("⚠️ Backup RPC failed: $endpoint")
        }
    }

    error("❌ All RPC endpoints unavailable")
}
```

**Prevention**: Configure multi-provider setup from day 1

---

### Scenario 3: Smart Contract Bug Discovered

**Impact**:
- New games may be vulnerable
- Existing games unaffected (immutable bytecode)

**Recovery Steps**:

1. **Hour 0**: Stop creating new games
   ```kotlin
   // Disable game creation in UI
   Button(
       onClick = { /* disabled */ },
       enabled = false
   ) {
       Text("⚠️ Game creation temporarily disabled")
   }
   ```

2. **Hour 1-24**: Deploy fixed contracts
   ```bash
   # In smart contract project
   cd $HARDHAT_PROJECT_DIR

   # Update contract code
   vim contracts/TicTacToeGame.sol

   # Deploy v1.1
   npx hardhat run scripts/deploy_v1.1.ts --network sepolia

   # Output:
   # GameImplementation v1.1: 0xNEW_IMPL_ADDRESS
   # Factory v1.1: 0xNEW_FACTORY_ADDRESS
   ```

3. **Day 2-7**: Communicate to users
   ```
   Subject: Security Update - New Factory Address

   We've deployed an updated factory contract to fix [bug description].

   Old Factory (DEPRECATED): 0xOLD_FACTORY_ADDRESS
   New Factory (USE THIS): 0xNEW_FACTORY_ADDRESS

   Existing games are NOT affected and remain playable.
   Please update your application to version v1.1.
   ```

4. **Week 2+**: Phase out old factory
   ```kotlin
   val oldFactory = "0xOLD_FACTORY_ADDRESS"
   if (Blockchain.getFactoryAddress()?.lowercase() == oldFactory.lowercase()) {
       showDialog("⚠️ Update Required",
                  "You're using an outdated factory. Please update to v1.1.")
   }
   ```

---

### Scenario 4: Hardhat Local Node Crashed

**Impact**:
- All local games lost (ephemeral state)
- Development workflow interrupted

**Recovery Steps**:

```bash
# 1. Restart Hardhat node
cd $HARDHAT_PROJECT_DIR
npx hardhat node  # Starts fresh chain (all state lost)

# 2. Redeploy contracts
npx tsx deployment/deploy_ethers.ts --local

# 3. Update deployment JSON in Kotlin project
cp deployment_output_hardhat_local.json \
   ../tic_tac_toe_compose/src/main/resources/

# 4. Restart Kotlin app
cd ../tic_tac_toe_compose
./gradlew run
```

**Prevention**: Use persistent Hardhat chain (not recommended for development)

---

## Security Best Practices

### Development Environment

✅ **DO**:
- Use separate accounts for local/testnet/mainnet
- Keep mainnet keys in hardware wallet ONLY
- Use `.gitignore` for `.env` files
- Rotate testnet keys monthly
- Enable 2FA on all cloud services (GitHub, Alchemy, etc.)

❌ **DON'T**:
- Commit private keys to Git (even in private repos)
- Share `.env` files via email/Slack
- Use production keys on shared development machines
- Store passwords in browser autofill
- Reuse passwords across services

---

### Production Environment

✅ **DO**:
- Use hardware wallets (Ledger, Trezor) for all mainnet accounts
- Implement multi-signature wallets for high-value operations
- Use encrypted keystores for server-side applications
- Rotate API keys quarterly
- Monitor accounts for unauthorized transactions
- Use cold wallets for long-term storage (>90% of funds)

❌ **DON'T**:
- Store mainnet keys in `.env` files
- Use single points of failure (1 key controls everything)
- Keep keys on internet-connected machines
- Use custodial services for large amounts (>$10k)
- Delay security updates

---

### Operational Security

**Access Control Matrix**:

| Asset | Developer (Local) | Developer (Testnet) | Production Admin | User |
|-------|------------------|---------------------|------------------|------|
| Hardhat Private Keys | ✅ Full access | ❌ No access | ❌ No access | ❌ |
| Testnet Private Keys | ✅ Read-only | ✅ Full access | ❌ No access | ❌ |
| Mainnet Private Keys | ❌ No access | ❌ No access | ✅ Hardware wallet | ✅ Own keys |
| Factory Contract | ✅ Deploy local | ✅ Deploy testnet | ✅ Deploy mainnet | ❌ Read-only |
| RPC API Keys | ✅ Shared key | ✅ Shared key | ✅ Dedicated key | ❌ (use public RPC) |

---

## Incident Response

### Response Plan

**Phase 1: Detection (0-5 minutes)**
1. Identify incident (unauthorized transaction, compromised key, etc.)
2. Assess severity (Low/Medium/High/Critical)
3. Alert team (Slack, PagerDuty, email)

**Phase 2: Containment (5-30 minutes)**
1. Transfer funds from compromised accounts to safe accounts
2. Revoke API keys
3. Block compromised accounts in application
4. Take snapshot of evidence (transaction logs, server logs)

**Phase 3: Investigation (1-24 hours)**
1. Analyze attack vector (phishing, malware, insider threat, etc.)
2. Identify affected accounts
3. Document timeline of events
4. Preserve evidence for potential law enforcement

**Phase 4: Recovery (1-7 days)**
1. Deploy new contracts if necessary
2. Restore from backups
3. Update deployment addresses
4. Communicate to users

**Phase 5: Post-Incident (1-4 weeks)**
1. Conduct root cause analysis
2. Implement preventive measures
3. Update security policies
4. Provide incident report to stakeholders

---

### Emergency Contacts

```
┌────────────────────────────────────────────────────┐
│            EMERGENCY CONTACT CARD                  │
│  ────────────────────────────────────────────────  │
│  Security Incident Hotline: +1-XXX-XXX-XXXX       │
│  Email: security@yourcompany.com                   │
│  Slack: #security-incidents                        │
│  ────────────────────────────────────────────────  │
│  RPC Provider Support:                             │
│    Alchemy: support@alchemy.com                    │
│    Infura: support.infura.io                       │
│  ────────────────────────────────────────────────  │
│  Legal/Compliance: legal@yourcompany.com           │
│  ────────────────────────────────────────────────  │
│  Law Enforcement (if funds stolen):                │
│    FBI Internet Crime Complaint Center (IC3)       │
│    https://www.ic3.gov                             │
└────────────────────────────────────────────────────┘
```

---

## Recovery Checklist

### Private Key Compromise

- [ ] Transfer all funds to new account immediately
- [ ] Rotate all API keys within 1 hour
- [ ] Audit recent transactions for unauthorized activity
- [ ] Update `.env` with new keys
- [ ] Notify affected users if necessary
- [ ] File incident report
- [ ] Implement 2FA on all services
- [ ] Review access logs for intrusion

### Data Loss

- [ ] Attempt restore from latest backup
- [ ] If no backup: Reconstruct from blockchain
- [ ] Verify data integrity after restore
- [ ] Test application functionality
- [ ] Document lessons learned
- [ ] Implement automated backups

### Contract Vulnerability

- [ ] Pause affected operations (if possible)
- [ ] Deploy patched contract version
- [ ] Update deployment JSON files
- [ ] Notify users of new addresses
- [ ] Monitor old contracts for exploitation
- [ ] Conduct security audit on new version
- [ ] Document vulnerability and fix

---

## Next Steps

- [Return to Database Overview](./DATABASE_OVERVIEW.md)
- [Integration Guide](./INTEGRATION.md)
- [Performance Tuning](./PERFORMANCE.md)
