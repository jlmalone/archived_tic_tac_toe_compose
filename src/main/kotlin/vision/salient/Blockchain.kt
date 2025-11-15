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
import kotlin.text.get

/**
 * Data class representing deployment addresses from deployment JSON files.
 *
 * This class is used to deserialize the deployment output files generated
 * by the Hardhat/TypeScript deployment scripts.
 *
 * @property gameImplementationAddress The address of the deployed TicTacToeGame implementation contract
 * @property factoryAddress The address of the deployed TicTacToeFactory contract
 */
@Serializable
private data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

/**
 * Blockchain integration module for Tic-Tac-Toe DApp.
 *
 * This object provides a complete Web3j-based interface to interact with
 * Ethereum-based Tic-Tac-Toe smart contracts. It supports both local Hardhat
 * development networks and Sepolia testnet.
 *
 * ## Features
 * - Dual network support (LOCAL Hardhat / SEPOLIA testnet)
 * - Player credential management
 * - Smart contract deployment via TypeScript scripts
 * - Game creation and move submission
 * - Board state reading and decoding
 * - Transaction handling with retry logic
 * - Gas price optimization
 *
 * ## Security Considerations
 * - Private keys are loaded from environment variables (.env file)
 * - All transactions are signed locally using Web3j
 * - Gas prices include 150% safety margin
 * - Automatic retry on "underpriced" transaction errors
 * - See SECURITY.md for comprehensive security analysis
 *
 * ## Usage Example
 * ```kotlin
 * // Switch to LOCAL network
 * Blockchain.applyLocal(true)
 *
 * // Load factory address
 * val factoryAddress = Blockchain.getFactoryAddress()
 *
 * // Create a new game
 * val gameAddress = Blockchain.createGameByPlayer(0)
 * Blockchain.setCurrentGameAddress(gameAddress)
 *
 * // Make a move
 * Blockchain.makeMove(0, row = 0, col = 0)
 *
 * // Read board state
 * val board = Blockchain.getBoardState()
 * ```
 *
 * @see <a href="https://docs.web3j.io/">Web3j Documentation</a>
 */
object Blockchain {
    // --- Startup .env load ---
    private val dotenv = dotenv { ignoreIfMissing = true }

    // --- Mutable runtime flags & RPC setup ---
    private var localFlag = dotenv["LOCAL"]?.toBoolean() ?: true

    /**
     * Returns true if currently configured for LOCAL (Hardhat) network.
     *
     * @return true if LOCAL mode, false if SEPOLIA mode
     */
    val isLocal get() = localFlag

    /**
     * Switches between LOCAL (Hardhat) and SEPOLIA testnet networks.
     *
     * This function performs a complete network switch, including:
     * - Updating the RPC URL
     * - Reloading deployment info from the appropriate JSON file
     * - Resetting the current game address
     * - Rebuilding the Web3j HTTP client
     *
     * @param flag true for LOCAL (Hardhat), false for SEPOLIA
     *
     * @see [isLocal] for checking current network mode
     */
    fun applyLocal(flag: Boolean) {
        localFlag = flag
        // reset chain-specific state
        deploymentInfo = loadDeploymentInfo()
        currentGameAddress = null
        // rebuild HTTP client
        httpService = HttpService(RPC_URL)
        web3j = Web3j.build(httpService)
        println("[Blockchain] ➡️  Switched to ${if (localFlag) "LOCAL/Hardhat" else "SEPOLIA"}")
        printDerivedAddresses()
    }

    private val ALCHEMY_API_KEY = dotenv["ALCHEMY_API_KEY"] ?: ""
    private val SEPOLIA_CHAIN_ID = dotenv["SEPOLIA_CHAIN_ID"]?.toLongOrNull() ?: 11155111L
    private val HARDHAT_CHAIN_ID = dotenv["HARDHAT_CHAIN_ID"]?.toLongOrNull() ?: 31337L

    private val HARDHAT_PROJECT_DIR = dotenv["HARDHAT_PROJECT_DIR"] ?: "/Users/josephmalone/tic-tac-toe-smart-contract"

    private val RPC_URL: String
        get() = if (localFlag)
            dotenv["LOCAL_RPC_URL"] ?: "http://127.0.0.1:8545/"
        else
            dotenv["SEPOLIA_RPC_URL"] ?: "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"

    private var httpService = HttpService(RPC_URL)
    private var web3j = Web3j.build(httpService)

    // --- Dynamic credentials getters ---
    /**
     * Retrieves player credentials (private key wrapper) for the specified player index.
     *
     * This function loads the appropriate private key from environment variables based on:
     * - Current network mode (LOCAL vs SEPOLIA)
     * - Player index (0 or 1)
     *
     * ## Environment Variables
     * - LOCAL mode:
     *   - Player 0: `PRIVATE_KEY_HARDHAT_0`
     *   - Player 1: `PRIVATE_KEY_HARDHAT_1`
     * - SEPOLIA mode:
     *   - Player 0: `PRIVATE_KEY_PLAYER1`
     *   - Player 1: `PRIVATE_KEY_PLAYER2`
     *
     * @param index Player index (0 or 1). Index >= 1 defaults to player 1.
     * @return Credentials object containing the player's private key and derived address
     * @throws IllegalStateException if the required environment variable is missing
     *
     * @see [isLocal] for current network mode
     */
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

    /**
     * Returns the number of supported players (always 2 for Tic-Tac-Toe).
     *
     * @return 2 (number of players)
     */
    fun getPlayerCount(): Int = 2

    // --- Deployment info load & reload ---
    private var deploymentInfo: DeploymentAddresses? = loadDeploymentInfo().apply {
        //todo
        println("deployment info is "+this.toString())
    }

    private fun loadDeploymentInfo(): DeploymentAddresses? {

        //do not screw this up and change it
        val path =  if(isLocal) "deployment_output_hardhat_local.json" else "deployment_output_sepolia_testnet.json"
        println("path is $path")
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return null
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<DeploymentAddresses>(stream.reader().readText())
    }

    /**
     * Returns the factory contract address for the current network.
     *
     * The factory address is loaded from deployment JSON files:
     * - LOCAL: `deployment_output_hardhat_local.json`
     * - SEPOLIA: `deployment_output_sepolia_testnet.json`
     *
     * @return Factory contract address (0x...) or null if not deployed
     */
    fun getFactoryAddress(): String? = deploymentInfo?.factoryAddress

    private var currentGameAddress: String? = null

    /**
     * Sets the currently active game contract address.
     *
     * This address is used by all game-related functions (makeMove, getBoardState, etc.).
     *
     * @param addr Game contract address (0x...) or null to clear
     * @see [getCurrentGameAddress] for retrieving the current game address
     */
    fun setCurrentGameAddress(addr: String?) {
        currentGameAddress = addr
    }

//    /** Call a zero-arg function that returns a single `bool`. */
//    suspend fun readBool(fnName: String): Boolean = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//        val fn = Function(
//            fnName,
//            emptyList(),
//            listOf(object : TypeReference<Bool>() {})
//        )
//        val callData = FunctionEncoder.encode(fn)
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(getPlayerCredentials(0).address, game, callData),
//            DefaultBlockParameterName.LATEST
//        ).send().value
//        println("DEBUG call $fnName → $raw")
//        FunctionReturnDecoder.decode(raw, fn.outputParameters)
//            .firstOrNull()
//            .let { (it as? Bool)?.value }
//            ?: false
//    }
//
//    /** Call a zero-arg function that returns a single `address`. */
//    suspend fun readAddress(fnName: String): String = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//        val fn = Function(
//            fnName,
//            emptyList(),
//            listOf(object : TypeReference<Address>() {})
//        )
//        val callData = FunctionEncoder.encode(fn)
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(getPlayerCredentials(0).address, game, callData),
//            DefaultBlockParameterName.LATEST
//        ).send().value
//        println("DEBUG call $fnName → $raw")
//        FunctionReturnDecoder.decode(raw, fn.outputParameters)
//            .firstOrNull()
//            .let { (it as? Address)?.value?.lowercase() }
//            ?: "<none>"
//    }

//    /** ----------------------------------------------------------------
//     *  Board decoding helper.
//     *  Hard-hat build returns a static address[3][3] (9×20-byte words).
//     *  Web3j cannot reflect `StaticArray3<StaticArray3<Address>>`, so:
//     *    ① try the canonical decoder (works if contract ever returns a
//     *       dynamic [][] array);
//     *    ② fallback → manual slice-up for the static 3×3 matrix.
//     *  Returns rows = listOf( row0, row1, row2 )  where each row is a
//     *  list of owner-addresses as lowercase hex strings.
//     *  ---------------------------------------------------------------- */
//    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//
//        // ①  attempt dynamic [][] decode
//        val fnDyn = Function(
//            "getBoardState", emptyList(),
//            listOf(object : TypeReference<
//                    org.web3j.abi.datatypes.DynamicArray<
//                            org.web3j.abi.datatypes.DynamicArray<Address>>>() {})
//        )
//        val callData = FunctionEncoder.encode(fnDyn)
//
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(getPlayerCredentials(0).address, game, callData),
//            DefaultBlockParameterName.LATEST
//        ).send().result
//        println("DEBUG RPC → $raw")
//
//        try {
//            val decoded = FunctionReturnDecoder.decode(raw, fnDyn.outputParameters)
//            val outer   = decoded[0].value as List<*>
//            return@withContext outer.map { row ->
//                (row as List<*>).map { (it as Address).value.lowercase() }
//            }
//        } catch (e: Exception) {
//            // ②  static address[3][3] fallback
//            println("…dynamic decode failed → static 3×3 fallback (${e.message})")
//            if (raw == "0x") error("Contract returned empty data")
//
//            /*  Solidity packs a static array directly (no head/tail):
//                   slot0-8 = 9 × 32-byte words
//                     each word: 12 bytes padding | 20 bytes address
//                 -> take the last 40 hex chars of each 64-char word       */
//            val addrs = buildList(9) {            // flatten first
//                for (i in 0 until 9) {
//                    val word   = raw.drop(2).substring(i * 64, i * 64 + 64)
//                    val addr40 = word.takeLast(40)
//                    add("0x$addr40".lowercase())
//                }
//            }
//            // chunk into 3 rows
//            return@withContext listOf(
//                addrs.subList(0, 3),
//                addrs.subList(3, 6),
//                addrs.subList(6, 9)
//            )
//        }
//    }

    /**
     * Returns the currently active game contract address.
     *
     * @return Current game address (0x...) or null if no game is set
     * @see [setCurrentGameAddress] for setting the active game
     */
    fun getCurrentGameAddress(): String? = currentGameAddress

    // --- Logging & debug info ---
    /**
     * Prints player addresses, factory address, and network information to console.
     *
     * This is a debug utility that displays:
     * - Player 1 and Player 2 addresses
     * - Factory contract address
     * - Current game address (if set)
     * - RPC URL and chain ID
     *
     * **Note**: This only prints public addresses, never private keys.
     */
    fun printDerivedAddresses() {
        println("── address info ──")
        println("P1    : ${getPlayerCredentials(0).address}")
        println("P2    : ${getPlayerCredentials(1).address}")
        println("Factory: ${getFactoryAddress() ?: "<none>"}")
        println("Game   : ${currentGameAddress ?: "<none>"}")
        println("RPC    : $RPC_URL (chainId=${if (localFlag) HARDHAT_CHAIN_ID else SEPOLIA_CHAIN_ID})")
        println("───────────────────")
    }

    // --- Helpers ---
    private suspend fun calculateGasPrice(prev: BigInteger? = null): BigInteger = withContext(Dispatchers.IO) {
        val market = web3j.ethGasPrice().send().gasPrice
        val target = market.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(2))
        val bump = prev?.multiply(BigInteger.valueOf(11))?.divide(BigInteger.valueOf(10)) ?: BigInteger.ZERO
        target.max(bump)
    }


    /**
     * Executes the TypeScript deployment script to deploy smart contracts.
     *
     * This function runs the deployment script located in the Hardhat project directory:
     * `deployment/deploy_ethers.ts`
     *
     * The script is executed via `npx tsx` with the appropriate network flag:
     * - LOCAL mode: `--local` flag
     * - SEPOLIA mode: `--sepolia` flag
     *
     * ## Environment Variables
     * - `HARDHAT_PROJECT_DIR`: Path to the smart contract repository (default: `/Users/josephmalone/tic-tac-toe-smart-contract`)
     * - `NPX_PATH`: Path to npx executable (default: `npx`)
     *
     * ## Security Warning
     * This function executes external commands. Ensure `HARDHAT_PROJECT_DIR` and `NPX_PATH`
     * point to trusted locations. See SECURITY.md for details.
     *
     * @return true if deployment succeeded (exit code 0), false otherwise
     *
     * @see [applyLocal] to switch networks before deploying
     */
    fun runDeploy(): Boolean {//projectDir: File
        val flag = if (isLocal) "--local" else "--sepolia"

        // 1️⃣  configurable override
        val rawNpx = dotenv["NPX_PATH"]?.ifBlank { null } ?: "npx"

        // first try plain npx
        var cmdList = listOf(rawNpx, "tsx", "deployment/deploy_ethers.ts", flag)

        var pb = ProcessBuilder(cmdList).directory(File(HARDHAT_PROJECT_DIR))

        fun run(pb: ProcessBuilder): Boolean {
            val p = pb.inheritIO().start()
            return p.waitFor() == 0
        }

        return try {
            println("[Blockchain] ■ ${cmdList.joinToString(" ")}")
            run(pb)
        } catch (e: java.io.IOException) {
            // fall-back: run through login shell so PATH gets initialised
            cmdList = listOf(
                "bash", "-lc",
                "${cmdList.joinToString(" ")}"        // the same npx command
            )
            pb = ProcessBuilder(cmdList).directory(File(HARDHAT_PROJECT_DIR))
            println("[Blockchain] ■ via shell → ${cmdList.joinToString(" ")}")
            run(pb)
        }
    }
//    suspend fun readBool(fnName: String): Boolean = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//        val fn = Function(
//            fnName,
//            emptyList(),
//            listOf(TypeReference.create(Bool::class.java))
//        )
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(
//                getPlayerCredentials(0).address, game,
//                FunctionEncoder.encode(fn)
//            ),
//            DefaultBlockParameterName.LATEST
//        ).send().result
//        try {
//            val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
//            return@withContext decoded[0].value as Boolean
//        } catch (iae: IllegalArgumentException) {
//            error("Cannot decode boolean from ‘$raw’")
//        }
//    }
//
//    suspend fun readAddress(fnName: String): String = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//        val fn = Function(
//            fnName,
//            emptyList(),
//            listOf(TypeReference.create(Address::class.java))
//        )
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(
//                getPlayerCredentials(0).address, game,
//                FunctionEncoder.encode(fn)
//            ),
//            DefaultBlockParameterName.LATEST
//        ).send().result
//        try {
//            val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
//            return@withContext (decoded[0].value as Address).value
//        } catch (iae: IllegalArgumentException) {
//            error("Cannot decode address from ‘$raw’")
//        }
//    }
//


    // ──────────────────────────────────
// REPLACE the old helpers with this
// ──────────────────────────────────
    /**
     * Calls a contract function that returns a boolean value.
     *
     * This is a generic helper for reading boolean contract state, such as:
     * - `gameEnded()` - checks if the game has ended
     * - Any other contract function returning `bool`
     *
     * @param fnName Name of the contract function to call
     * @return Boolean value returned by the contract
     * @throws IllegalStateException if no game address is set
     * @throws Exception if the contract call fails or returns invalid data
     *
     * @see [setCurrentGameAddress] for setting the active game
     */
    suspend fun readBool(fnName: String): Boolean = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")
        val fn   = Function(fnName, emptyList(),
            listOf(object : TypeReference<Bool>() {})
        )

        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                getPlayerCredentials(0).address, game,
                FunctionEncoder.encode(fn)
            ),
            DefaultBlockParameterName.LATEST
        ).send().result

        return@withContext try {
            val d = FunctionReturnDecoder.decode(raw, fn.outputParameters)
            when (val v = d.firstOrNull()?.value) {
                is Boolean -> v
                is Bool    -> v.value
                else       -> error("Unexpected bool payload: $v")
            }
        } catch (e: Exception) {
            error("Cannot decode bool for $fnName – $raw")
        }
    }

    /**
     * Calls a contract function that returns an Ethereum address.
     *
     * This is a generic helper for reading address values from contract state, such as:
     * - `winner()` - returns the winner's address (or zero address)
     * - `lastPlayer()` - returns the address of the last player who moved
     * - Any other contract function returning `address`
     *
     * @param fnName Name of the contract function to call
     * @return Ethereum address as lowercase hex string (0x...)
     * @throws IllegalStateException if no game address is set
     *
     * **Note**: Returns zero address (0x0000...0000) on error instead of throwing
     *
     * @see [setCurrentGameAddress] for setting the active game
     */
    suspend fun readAddress(fnName: String): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")
        val fn   = Function(fnName, emptyList(),
            listOf(object : TypeReference<Address>() {})
        )

        val raw = web3j.ethCall(
            Transaction.createEthCallTransaction(
                getPlayerCredentials(0).address, game,
                FunctionEncoder.encode(fn)
            ),
            DefaultBlockParameterName.LATEST
        ).send().result

        return@withContext try {
            val d = FunctionReturnDecoder.decode(raw, fn.outputParameters)
            val v = d.firstOrNull()?.value
            when (v) {
                is Address -> v.value.lowercase()
                is String  -> v.lowercase()
                else       -> ZERO_ADDRESS          // fallback – treat as “no winner”
            }
        } catch (_: Exception) {
            ZERO_ADDRESS                              // graceful fallback
        }
    }



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
                return@withContext sendTransaction(from, to, data, value, gasPrice, 1)
            }
            error("TX Error: $msg")
        }
        resp.transactionHash
    }

    private suspend fun waitForReceipt(txHash: String): TransactionReceipt? = withContext(Dispatchers.IO) {
        repeat(120) { i ->
            if (web3j.ethGetTransactionByHash(txHash).send().transaction.isPresent)
                println("📦 in mempool… attempt ${i + 1}")
            val recOpt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (recOpt.isPresent) return@withContext recOpt.get()
            kotlinx.coroutines.delay(2000)
        }
        null
    }
//
//    // --- Deploy local script launcher ---
//    suspend fun runLocalDeploy(projectDir: File): Boolean = withContext(Dispatchers.IO) {
//        try {
//            val proc = ProcessBuilder("npx", "tsx", "deployment/deploy_ethers.ts")
//                .directory(projectDir)
//                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
//                .redirectError(ProcessBuilder.Redirect.INHERIT)
//                .start()
//            val exit = proc.waitFor()
//            println("[Blockchain] Local deploy exited $exit")
//            // reload factory address
//            applyLocal(true)
//            exit == 0
//        } catch (e: Exception) {
//            println("[Blockchain] Deploy failed: ${e.message}")
//            false
//        }
//    }

    // --- Deploy / Factory → Game flows ---
    /**
     * Creates a new Tic-Tac-Toe game via the factory contract.
     *
     * This function:
     * 1. Calls `createGame()` on the TicTacToeFactory contract
     * 2. Waits for transaction confirmation
     * 3. Extracts the new game address from the `GameCreated` event
     * 4. Automatically sets the new game as the current game
     *
     * ## Transaction Details
     * - Gas price: 150% of current market price
     * - Gas limit: 150% of estimated gas
     * - Signer: Player at index `idx`
     *
     * @param idx Player index who creates the game (default: 0)
     * @return Address of the newly created game contract (0x...) or null if creation failed
     * @throws IllegalStateException if factory address is not set
     * @throws Exception if transaction fails
     *
     * @see [getFactoryAddress] for checking if factory is deployed
     * @see [setCurrentGameAddress] - automatically called with new game address
     */
    suspend fun createGameByPlayer(idx: Int = 0): String? = withContext(Dispatchers.IO) {
        val factory = getFactoryAddress() ?: error("No factory")
        val fn = Function("createGame", emptyList(), listOf(TypeReference.create(Address::class.java)))
        val data = FunctionEncoder.encode(fn)

        val hash = sendTransaction(getPlayerCredentials(idx), factory, data)
        println("[Blockchain] ✔ tx $hash")
        val rec = waitForReceipt(hash) ?: error("No receipt")
        val evt = Event("GameCreated", listOf(TypeReference.create(Address::class.java)))
        val sig = EventEncoder.encode(evt)
        rec.logs.forEach { log ->
            if (log.topics.firstOrNull() == sig) {
                val addrHex = log.topics[1].removePrefix("0x").takeLast(40)
                val newAddr = "0x$addrHex"
                currentGameAddress = newAddr
                return@withContext newAddr
            }
        }
        null
    }

    /**
     * Submits a move to the current game contract.
     *
     * This function:
     * 1. Validates that a game is set (throws if not)
     * 2. Encodes the `makeMove(uint8 row, uint8 col)` call
     * 3. Signs and sends the transaction
     * 4. Waits for transaction confirmation
     *
     * ## Input Validation
     * Client-side validation should ensure:
     * - `row` and `col` are in range [0, 2]
     * - The cell is not already occupied
     * - It's the correct player's turn
     *
     * Contract-side validation will revert if any rules are violated.
     *
     * @param idx Player index making the move (0 or 1)
     * @param row Row index (0-2, top to bottom)
     * @param col Column index (0-2, left to right)
     * @return Transaction hash (0x...)
     * @throws IllegalStateException if no game address is set
     * @throws Exception if transaction fails or is reverted
     *
     * @see [setCurrentGameAddress] for setting the active game
     * @see [getBoardState] for reading the board after a move
     */
    suspend fun makeMove(idx: Int, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")
        val fn = Function(
            "makeMove",
            listOf(Uint8(row.toLong()), Uint8(col.toLong())),
            emptyList()
        )
        val data = FunctionEncoder.encode(fn)
        val hash = sendTransaction(getPlayerCredentials(idx), game, data)
        waitForReceipt(hash) ?: error("No receipt move")
        hash
    }
//
//    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//        val fn = Function(
//            "getBoardState",
//            emptyList(),
//            listOf(object :
//                TypeReference<org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.DynamicArray<Address>>>() {})
//        )
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(
//                getPlayerCredentials(0).address,
//                game,
//                FunctionEncoder.encode(fn)
//            ),
//            DefaultBlockParameterName.LATEST
//        ).send().result
//        println("DEBUG RPC → $raw")
//        val decoded = FunctionReturnDecoder.decode(raw, fn.outputParameters)
//        val outer = decoded[0].value as List<*>
//        outer.map { row ->
//            (row as List<*>).map { (it as Address).value }
//        }
//    }

    /**
     * Generates a deterministic emoji representation for an Ethereum address.
     *
     * This function uses SHA-256 hashing to map each address to one of 20 emojis.
     * The same address will always produce the same emoji, making it useful for
     * visual player identification in the UI.
     *
     * ## Emoji Palette
     * 😀, 🐶, 🌟, 🍕, 🚀, 🐍, 🎮, 📚, 🎵, 🌈,
     * 🍔, 🧠, 🦄, 💎, 🕹️, 🧊, ⚡, 💡, 🧩, 🎯
     *
     * @param addr Ethereum address (0x... or without prefix, case-insensitive)
     * @return Single emoji character representing this address
     *
     * @see [getBoardState] which uses this for displaying occupied cells
     */
    fun emojiForAddress(addr: String): String {
        val emojis = listOf(
            "😀", "🐶", "🌟", "🍕", "🚀", "🐍", "🎮", "📚", "🎵", "🌈",
            "🍔", "🧠", "🦄", "💎", "🕹️", "🧊", "⚡", "💡", "🧩", "🎯"
        )
        val h = MessageDigest.getInstance("SHA-256")
            .digest(addr.lowercase().removePrefix("0x").toByteArray())
        return emojis[(h[0].toInt() and 0xFF) % emojis.size]
    }

    /**
     * Fetches the current 3×3 board state from the game contract.
     *
     * This function reads the board via `getBoardState()` contract call and returns
     * a 3×3 matrix of Ethereum addresses representing occupied cells.
     *
     * ## Return Format
     * ```kotlin
     * listOf(
     *   listOf(cell[0][0], cell[0][1], cell[0][2]), // Top row
     *   listOf(cell[1][0], cell[1][1], cell[1][2]), // Middle row
     *   listOf(cell[2][0], cell[2][1], cell[2][2])  // Bottom row
     * )
     * ```
     *
     * Each cell contains:
     * - Player's Ethereum address (0x...) if occupied
     * - Zero address (0x0000...0000) if empty
     *
     * ## Decoding Strategy
     * This function implements a fallback decoding strategy:
     * 1. **Primary**: Attempt to decode as dynamic `address[][]` array
     * 2. **Fallback**: If decoding fails, manually parse the raw 9×32-byte ABI encoding
     *
     * The fallback is necessary because Solidity's static `address[3][3]` and dynamic
     * `address[][]` have different ABI encodings.
     *
     * @return 3×3 list of lowercase Ethereum address strings
     * @throws IllegalStateException if no game address is set
     * @throws Exception if contract returns empty data or invalid format
     *
     * @see [makeMove] for updating the board
     * @see [emojiForAddress] for converting addresses to visual emojis
     */
    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
        val game = currentGameAddress ?: error("No game set")

        // ① dynamic decode
        val dynFn = Function(
            "getBoardState",
            emptyList(),
            listOf(object : TypeReference<
                    org.web3j.abi.datatypes.DynamicArray<
                            org.web3j.abi.datatypes.DynamicArray<Address>>>() {})
        )
        val callData = FunctionEncoder.encode(dynFn)
        val raw = web3j
            .ethCall(
                Transaction.createEthCallTransaction(
                    getPlayerCredentials(0).address,
                    game, callData
                ),
                DefaultBlockParameterName.LATEST
            )
            .send()
            .result
        println("DEBUG RPC → $raw")

        try {
            val decoded = FunctionReturnDecoder.decode(raw, dynFn.outputParameters)
            @Suppress("UNCHECKED_CAST")
            val outer = decoded[0].value as List<*>
            return@withContext outer.map { row ->
                @Suppress("UNCHECKED_CAST")
                (row as List<*>).map { cell ->
                    when (cell) {
                        is Address -> cell.value.lowercase()
                        is String  -> cell.lowercase()
                        else        -> error("Unexpected board cell type: ${cell?.javaClass}")
                    }
                }
            }
        } catch (decodeErr: Exception) {
            // ② static 3×3 fallback
            println("…dynamic decode failed → static 3×3 fallback (${decodeErr.message})")
            if (raw == "0x") error("Contract returned empty data")

            // strip “0x”, take 9 words of 64 hex chars each → last 40 chars = address
            val flat = (0 until 9).map { i ->
                val word = raw.drop(2).substring(i*64, i*64 + 64)
                "0x" + word.takeLast(40)
            }
            return@withContext listOf(
                flat.subList(0,3),
                flat.subList(3,6),
                flat.subList(6,9)
            ).map { row -> row.map(String::lowercase) }
        }
    }


//
//
//    suspend fun getBoardState(): List<List<String>> = withContext(Dispatchers.IO) {
//        val game = currentGameAddress ?: error("No game set")
//
//        // ① Try the canonical dynamic [][] decoder
//        val fnDyn = Function(
//            "getBoardState",
//            emptyList(),
//            listOf(object : TypeReference<DynamicArray<DynamicArray<Address>>>() {})
//        )
//        val callData = FunctionEncoder.encode(fnDyn)
//
//        val raw = web3j.ethCall(
//            Transaction.createEthCallTransaction(
//                getPlayerCredentials(0).address,
//                game,
//                callData
//            ),
//            DefaultBlockParameterName.LATEST
//        ).send().value
//        println("DEBUG RPC → $raw")
//
//        try {
//            val decoded = FunctionReturnDecoder.decode(raw, fnDyn.outputParameters)
//            val outer   = decoded[0].value as List<*>
//            return@withContext outer.map { row ->
//                (row as List<*>).map { (it as Address).value.lowercase() }
//            }
//        } catch (e: Exception) {
//            // ② Fallback for a static 3×3 array (Hardhat output)
//            println("…dynamic decode failed → static 3×3 fallback (${e.message})")
//            if (raw == "0x") error("Contract returned empty data")
//
//            // Split into 64-char words, take first 9
//            val words = raw.removePrefix("0x").chunked(64)
//            if (words.size < 9) error("Unexpected board payload length: ${words.size}")
//            val addrs = words.take(9).map { word ->
//                "0x" + word.takeLast(40).lowercase()
//            }
//
//            // Chunk into three rows
//            return@withContext listOf(
//                addrs.subList(0, 3),
//                addrs.subList(3, 6),
//                addrs.subList(6, 9)
//            )
//        }
//    }
}
