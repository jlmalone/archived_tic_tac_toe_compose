package vision.salient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import io.github.cdimascio.dotenv.dotenv
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint8
import java.io.File
import java.math.BigInteger

/**
 * deployment_output.json holder
 */
@Serializable
data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

/**
 * Thin helper around web3j that:
 *  • picks local vs sepolia via .env (LOCAL=true/false)
 *  • provides noisy printlns for quick debugging
 *  • does a **manual ABI decode** of the address[3][3] return value so we don’t hit the
 *    reflection issues in web3j’s generic `StaticArrayX` handling.
 */
object Blockchain {

    // ───────────────────────── env / setup ─────────────────────────
    private val env           = dotenv { ignoreIfMissing = true }
    val    LOCAL              = env["LOCAL"].toBooleanStrictOrNull() ?: true
    private val ALCHEMY_KEY   = env["SEPOLIA_ALCHEMY_API_KEY"] ?: ""
    private val RPC_URL       = if (LOCAL) "http://127.0.0.1:8545/" else "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_KEY"
    private val CHAIN_ID      = if (LOCAL) 31337L else 11155111L

    private val web3j: Web3j  by lazy { Web3j.build(HttpService(RPC_URL)) }

    val credentialsPlayer1: Credentials by lazy {
        Credentials.create(if (LOCAL) env["PRIVATE_KEY_HARDHAT_0"] else env["PRIVATE_KEY_PLAYER1"])
    }
    val credentialsPlayer2: Credentials by lazy {
        Credentials.create(if (LOCAL) env["PRIVATE_KEY_HARDHAT_1"] else env["PRIVATE_KEY_PLAYER2"])
    }

    // ───────────────────────── deployment file (hot‑reloadable) ─────────────────────────
    @Volatile private var deploymentInfo: DeploymentAddresses? = null
    fun reloadDeployment(): DeploymentAddresses? {
        val res = javaClass.classLoader.getResource("deployment_output.json") ?: return null
        deploymentInfo = Json { ignoreUnknownKeys = true }.decodeFromString(res.readText())
        println("[Blockchain] re‑read deployment_output.json → $deploymentInfo")
        return deploymentInfo
    }
    fun getFactoryAddress() = (deploymentInfo ?: reloadDeployment())?.factoryAddress

    // ───────────────────────── misc state ─────────────────────────
    private var currentGameAddress: String? = null
    fun setCurrentGameAddress(addr: String?) { currentGameAddress = addr }

    // ───────────────────────── pretty debug print ─────────────────────────
    fun printDerivedAddresses() {
        println("\n── address info ──")
        println("P1     : ${credentialsPlayer1.address}")
        println("P2     : ${credentialsPlayer2.address}")
        println("Factory: ${getFactoryAddress()}")
        println("Game   : ${currentGameAddress ?: "<none>"}")
        println("RPC    : $RPC_URL (chainId=$CHAIN_ID)")
        println("──────────────")
    }

    // ───────────────────────── tx helpers ─────────────────────────
    private suspend fun calculateGasPrice(prev: BigInteger? = null): BigInteger = withContext(Dispatchers.IO) {
        val market = web3j.ethGasPrice().send().gasPrice
        val target = market * BigInteger.valueOf(3) / BigInteger.valueOf(2)       // 1.5×
        val bump   = prev?.multiply(BigInteger.valueOf(11))?.divide(BigInteger.valueOf(10)) ?: BigInteger.ZERO // +10 %
        target.max(bump)
    }

    private suspend fun sendTransaction(
        from: Credentials,
        to: String,
        data: String,
        value: BigInteger = BigInteger.ZERO,
        prevGas: BigInteger? = null,
        retry: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val nonce    = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING).send().transactionCount
        val gasPrice = calculateGasPrice(prevGas)
        val gasLimit = try {
            val est = web3j.ethEstimateGas(Transaction.createEthCallTransaction(from.address, to, data)).send().amountUsed
            est * BigInteger.valueOf(150) / BigInteger.valueOf(100)          // +50 % safety margin
        } catch (e: Exception) {
            println("[Blockchain] ⚠ estimateGas failed (${e.message}); using 300 000")
            BigInteger.valueOf(300_000)
        }
        val raw      = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data)
        val signed   = TransactionEncoder.signMessage(raw, CHAIN_ID, from)
        val resp     = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send()
        if (resp.hasError()) {
            val msg = resp.error.message
            if (retry == 0 && msg.contains("underpriced", true))
                return@withContext sendTransaction(from, to, data, value, gasPrice, 1)
            error("TX Error: $msg")
        }
        println("[Blockchain] ✔ tx ${resp.transactionHash} (gas=$gasLimit)")
        resp.transactionHash
    }

    private suspend fun waitForReceipt(hash: String): TransactionReceipt? = withContext(Dispatchers.IO) {
        repeat(60) { i ->
            val recOpt = web3j.ethGetTransactionReceipt(hash).send().transactionReceipt
            if (recOpt.isPresent) return@withContext recOpt.get()
            kotlinx.coroutines.delay(1200)
        }
        null
    }

    // ───────────────────────── game operations ─────────────────────────
    suspend fun createGameByPlayer1(): String? = withContext(Dispatchers.IO) {
        val factory = getFactoryAddress() ?: error("Factory not loaded")
        val data = FunctionEncoder.encode(org.web3j.abi.datatypes.Function(
            "createGame",
            emptyList(),
            listOf(object : TypeReference<Address>() {})
        ))
        val tx = sendTransaction(credentialsPlayer1, factory, data)
        val rec = waitForReceipt(tx) ?: error("No receipt")
        val sig = EventEncoder.encode(org.web3j.abi.datatypes.Event("GameCreated", listOf(object : TypeReference<Address>() {})))
        rec.logs.firstOrNull { it.topics.first() == sig }?.let { log ->
            val addr = "0x" + log.topics[1].removePrefix("0x").takeLast(40)
            currentGameAddress = addr
            println("[Blockchain] 🎲 new game $addr")
            return@withContext addr
        }
        null
    }

    suspend fun makeMove(player: Credentials, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game joined")
        val fn   = org.web3j.abi.datatypes.Function("makeMove", listOf(Uint8(row.toLong()), Uint8(col.toLong())), emptyList())
        val tx   = sendTransaction(player, game, FunctionEncoder.encode(fn))
        waitForReceipt(tx) ?: error("No receipt for move")
        tx
    }

    /**
     * Fetch board state (address[3][3]) and decode manually to avoid web3j reflection issues.
     * Returns a 3×3 List of address strings (all lowercase).
     */
    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game joined")

        // encode the call (no outputs → we get raw hex back)
        val encoded = FunctionEncoder.encode(org.web3j.abi.datatypes.Function("getBoardState", emptyList(), emptyList()))
        val raw     = web3j.ethCall(
            Transaction.createEthCallTransaction(credentialsPlayer1.address, game, encoded),
            DefaultBlockParameterName.LATEST
        ).send().result
        println("[Blockchain] raw board hex = $raw")

        val clean = raw.removePrefix("0x")
        if (clean.length < 64 * 9) error("getBoardState returned only ${clean.length} hex chars (expected >= 576)")

        // split into 9 words → last 40 hex chars of each = address
        val flattened = (0 until 9).map { i ->
            "0x" + clean.substring(i * 64, (i + 1) * 64).takeLast(40)
        }
        val matrix = flattened.chunked(3).map { row -> row.map { it.lowercase() } }
        println("[Blockchain] decoded board = $matrix")
        matrix
    }

    // ───────────────────────── convenience: run local deploy script ─────────────────────────
    fun runLocalDeploy(repoDir: File): Boolean {
        println("[Blockchain] 🚀 launching deploy script…")
        val pb = ProcessBuilder("npx", "tsx", "deployment/deploy_ethers.ts")
            .directory(repoDir)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
        return pb.start().let { it.waitFor() == 0 }
    }
}