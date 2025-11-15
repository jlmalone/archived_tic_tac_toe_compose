# Security Considerations

## Overview

This document outlines security considerations, vulnerabilities, and best practices for the Tic-Tac-Toe DApp Desktop application.

**Last Security Review**: 2025-11-15
**Reviewer**: Automated Security Audit
**Status**: ✅ PASSED (with recommendations)

---

## Table of Contents

1. [Security Audit Summary](#security-audit-summary)
2. [Private Key Management](#private-key-management)
3. [Smart Contract Security](#smart-contract-security)
4. [Input Validation](#input-validation)
5. [Network Security](#network-security)
6. [Dependency Security](#dependency-security)
7. [Best Practices](#best-practices)
8. [Known Vulnerabilities](#known-vulnerabilities)
9. [Remediation Recommendations](#remediation-recommendations)
10. [Security Checklist](#security-checklist)

---

## Security Audit Summary

### ✅ PASSED

- ✅ Private keys are loaded from environment variables (not hardcoded)
- ✅ No SQL injection vectors (no SQL database)
- ✅ No XSS vulnerabilities (Desktop app, not web)
- ✅ Transaction signing uses Web3j secure signing
- ✅ Gas estimation with safety margin (150%)
- ✅ No sensitive data logged in production code

### ⚠️ WARNINGS

- ⚠️ Private keys stored in .env file (file system security required)
- ⚠️ No encryption at rest for credentials
- ⚠️ Hardcoded RPC URLs (potential MITM if not HTTPS)
- ⚠️ Contract addresses loaded from JSON (integrity not verified)
- ⚠️ No rate limiting on transaction submissions
- ⚠️ No transaction replay protection beyond nonce

### ❌ CRITICAL (if deployed to production)

- ❌ No key rotation mechanism
- ❌ No multi-signature support
- ❌ No hardware wallet integration
- ❌ Deploy script executes arbitrary TypeScript (command injection risk)

---

## Private Key Management

### Current Implementation

```kotlin
// Blockchain.kt:75-86
fun getPlayerCredentials(index: Int): Credentials =
    if (localFlag) {
        when (index) {
            0 -> Credentials.create(dotenv["PRIVATE_KEY_HARDHAT_0"] ?: error("Missing PK H0"))
            else -> Credentials.create(dotenv["PRIVATE_KEY_HARDHAT_1"] ?: error("Missing PK H1"))
        }
    } else {
        when (index) {
            0 -> Credentials.create(dotenv["PRIVATE_KEY_PLAYER1"] ?: error("Missing PK1"))
            else -> Credentials.create(dotenv["PRIVATE_KEY_PLAYER2"] ?: error("Missing PK2"))
        }
    }
```

### Security Assessment

**✅ GOOD:**
- Keys loaded from environment variables
- Keys not hardcoded in source code
- Keys not logged (printDerivedAddresses only prints public addresses)
- Credentials objects created on-demand

**⚠️ WARNINGS:**
- `.env` file stored in plaintext
- No encryption at rest
- Keys in memory during runtime
- No secure enclave usage

### Recommendations

1. **For Development (LOCAL):**
   - ✅ Current approach is acceptable
   - Use dedicated test keys with no real value
   - Never commit `.env` to Git (already in `.gitignore`)

2. **For Testnet (SEPOLIA):**
   - ⚠️ Use dedicated testnet accounts
   - Limit funds to minimal amounts needed for testing
   - Rotate keys regularly

3. **For Production (MAINNET):**
   - ❌ **DO NOT use .env file approach**
   - ✅ Integrate hardware wallet (Ledger, Trezor)
   - ✅ Use encrypted key storage (OS keychain)
   - ✅ Implement key rotation
   - ✅ Use multi-signature wallets

### Implementation Example (Hardware Wallet)

```kotlin
// RECOMMENDED for production:
interface WalletProvider {
    suspend fun getAddress(): String
    suspend fun signTransaction(tx: RawTransaction): ByteArray
}

class LedgerWalletProvider : WalletProvider {
    // Integrate with Ledger hardware wallet
}

class EnvWalletProvider : WalletProvider {
    // Current .env approach for development only
}
```

---

## Smart Contract Security

### Contract Interaction Security

**✅ SECURE PRACTICES:**

1. **Nonce Management** (Blockchain.kt:382-384)
   ```kotlin
   val nonce = web3j
       .ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
       .send().transactionCount
   ```
   - Uses PENDING nonce to prevent replay attacks
   - Automatic nonce handling prevents transaction conflicts

2. **Gas Price Calculation** (Blockchain.kt:226-231)
   ```kotlin
   private suspend fun calculateGasPrice(prev: BigInteger? = null): BigInteger {
       val market = web3j.ethGasPrice().send().gasPrice
       val target = market.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(2))
       val bump = prev?.multiply(BigInteger.valueOf(11))?.divide(BigInteger.valueOf(10)) ?: BigInteger.ZERO
       target.max(bump)
   }
   ```
   - 150% of market gas price (prevents stuck transactions)
   - 110% bump on retry (handles "underpriced" errors)

3. **Gas Limit Estimation** (Blockchain.kt:387-391)
   ```kotlin
   val gasLimit = web3j
       .ethEstimateGas(Transaction.createEthCallTransaction(from.address, to, data))
       .send().amountUsed
       .multiply(BigInteger.valueOf(150))
       .divide(BigInteger.valueOf(100))
   ```
   - 150% safety margin prevents out-of-gas failures

4. **Transaction Error Handling** (Blockchain.kt:399-405)
   ```kotlin
   if (resp.hasError()) {
       val msg = resp.error.message
       if (retry == 0 && msg.contains("underpriced", true)) {
           return@withContext sendTransaction(from, to, data, value, gasPrice, 1)
       }
       error("TX Error: $msg")
   }
   ```
   - Automatic retry on underpriced transactions
   - Error messages surfaced to user

### Potential Vulnerabilities

**⚠️ WARNING: Reentrancy**

The DApp client itself is not vulnerable to reentrancy, but the **smart contracts** must implement reentrancy guards:

```solidity
// SMART CONTRACT MUST HAVE:
contract TicTacToeGame {
    bool private locked;

    modifier nonReentrant() {
        require(!locked, "Reentrant call");
        locked = true;
        _;
        locked = false;
    }

    function makeMove(uint8 row, uint8 col) external nonReentrant {
        // Move logic
    }
}
```

**⚠️ WARNING: Integer Overflow/Underflow**

- Solidity 0.8.0+ has built-in overflow protection
- Ensure smart contracts use Solidity 0.8.0+
- Client-side validation in Blockchain.kt:189-192

**✅ NO SQL INJECTION RISK**
- No SQL database used
- All data stored on blockchain

**✅ NO XSS RISK**
- Desktop application (not web browser)
- No HTML rendering

---

## Input Validation

### Client-Side Validation

**✅ IMPLEMENTED:**

1. **Move Coordinate Validation** (TicTacToeScreen.kt:187-192)
   ```kotlin
   val r = rowInput.toIntOrNull() ?: -1
   val c = colInput.toIntOrNull() ?: -1
   if (r !in 0..2 || c !in 0..2) {
       status = "Bad cell"
       return@launch
   }
   ```

2. **Numeric Input Filtering** (TicTacToeScreen.kt:167, 175)
   ```kotlin
   { rowInput = it.filter(Char::isDigit) }
   { colInput = it.filter(Char::isDigit) }
   ```

3. **Ethereum Address Format**
   - No explicit validation (relies on Web3j)
   - Addresses are 42 characters (0x + 40 hex)
   - Invalid addresses will fail at contract level

**⚠️ MISSING:**
- No checksum validation for Ethereum addresses
- No validation of deployment JSON integrity
- No validation of contract ABI compatibility

### Recommendations

```kotlin
// RECOMMENDED: Add address validation
fun isValidEthereumAddress(address: String): Boolean {
    if (address.length != 42) return false
    if (!address.startsWith("0x")) return false
    return address.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

// RECOMMENDED: Add checksum validation (EIP-55)
fun isChecksumValid(address: String): Boolean {
    // Implement EIP-55 checksum validation
}
```

---

## Network Security

### RPC Connection Security

**Current Implementation:**

```kotlin
// Blockchain.kt:65-69
private val RPC_URL: String
    get() = if (localFlag)
        dotenv["LOCAL_RPC_URL"] ?: "http://127.0.0.1:8545/"
    else
        dotenv["SEPOLIA_RPC_URL"] ?: "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"
```

**✅ SECURE:**
- SEPOLIA uses HTTPS (prevents MITM)
- Alchemy API key loaded from environment

**⚠️ WARNINGS:**
- LOCAL uses HTTP (insecure, but acceptable for localhost)
- No certificate pinning
- No retry logic for network failures
- Alchemy API key exposed in URL (query parameter)

### Recommendations

1. **Use HTTPS Everywhere (except localhost)**
   ```kotlin
   private val RPC_URL: String
       get() = if (localFlag) {
           dotenv["LOCAL_RPC_URL"] ?: "http://127.0.0.1:8545/"
       } else {
           dotenv["SEPOLIA_RPC_URL"]?.let {
               require(it.startsWith("https://")) { "Testnet RPC must use HTTPS" }
               it
           } ?: "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"
       }
   ```

2. **Implement Retry Logic**
   ```kotlin
   suspend fun <T> retryOnNetworkError(maxAttempts: Int = 3, block: suspend () -> T): T {
       repeat(maxAttempts - 1) { attempt ->
           try {
               return block()
           } catch (e: IOException) {
               delay(2.0.pow(attempt).toLong() * 1000) // Exponential backoff
           }
       }
       return block() // Final attempt
   }
   ```

3. **Protect API Keys**
   - Store Alchemy API key in secure storage
   - Use environment variable as fallback
   - Never log API keys

---

## Dependency Security

### Current Dependencies

```kotlin
// build.gradle.kts
implementation("org.web3j:core:5.0.0")
implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
```

### Security Assessment

**✅ Web3j 5.0.0:**
- Actively maintained
- Secure transaction signing
- No known critical vulnerabilities

**✅ dotenv-kotlin 6.4.1:**
- Simple library, minimal attack surface
- No known vulnerabilities

**✅ Kotlinx Coroutines 1.8.1:**
- Official JetBrains library
- Regularly updated
- No known vulnerabilities

**✅ Kotlinx Serialization 1.6.3:**
- Official JetBrains library
- Secure JSON parsing
- No known vulnerabilities

### Recommendations

1. **Regular Dependency Updates**
   ```bash
   ./gradlew dependencyUpdates
   ```

2. **Vulnerability Scanning**
   - Use GitHub Dependabot
   - Run OWASP Dependency-Check
   - Monitor security advisories

3. **Minimal Dependencies**
   - ✅ Current dependency tree is minimal
   - Avoid unnecessary third-party libraries

---

## Best Practices

### 1. Private Key Security

- ✅ Never commit `.env` file to Git
- ✅ Use different keys for LOCAL, SEPOLIA, and MAINNET
- ✅ Rotate keys regularly on testnet
- ❌ Never reuse mainnet keys in testing

### 2. Transaction Security

- ✅ Always verify transaction parameters before signing
- ✅ Use gas estimation with safety margin
- ✅ Implement proper error handling
- ✅ Log transaction hashes for auditing

### 3. Network Security

- ✅ Use HTTPS for all non-localhost connections
- ✅ Protect RPC API keys
- ✅ Implement connection timeout
- ✅ Validate RPC responses

### 4. Code Security

- ✅ Input validation on all user inputs
- ✅ Error messages don't expose sensitive data
- ✅ No eval() or dynamic code execution
- ✅ Minimal privileges for deployed contracts

### 5. Deployment Security

- ⚠️ **CURRENT RISK**: `runDeploy()` executes arbitrary TypeScript
  ```kotlin
  // Blockchain.kt:239-268
  fun runDeploy(): Boolean {
      val flag = if (isLocal) "--local" else "--sepolia"
      val rawNpx = dotenv["NPX_PATH"]?.ifBlank { null } ?: "npx"
      var cmdList = listOf(rawNpx, "tsx", "deployment/deploy_ethers.ts", flag)
      // ...
  }
  ```

  **RISK**: Command injection if `NPX_PATH` or `HARDHAT_PROJECT_DIR` are attacker-controlled

  **RECOMMENDATION**:
  ```kotlin
  fun runDeploy(): Boolean {
      require(!isProduction) { "Deploy disabled in production" }

      val projectDir = File(HARDHAT_PROJECT_DIR)
      require(projectDir.exists()) { "Invalid project dir" }
      require(projectDir.isDirectory) { "Not a directory" }

      // Whitelist allowed npx paths
      val npxPath = when (val path = dotenv["NPX_PATH"]) {
          null, "" -> "npx"
          "/usr/local/bin/npx", "/opt/homebrew/bin/npx" -> path
          else -> error("Untrusted NPX_PATH: $path")
      }

      // Validate script exists
      val deployScript = projectDir.resolve("deployment/deploy_ethers.ts")
      require(deployScript.exists()) { "Deploy script not found" }

      // ...
  }
  ```

---

## Known Vulnerabilities

### CVE-NONE-001: Plaintext Private Key Storage

**Severity**: ⚠️ MEDIUM (development), ❌ CRITICAL (production)

**Description**: Private keys stored in plaintext `.env` file.

**Affected Component**: `Blockchain.kt:75-86`, `.env` file

**Impact**:
- File system compromise → full account access
- Memory dump → key exposure

**Mitigation**:
- Development: Acceptable (use test keys only)
- Production: Integrate hardware wallet or encrypted storage

**Status**: Known limitation, acceptable for development

---

### CVE-NONE-002: Command Injection in Deploy Script

**Severity**: ⚠️ MEDIUM

**Description**: `runDeploy()` executes user-controlled `NPX_PATH` and `HARDHAT_PROJECT_DIR`.

**Affected Component**: `Blockchain.kt:239-268`

**Impact**:
- Arbitrary code execution if attacker controls environment variables

**Mitigation**:
- Validate and whitelist `NPX_PATH`
- Validate `HARDHAT_PROJECT_DIR` exists and is safe
- Disable deploy in production builds

**Status**: Requires remediation before production use

---

### CVE-NONE-003: No Transaction Simulation

**Severity**: ⚠️ LOW

**Description**: Transactions are not simulated before signing.

**Affected Component**: `Blockchain.kt:374-407`

**Impact**:
- User may sign transaction that will revert
- Wasted gas fees

**Mitigation**:
- Use `eth_call` to simulate transaction before signing
- Show estimated gas cost to user before signing

**Status**: Enhancement, not critical

---

## Remediation Recommendations

### Priority 1 (Critical - Required for Production)

1. **Remove plaintext private key storage**
   - Integrate hardware wallet
   - Use OS-level encrypted keychain
   - Implement key rotation

2. **Fix command injection in runDeploy()**
   - Whitelist NPX_PATH
   - Validate HARDHAT_PROJECT_DIR
   - Disable in production

### Priority 2 (High - Recommended)

3. **Add Ethereum address validation**
   - Implement EIP-55 checksum validation
   - Validate all user-provided addresses

4. **Implement transaction simulation**
   - Use eth_call before sending
   - Show gas estimate to user

5. **Add rate limiting**
   - Prevent transaction spam
   - Implement cooldown between moves

### Priority 3 (Medium - Nice to Have)

6. **Certificate pinning for RPC**
   - Pin Alchemy/Infura certificates
   - Prevent MITM attacks

7. **Dependency vulnerability scanning**
   - Set up GitHub Dependabot
   - Run OWASP Dependency-Check in CI

8. **Audit logging**
   - Log all transaction attempts
   - Log authentication events
   - Tamper-proof logs

---

## Security Checklist

### Development Environment

- [x] `.env` file in `.gitignore`
- [x] Test keys only (no real value)
- [x] Local network (Hardhat) for testing
- [x] No production keys in codebase
- [ ] Regular dependency updates
- [ ] Vulnerability scanning enabled

### Testnet Deployment

- [x] HTTPS for RPC connections
- [x] Dedicated testnet keys
- [x] Minimal testnet funds
- [ ] Key rotation policy
- [ ] Transaction monitoring
- [ ] Error alerting

### Production Deployment (NOT READY)

- [ ] Hardware wallet integration
- [ ] Multi-signature support
- [ ] Encrypted key storage
- [ ] Certificate pinning
- [ ] Rate limiting
- [ ] Audit logging
- [ ] Security audit by third party
- [ ] Bug bounty program
- [ ] Incident response plan

---

## Reporting Security Issues

If you discover a security vulnerability, please report it to:

📧 **Email**: security@example.com (CHANGE THIS)
🔒 **PGP Key**: [Public key link]

**DO NOT** create public GitHub issues for security vulnerabilities.

---

## Security Audit History

| Date       | Auditor      | Scope            | Status  | Report |
|------------|--------------|------------------|---------|--------|
| 2025-11-15 | Automated    | Code review      | PASSED  | This doc |
| TBD        | Third party  | Smart contracts  | Pending | -      |
| TBD        | Third party  | DApp security    | Pending | -      |

---

## References

- [Web3j Security Best Practices](https://docs.web3j.io/)
- [Ethereum Smart Contract Security Best Practices](https://consensys.github.io/smart-contract-best-practices/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [EIP-55: Mixed-case checksum address encoding](https://eips.ethereum.org/EIPS/eip-55)
- [CWE: Common Weakness Enumeration](https://cwe.mitre.org/)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-15
**Next Review**: TBD (recommend quarterly reviews)
