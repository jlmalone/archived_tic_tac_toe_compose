package vision.salient

// Imports needed for manual interaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event // Use this specific Event class
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type // Import Type for the List
import org.web3j.abi.datatypes.generated.Uint8 // Use this for uint8
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthSendTransaction // Ensure this import exists
import org.web3j.protocol.core.methods.response.Log
import org.web3j.protocol.core.methods.response.TransactionReceipt // Final receipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager // For constants
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor // For waiting
import org.web3j.utils.Numeric
import io.github.cdimascio.dotenv.dotenv
import java.io.IOException // For exception handling
import java.math.BigInteger


// Data class to load addresses from JSON
@Serializable
private data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

object Blockchain {

    // --- Configuration ---
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val ALCHEMY_API_KEY = dotenv["SEPOLIA_RPC_URL"]?.substringAfterLast('/')
        ?: throw IllegalStateException("SEPOLIA_RPC_URL missing or invalid in .env")
    private val RPC_URL = "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"
    private val PRIVATE_KEY_PLAYER1 = dotenv["PRIVATE_KEY_PLAYER1"] ?: throw RuntimeException("PRIVATE_KEY_PLAYER1 not found")
    private val PRIVATE_KEY_PLAYER2 = dotenv["PRIVATE_KEY_PLAYER2"] ?: throw RuntimeException("PRIVATE_KEY_PLAYER2 not found")

    private val deploymentInfo: DeploymentAddresses? by lazy { loadDeploymentInfo() }
    val factoryContractAddress: String? get() = deploymentInfo?.factoryAddress
    private var currentGameContractAddress: String? = null
    private const val SEPOLIA_CHAIN_ID = 11155111L

    // --- ABIs ---
    private const val factoryAbiString: String = """[{"inputs":[{"internalType":"address","name":"_gameMaster","type":"address"}],"stateMutability":"nonpayable","type":"constructor"},{"inputs":[],"name":"FailedDeployment","type":"error"},{"inputs":[{"internalType":"uint256","name":"balance","type":"uint256"},{"internalType":"uint256","name":"needed","type":"uint256"}],"name":"InsufficientBalance","type":"error"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"gameAddress","type":"address"}],"name":"GameCreated","type":"event"},{"inputs":[],"name":"createGame","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"gameMaster","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"}]"""
    private const val gameAbiString: String = """[{"inputs":[],"stateMutability":"nonpayable","type":"constructor"},{"anonymous":false,"inputs":[],"name":"GameDraw","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"winner","type":"address"}],"name":"GameWon","type":"event"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"address","name":"player","type":"address"},{"indexed":false,"internalType":"uint8","name":"row","type":"uint8"},{"indexed":false,"internalType":"uint8","name":"col","type":"uint8"}],"name":"MoveMade","type":"event"},{"inputs":[{"internalType":"uint8","name":"","type":"uint8"},{"internalType":"uint8","name":"","type":"uint8"}],"name":"board","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"gameEnded","outputs":[{"internalType":"bool","name":"","type":"bool"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"getBoardState","outputs":[{"internalType":"address[3][3]","name":"","type":"address[3][3]"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"lastPlayer","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"uint8","name":"row","type":"uint8"},{"internalType":"uint8","name":"col","type":"uint8"}],"name":"makeMove","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"winner","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"}]"""

    // --- Web3j Instances & Credentials ---
    private val web3j: Web3j by lazy { Web3j.build(HttpService(RPC_URL)) }
    val credentialsPlayer1: Credentials by lazy { Credentials.create(PRIVATE_KEY_PLAYER1) }
    val credentialsPlayer2: Credentials by lazy { Credentials.create(PRIVATE_KEY_PLAYER2) }
    private val gasProvider: DefaultGasProvider by lazy { DefaultGasProvider() }

    // --- Corrected Event Definition ---
    // Constructor takes name and a single list of TypeReferences
    private val gameCreatedEvent = Event(
        "GameCreated", // Event name matches ABI
        // Define the single list of parameters, marking indexed ones
        listOf<TypeReference<*>>( // Explicitly type the list
            TypeReference.create(Address::class.java, true) // gameAddress is indexed address
        )
    )
    private val gameCreatedEventSignature = EventEncoder.encode(gameCreatedEvent)

    // --- Load Deployment Info ---
    private fun loadDeploymentInfo(): DeploymentAddresses? {
        println("Attempting to load deployment info from resources/deployment_output.json...")
        return try {
            val resourceStream = this::class.java.classLoader.getResourceAsStream("deployment_output.json")
            if (resourceStream == null) {
                println("WARNING: deployment_output.json not found.")
                null // Return null if file not found
            } else {
                val jsonString = resourceStream.bufferedReader().use { it.readText() }
                val addresses = Json.decodeFromString<DeploymentAddresses>(jsonString)
                println("Loaded Factory Address: ${addresses.factoryAddress}")
                addresses
            }
        } catch (e: Exception) {
            println("!!! ERROR loading or parsing deployment_output.json: ${e.message}")
            null // Return null on error
        }
    }

    // --- State Management ---
    fun printDerivedAddresses() { /* ... as before ... */ }
    fun getCurrentGameAddress(): String? = currentGameContractAddress
    fun getFactoryAddress(): String? = factoryContractAddress
    fun setCurrentGameAddress(address: String?) { currentGameContractAddress = address }

    // --- Core Interaction Functions ---

    /** Calls createGame, waits for receipt, parses event manually. */
    suspend fun createGameByPlayer1(): String? = withContext(Dispatchers.IO) {
        val currentFactoryAddress = factoryContractAddress ?: throw IllegalStateException("Factory address not loaded.")
        val function = Function("createGame", emptyList(), listOf(TypeReference.create(Address::class.java)))
        val encodedFunction = FunctionEncoder.encode(function)
        val txManager = RawTransactionManager(web3j, credentialsPlayer1, SEPOLIA_CHAIN_ID)

        println("Sending createGame transaction to Factory $currentFactoryAddress...")
        // Step 1: Send Transaction (returns EthSendTransaction with hash)
        val ethSendTx: EthSendTransaction = try { // Explicitly type the variable
            txManager.sendTransaction(
                gasProvider.gasPrice, BigInteger.valueOf(1_500_000L),
                currentFactoryAddress, encodedFunction, BigInteger.ZERO
            )
        } catch (e: Exception) { throw RuntimeException("Failed to send tx: ${e.message}", e) }

        // Use getter methods for EthSendTransaction properties
        if (ethSendTx.hasError()) { // Use getter
            throw RuntimeException("Node error sending tx: ${ethSendTx.error.message}") // Access error message
        }
        val txHash = ethSendTx.transactionHash // Use getter (or property if hasError check works)
        if (txHash == null) {
            throw RuntimeException("Tx sent but no hash received.")
        }
        println("Transaction sent. Tx Hash: $txHash. Waiting for receipt...")

        // Step 2: Wait for Receipt (remains the same)
        val receiptProcessor = PollingTransactionReceiptProcessor(web3j, TransactionManager.DEFAULT_POLLING_FREQUENCY, TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH)
        val txReceipt: TransactionReceipt = try {
            receiptProcessor.waitForTransactionReceipt(txHash)
        } catch (e: Exception) { throw RuntimeException("Failed to get receipt for $txHash: ${e.message}", e) }

        // Step 3: Process Receipt (use getters)
        val status = txReceipt.getStatus()
        val blockNumber = txReceipt.getBlockNumber()
        println("Receipt received. Status: $status. Block: $blockNumber")
        if (status != "0x1") throw RuntimeException("Tx failed on chain (Status: $status)")

        // Step 4: Parse Event (remains the same)
        var newGameAddress: String? = null
        for (log in txReceipt.logs) { /* ... event parsing logic ... */ }

        if (newGameAddress != null) { setCurrentGameAddress(newGameAddress) }
        else { println("Warning: GameCreated event log not found.") }
        return@withContext newGameAddress
    }

    /** Calls makeMove, waits for receipt. */
    suspend fun makeMove(playerIndex: Int, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val gameAddr = currentGameContractAddress ?: throw IllegalStateException("No current game address.")
        val credentials = if (playerIndex == 1) credentialsPlayer1 else credentialsPlayer2
        val txManager = RawTransactionManager(web3j, credentials, SEPOLIA_CHAIN_ID)
        val function = Function("makeMove", listOf(Uint8(row.toLong()), Uint8(col.toLong())), emptyList())
        val encodedFunction = FunctionEncoder.encode(function)

        println("Sending makeMove($row, $col) to $gameAddr as Player $playerIndex...")
        // Step 1: Send Transaction
        val ethSendTx: EthSendTransaction = try { // Explicitly type
            txManager.sendTransaction(gasProvider.gasPrice, BigInteger.valueOf(200_000L), gameAddr, encodedFunction, BigInteger.ZERO)
        } catch (e: Exception) { throw RuntimeException("Failed to send makeMove tx: ${e.message}", e) }

        // Use getter methods for EthSendTransaction properties
        if (ethSendTx.hasError()) { // Use getter
            throw RuntimeException("Node error sending makeMove tx: ${ethSendTx.error.message}") // Access error message
        }
        val txHash = ethSendTx.transactionHash // Use getter
        if (txHash == null) {
            throw RuntimeException("makeMove Tx sent but no hash received.")
        }
        println("Transaction sent. Tx Hash: $txHash. Waiting for receipt...")

        // Step 2: Wait for Receipt (remains the same)
        val receiptProcessor = PollingTransactionReceiptProcessor(web3j, TransactionManager.DEFAULT_POLLING_FREQUENCY, TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH)
        val txReceipt: TransactionReceipt = try {
            receiptProcessor.waitForTransactionReceipt(txHash)
        } catch (e: Exception) { throw RuntimeException("Failed to get receipt for makeMove $txHash: ${e.message}", e) }

        // Step 3: Process Receipt (use getters)
        val status = txReceipt.getStatus()
        println("Receipt received for makeMove. Status: $status. Block: ${txReceipt.getBlockNumber()}")
        if (status != "0x1") throw RuntimeException("makeMove tx failed on chain (Status: $status)")

        println("makeMove Transaction successful!")
        return@withContext txHash
    }

    // --- View Functions (getBoardState, isGameEnded etc. remain the same) ---
//    suspend fun getBoardState(): List<List<String>>? { /* ... as before ... */ }
//    suspend fun isGameEnded(): Boolean? { /* ... as before ... */ }

//} // End Blockchain object
    /** Gets the board state from the current game contract using manual encoding. */
    suspend fun getBoardState(): List<List<String>>? = withContext(Dispatchers.IO) {
        val gameAddr = currentGameContractAddress ?: return@withContext null
        // Define function with correct output type for manual decoding
        val function = Function("getBoardState", emptyList(),
            listOf(object : TypeReference<DynamicArray<DynamicArray<Address>>>() {})
        )
        val encodedFunction = FunctionEncoder.encode(function)

        println("Calling getBoardState view function on $gameAddr...")
        return@withContext try {
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(credentialsPlayer1.address, gameAddr, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send()
            if (response.hasError() || response.result == null || response.result == "0x") {
                println("View call getBoardState failed: ${response.error?.message}"); null
            } else {
                val decodedResult = FunctionReturnDecoder.decode(response.result, function.outputParameters)
                if (decodedResult.isNotEmpty()) {
                    val outerArray = decodedResult[0] as DynamicArray<*>
                    outerArray.value.map { row -> (row as DynamicArray<*>).value.map { (it as Address).value } }
                } else { null }
            }
        } catch (e: Exception) { println("Exc calling getBoardState: ${e.message}"); null }
    }

    // Add other view functions manually if needed (isGameEnded, getLastPlayer, getWinner)

} // End Blockchain object