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

@Serializable
private data class DeploymentAddresses(
    val gameImplementationAddress: String? = null,
    val factoryAddress: String? = null
)

object Blockchain {
    // --- Startup .env load ---
    private val dotenv = dotenv { ignoreIfMissing = true }

    // --- Mutable runtime flags & RPC setup ---
    private var localFlag = dotenv["LOCAL"]?.toBoolean() ?: true
    val isLocal get() = localFlag

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

    private val HARDHAT_PROJECT_DIR = dotenv["HARDHAT_PROJECT_DIR"] ?: "${System.getProperty("user.home")}/tic-tac-toe-smart-contract"

    private val RPC_URL: String
        get() = if (localFlag)
            dotenv["LOCAL_RPC_URL"] ?: "http://127.0.0.1:8545/"
        else
            dotenv["SEPOLIA_RPC_URL"] ?: "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"

    private var httpService = HttpService(RPC_URL)
    private var web3j = Web3j.build(httpService)

    // --- Dynamic credentials getters ---
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

    fun getFactoryAddress(): String? = deploymentInfo?.factoryAddress

    private var currentGameAddress: String? = null
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

    fun getCurrentGameAddress(): String? = currentGameAddress

    // --- Logging & debug info ---
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
     * Run the TS deploy script in the Solidity repo.
    // removed    * @param projectDir  absolute path to tic-tac-toe-smart-contract
     * @return true on success
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

    /** Returns the hex address in lower-case; never throws ClassCastException. */
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

    // Emoji-hash helper (if you need it elsewhere)
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
     * Fetches the 3×3 board from the contract.
     * Tries the “dynamic array of dynamic arrays” decoder first;
     * on ANY failure (including ClassCastException) falls back
     * to slicing the raw 9×32-byte words.
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
