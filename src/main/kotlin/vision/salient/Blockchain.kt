// src/main/kotlin/vision/salient/Blockchain.kt
package vision.salient

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Data class to hold deployment addresses loaded from JSON resources
 */
@Serializable
private data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

/**
 * Blockchain service object that handles all Web3 interactions for the Tic-Tac-Toe game.
 * Supports both local Hardhat network and Sepolia testnet.
 */
object Blockchain {
    // --- Configuration loading ---
    private val dotenv = dotenv { ignoreIfMissing = true }

    // --- Network configuration ---
    private var localFlag = dotenv["LOCAL"]?.toBoolean() ?: true
    val isLocal get() = localFlag

    private val ALCHEMY_API_KEY = dotenv["ALCHEMY_API_KEY"] ?: ""
    private val SEPOLIA_CHAIN_ID = dotenv["SEPOLIA_CHAIN_ID"]?.toLongOrNull() ?: 11155111L
    private val HARDHAT_CHAIN_ID = dotenv["HARDHAT_CHAIN_ID"]?.toLongOrNull() ?: 31337L
    private val HARDHAT_PROJECT_DIR = dotenv["HARDHAT_PROJECT_DIR"]
        ?: System.getProperty("user.home") + "/tic-tac-toe-smart-contract"

    private val RPC_URL: String
        get() = if (localFlag)
            dotenv["LOCAL_RPC_URL"] ?: "http://127.0.0.1:8545/"
        else
            dotenv["SEPOLIA_RPC_URL"] ?: "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"

    // --- Web3 client ---
    private var httpService = HttpService(RPC_URL)
    private var web3j = Web3j.build(httpService)

    // --- State management ---
    private var deploymentInfo: DeploymentAddresses? = loadDeploymentInfo().apply {
        println("[Blockchain] Loaded deployment info: $this")
    }
    private var currentGameAddress: String? = null

    /**
     * Switch between local Hardhat and Sepolia network
     */
    fun applyLocal(flag: Boolean) {
        localFlag = flag
        // Reset chain-specific state
        deploymentInfo = loadDeploymentInfo()
        currentGameAddress = null
        // Rebuild HTTP client for new network
        httpService = HttpService(RPC_URL)
        web3j = Web3j.build(httpService)
        println("[Blockchain] ➡️  Switched to ${if (localFlag) "LOCAL/Hardhat" else "SEPOLIA"}")
        printDerivedAddresses()
    }

    /**
     * Get player credentials based on current network
     */
    fun getPlayerCredentials(index: Int): Credentials =
        if (localFlag) {
            when (index) {
                0 -> Credentials.create(dotenv["PRIVATE_KEY_HARDHAT_0"] ?: error("Missing PRIVATE_KEY_HARDHAT_0 in .env"))
                else -> Credentials.create(dotenv["PRIVATE_KEY_HARDHAT_1"] ?: error("Missing PRIVATE_KEY_HARDHAT_1 in .env"))
            }
        } else {
            when (index) {
                0 -> Credentials.create(dotenv["PRIVATE_KEY_PLAYER1"] ?: error("Missing PRIVATE_KEY_PLAYER1 in .env"))
                else -> Credentials.create(dotenv["PRIVATE_KEY_PLAYER2"] ?: error("Missing PRIVATE_KEY_PLAYER2 in .env"))
            }
        }

    fun getPlayerCount(): Int = 2

    /**
     * Load deployment addresses from resources based on current network
     */
    private fun loadDeploymentInfo(): DeploymentAddresses? {
        val path = if (isLocal)
            "deployment_output_hardhat_local.json"
        else
            "deployment_output_sepolia_testnet.json"

        println("[Blockchain] Loading deployment info from: $path")
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: run {
            println("[Blockchain] Warning: Could not find $path in resources")
            return null
        }

        return try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<DeploymentAddresses>(stream.reader().readText())
        } catch (e: Exception) {
            println("[Blockchain] Error parsing deployment info: ${e.message}")
            null
        }
    }

    fun getFactoryAddress(): String? = deploymentInfo?.factoryAddress

    fun setCurrentGameAddress(addr: String?) {
        currentGameAddress = addr
        println("[Blockchain] Current game address set to: ${addr ?: "<none>"}")
    }

    fun getCurrentGameAddress(): String? = currentGameAddress

    /**
     * Print current configuration and addresses for debugging
     */
    fun printDerivedAddresses() {
        println("── address info ──")
        println("P1      : ${getPlayerCredentials(0).address}")
        println("P2      : ${getPlayerCredentials(1).address}")
        println("Factory : ${getFactoryAddress() ?: "<none>"}")
        println("Game    : ${currentGameAddress ?: "<none>"}")
        println("RPC     : $RPC_URL")
        println("Chain ID: ${if (localFlag) HARDHAT_CHAIN_ID else SEPOLIA_CHAIN_ID}")
        println("───────────────────")
    }

    /**
     * Calculate gas price with market-based pricing and optional bump for retries
     */
    private suspend fun calculateGasPrice(prev: BigInteger? = null): BigInteger = withContext(Dispatchers.IO) {
        val market = web3j.ethGasPrice().send().gasPrice
        val target = market.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(2))
        val bump = prev?.multiply(BigInteger.valueOf(11))?.divide(BigInteger.valueOf(10)) ?: BigInteger.ZERO
        target.max(bump)
    }

    /**
     * Run the TypeScript deploy script in the Solidity project directory
     * @return true on success
     */
    fun runDeploy(): Boolean {
        val flag = if (isLocal) "--local" else "--sepolia"
        val rawNpx = dotenv["NPX_PATH"]?.ifBlank { null } ?: "npx"
        var cmdList = listOf(rawNpx, "tsx", "deployment/deploy_ethers.ts", flag)
        var pb = ProcessBuilder(cmdList).directory(File(HARDHAT_PROJECT_DIR))

        fun run(pb: ProcessBuilder): Boolean {
            val p = pb.inheritIO().start()
            return p.waitFor() == 0
        }

        return try {
            println("[Blockchain] Running: ${cmdList.joinToString(" ")}")
            run(pb)
        } catch (e: java.io.IOException) {
            // Fallback: run through login shell so PATH gets initialized
            cmdList = listOf("bash", "-lc", cmdList.joinToString(" "))
            pb = ProcessBuilder(cmdList).directory(File(HARDHAT_PROJECT_DIR))
            println("[Blockchain] Fallback via shell: ${cmdList.joinToString(" ")}")
            run(pb)
        }
    }

    /**
     * Call a zero-argument contract function that returns a boolean
     */
    suspend fun readBool(fnName: String): Boolean = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game address set")
        val fn = Function(
            fnName,
            emptyList(),
            listOf(object : TypeReference<Bool>() {})
        )

        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                getPlayerCredentials(0).address,
                game,
                FunctionEncoder.encode(fn)
            ),
            DefaultBlockParameterName.LATEST
        ).send().result

        return@withContext try {
            val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
            when (val v = decoded.firstOrNull()?.value) {
                is Boolean -> v
                is Bool -> v.value
                else -> error("Unexpected bool payload: $v")
            }
        } catch (e: Exception) {
            println("[Blockchain] Error decoding bool for $fnName: ${e.message}")
            error("Cannot decode bool for $fnName from $raw")
        }
    }

    /**
     * Call a zero-argument contract function that returns an address
     * Returns the hex address in lowercase, or ZERO_ADDRESS on error
     */
    suspend fun readAddress(fnName: String): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game address set")
        val fn = Function(
            fnName,
            emptyList(),
            listOf(object : TypeReference<Address>() {})
        )

        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                getPlayerCredentials(0).address,
                game,
                FunctionEncoder.encode(fn)
            ),
            DefaultBlockParameterName.LATEST
        ).send().result

        return@withContext try {
            val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
            when (val v = decoded.firstOrNull()?.value) {
                is Address -> v.value.lowercase()
                is String -> v.lowercase()
                else -> ZERO_ADDRESS
            }
        } catch (e: Exception) {
            println("[Blockchain] Error decoding address for $fnName: ${e.message}")
            ZERO_ADDRESS
        }
    }

    /**
     * Send a transaction to the blockchain with automatic gas pricing and retry logic
     */
    private suspend fun sendTransaction(
        from: Credentials,
        to: String,
        data: String,
        value: BigInteger = BigInteger.ZERO,
        prevGas: BigInteger? = null,
        retry: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val nonce = web3j
            .ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount

        val gasPrice = calculateGasPrice(prevGas)
        val gasLimit = web3j
            .ethEstimateGas(Transaction.createEthCallTransaction(from.address, to, data))
            .send().amountUsed
            .multiply(BigInteger.valueOf(150))
            .divide(BigInteger.valueOf(100))

        val raw = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data)
        val chainId = if (localFlag) HARDHAT_CHAIN_ID else SEPOLIA_CHAIN_ID
        val signed = TransactionEncoder.signMessage(raw, chainId, from)
        val txHex = Numeric.toHexString(signed)

        val resp = web3j.ethSendRawTransaction(txHex).send()
        if (resp.hasError()) {
            val msg = resp.error.message
            if (retry == 0 && msg.contains("underpriced", true)) {
                println("[Blockchain] Transaction underpriced, retrying with higher gas...")
                return@withContext sendTransaction(from, to, data, value, gasPrice, 1)
            }
            error("Transaction error: $msg")
        }
        resp.transactionHash
    }

    /**
     * Wait for a transaction receipt with timeout
     * @return TransactionReceipt if found within 120 attempts (4 minutes), null otherwise
     */
    private suspend fun waitForReceipt(txHash: String): TransactionReceipt? = withContext(Dispatchers.IO) {
        repeat(120) { i ->
            if (web3j.ethGetTransactionByHash(txHash).send().transaction.isPresent) {
                println("[Blockchain] 📦 Transaction in mempool... attempt ${i + 1}")
            }
            val recOpt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (recOpt.isPresent) {
                println("[Blockchain] ✅ Transaction confirmed")
                return@withContext recOpt.get()
            }
            kotlinx.coroutines.delay(2000)
        }
        println("[Blockchain] ⚠️  Transaction receipt timeout")
        null
    }

    /**
     * Create a new game via the factory contract
     * @param idx Player index (0 or 1)
     * @return Game address if successful, null otherwise
     */
    suspend fun createGameByPlayer(idx: Int = 0): String? = withContext(Dispatchers.IO) {
        val factory = getFactoryAddress() ?: error("No factory address available")
        val fn = Function(
            "createGame",
            emptyList(),
            listOf(TypeReference.create(Address::class.java))
        )
        val data = FunctionEncoder.encode(fn)

        val hash = sendTransaction(getPlayerCredentials(idx), factory, data)
        println("[Blockchain] ✔ Transaction sent: $hash")

        val rec = waitForReceipt(hash) ?: error("No receipt received for game creation")
        val evt = Event("GameCreated", listOf(TypeReference.create(Address::class.java)))
        val sig = EventEncoder.encode(evt)

        rec.logs.forEach { log ->
            if (log.topics.firstOrNull() == sig) {
                val addrHex = log.topics[1].removePrefix("0x").takeLast(40)
                val newAddr = "0x$addrHex"
                currentGameAddress = newAddr
                println("[Blockchain] 🎮 New game created at: $newAddr")
                return@withContext newAddr
            }
        }
        println("[Blockchain] ⚠️  Could not find GameCreated event in receipt")
        null
    }

    /**
     * Make a move in the current game
     * @param idx Player index (0 or 1)
     * @param row Row index (0-2)
     * @param col Column index (0-2)
     * @return Transaction hash
     */
    suspend fun makeMove(idx: Int, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game address set")
        val fn = Function(
            "makeMove",
            listOf(Uint8(row.toLong()), Uint8(col.toLong())),
            emptyList()
        )
        val data = FunctionEncoder.encode(fn)
        val hash = sendTransaction(getPlayerCredentials(idx), game, data)
        println("[Blockchain] 🎯 Move submitted: ($row, $col) - tx: $hash")
        waitForReceipt(hash) ?: error("No receipt received for move")
        hash
    }

    /**
     * Get the current board state from the contract
     * Tries dynamic array decoding first, falls back to static array decoding
     * @return 3x3 grid of addresses (lowercase hex strings)
     */
    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game address set")

        val dynFn = Function(
            "getBoardState",
            emptyList(),
            listOf(object : TypeReference<DynamicArray<DynamicArray<Address>>>() {})
        )
        val callData = FunctionEncoder.encode(dynFn)
        val raw = web3j
            .ethCall(
                Transaction.createEthCallTransaction(
                    getPlayerCredentials(0).address,
                    game,
                    callData
                ),
                DefaultBlockParameterName.LATEST
            )
            .send()
            .result

        println("[Blockchain] Board state RPC response: $raw")

        try {
            // Try dynamic array decoding
            val decoded = FunctionReturnDecoder.decode(raw, dynFn.outputParameters)
            @Suppress("UNCHECKED_CAST")
            val outer = decoded[0].value as List<*>
            return@withContext outer.map { row ->
                @Suppress("UNCHECKED_CAST")
                (row as List<*>).map { cell ->
                    when (cell) {
                        is Address -> cell.value.lowercase()
                        is String -> cell.lowercase()
                        else -> error("Unexpected board cell type: ${cell?.javaClass}")
                    }
                }
            }
        } catch (decodeErr: Exception) {
            // Fallback to static array decoding
            println("[Blockchain] Dynamic decode failed, using static 3×3 fallback: ${decodeErr.message}")
            if (raw == "0x") error("Contract returned empty data")

            // Parse static array: 9 words of 64 hex chars each, last 40 chars = address
            val flat = (0 until 9).map { i ->
                val word = raw.drop(2).substring(i * 64, i * 64 + 64)
                "0x" + word.takeLast(40)
            }
            return@withContext listOf(
                flat.subList(0, 3),
                flat.subList(3, 6),
                flat.subList(6, 9)
            ).map { row -> row.map(String::lowercase) }
        }
    }

    /**
     * Generate a deterministic emoji for an Ethereum address
     * Used to visually represent players on the board
     */
    fun emojiForAddress(addr: String): String {
        val emojis = listOf(
            "😀", "🐶", "🌟", "🍕", "🚀", "🐍", "🎮", "📚", "🎵", "🌈",
            "🍔", "🧠", "🦄", "💎", "🕹️", "🧊", "⚡", "💡", "🧩", "🎯"
        )
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(addr.lowercase().removePrefix("0x").toByteArray())
        return emojis[(hash[0].toInt() and 0xFF) % emojis.size]
    }
}
