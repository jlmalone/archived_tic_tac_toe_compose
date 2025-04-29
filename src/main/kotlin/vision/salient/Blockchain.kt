package vision.salient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint8
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
import java.math.BigInteger

@Serializable
private data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

object Blockchain {
    // --- Configuration ---
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val ALCHEMY_API_KEY = dotenv["SEPOLIA_ALCHEMY_API_KEY"]
        ?: throw IllegalStateException("SEPOLIA_API_KEY missing")
    val LOCAL = dotenv["LOCAL"]?.toBoolean() ?: true
    private val RPC_URL =
        if (LOCAL) "http://127.0.0.1:8545/" else "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"


    //todo env
    val SEPOLIA_CHAIN_ID : Long=  dotenv["SEPOLIA_CHAIN_ID"]?.toLongOrNull()?: 11155111L
    val HARDHAT_CHAIN_ID =  dotenv["HARDHAT_CHAIN_ID"]?.toLongOrNull()?: 31337L

//    private val RPC_URL =
//    private const val SEPOLIA_CHAIN_ID = 11155111L

    // --- Web3j & Credentials ---
    private val web3j: Web3j by lazy { Web3j.build(HttpService(RPC_URL)) }
    val credentialsPlayer1: Credentials by lazy {
        Credentials.create(
            if (LOCAL) dotenv["PRIVATE_KEY_HARDHAT_0"] else dotenv["PRIVATE_KEY_PLAYER1"] ?: error("Missing PK1")
        )
    }
    val credentialsPlayer2: Credentials by lazy {
        Credentials.create(
            if (LOCAL) dotenv["PRIVATE_KEY_HARDHAT_1"] else dotenv["PRIVATE_KEY_PLAYER2"] ?: error("Missing PK2")
        )
    }

    // --- Deployment Info ---
    private val deploymentInfo: DeploymentAddresses? by lazy { loadDeploymentInfo() }
    fun getFactoryAddress(): String? = deploymentInfo?.factoryAddress

    private fun loadDeploymentInfo(): DeploymentAddresses? {
        val stream = javaClass.classLoader.getResourceAsStream("deployment_output.json") ?: return null
        return Json { ignoreUnknownKeys = true }.decodeFromString(stream.reader().readText())
    }

    // --- State ---
    private var currentGameAddress: String? = null
    fun setCurrentGameAddress(addr: String?) {
        currentGameAddress = addr
    }

    fun getCurrentGameAddress(): String? = currentGameAddress

    // --- Logging ---
    fun printDerivedAddresses() {
        println("--- Address Info ---")
        println("Player1: ${credentialsPlayer1.address}")
        println("Player2: ${credentialsPlayer2.address}")
        println("Factory: ${getFactoryAddress() ?: "<none>"}")
        println("Game: ${currentGameAddress ?: "<none>"}")
        println("RPC: $RPC_URL")
        println("---------------------")
    }

    // --- Gas price calc (1.5× market, bump 10% on retry) ---
    suspend fun calculateGasPrice(
        previous: BigInteger? = null
    ): BigInteger = withContext(Dispatchers.IO) {
        val market = web3j.ethGasPrice().send().gasPrice
        val target = market.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(2))
        val bump = previous?.multiply(BigInteger.valueOf(11))?.divide(BigInteger.valueOf(10)) ?: BigInteger.ZERO
        target.max(bump)
    }

    // --- Send & wait ---
    suspend fun sendTransaction(
        from: Credentials,
        to: String,
        data: String,
        value: BigInteger = BigInteger.ZERO,
        prevGas: BigInteger? = null,
        retry: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val nonce = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount

        val gasPrice = calculateGasPrice(prevGas)
        val gasLimit = web3j.ethEstimateGas(Transaction.createEthCallTransaction(from.address, to, data))
            .send().amountUsed.multiply(BigInteger.valueOf(150)).divide(BigInteger.valueOf(100))

        val raw = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data)


// …

        val usedChainId = if (LOCAL) HARDHAT_CHAIN_ID else SEPOLIA_CHAIN_ID


        val signed = TransactionEncoder.signMessage(raw, usedChainId, from)
        val txHex = Numeric.toHexString(signed)

        val resp = web3j.ethSendRawTransaction(txHex).send()
        if (resp.hasError()) {
            val msg = resp.error.message
            if (retry == 0 && msg.contains("underpriced", ignoreCase = true)) {
                return@withContext sendTransaction(from, to, data, value, gasPrice, 1)
            }
            error("TX Error: $msg")
        }
        resp.transactionHash
    }

    /** Polls receipt up to 120×2s intervals */
    suspend fun waitForReceipt(txHash: String): TransactionReceipt? = withContext(Dispatchers.IO) {
        repeat(120) { i ->
            val byHash = web3j.ethGetTransactionByHash(txHash).send()
            if (!byHash.transaction.isEmpty) println("📦 in mempool... attempt ${i + 1}")

            val recOpt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (recOpt.isPresent) return@withContext recOpt.get()
            kotlinx.coroutines.delay(2000)
        }
        null
    }

    // --- Core flows ---
    suspend fun createGameByPlayer1(): String? = withContext(Dispatchers.IO) {
        val factory = getFactoryAddress() ?: error("No factory")
        val fn = Function("createGame", emptyList(), listOf(TypeReference.create(Address::class.java)))
        val data = FunctionEncoder.encode(fn)

        val hash = sendTransaction(credentialsPlayer1, factory, data)
        val rec = waitForReceipt(hash) ?: error("No receipt")

        // parse event
        val evt = Event("GameCreated", listOf(TypeReference.create(Address::class.java)))
        val sig = EventEncoder.encode(evt)
        rec.logs.forEach { log ->
            if (log.topics.firstOrNull() == sig) {
                val addrHex = log.topics[1].removePrefix("0x").takeLast(40)
                val newAddr = "0x$addrHex"
                setCurrentGameAddress(newAddr)
                return@withContext newAddr
            }
        }
        null
    }

    suspend fun makeMove(player: Credentials, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")
        val fn = Function("makeMove", listOf(Uint8(row.toLong()), Uint8(col.toLong())), emptyList())
        val data = FunctionEncoder.encode(fn)
        val hash = sendTransaction(player, game, data)
        waitForReceipt(hash) ?: error("No receipt move")
        hash
    }

    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")
        val fn = Function(
            "getBoardState", emptyList(), listOf(
            object :
                TypeReference<org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.DynamicArray<Address>>>() {}
        ))
        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                credentialsPlayer1.address,
                game,
                FunctionEncoder.encode(fn)
            ), DefaultBlockParameterName.LATEST
        ).send().result
        println("DEBUG board RPC: $raw")
        val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
        val outer = decoded[0].value as List<*>
        outer.map { row -> (row as List<*>).map { (it as Address).value } }
    }
}