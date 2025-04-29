// src/main/kotlin/vision/salient/Blockchain.kt
package vision.salient

import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.FunctionReturnDecoder
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import io.github.cdimascio.dotenv.dotenv // Import dotenv
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.tx.TransactionManager
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.io.IOException

object Blockchain {


    // Add or uncomment this function INSIDE the Blockchain object
    suspend fun getFactoryGameMasterAddress(): String? = withContext(Dispatchers.IO) {
        val function = Function(
            "gameMaster", // Function name from Factory ABI
            emptyList(),
            listOf(TypeReference.create(Address::class.java)) // Expects Address output
        )
        val encodedFunction = FunctionEncoder.encode(function)

        println("Calling gameMaster view function on Factory $FACTORY_CONTRACT_ADDRESS...")
        return@withContext try {
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(credentialsPlayer1.address, FACTORY_CONTRACT_ADDRESS, encodedFunction),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError()) {
                println("View call to Factory gameMaster failed: ${response.error.message}")
                null // Return null if the node reports an error
            } else if (response.result == null || response.result.equals("0x", ignoreCase = true) || response.result.equals("0x0000000000000000000000000000000000000000000000000000000000000000", ignoreCase = true)) {
                // Handle cases where the node might return empty '0x' or actual zero address
                println("View call to Factory gameMaster returned empty or zero address result.")
                "0x0000000000000000000000000000000000000000" // Return zero address string explicitly
            } else {
                // Try decoding if we got a non-empty, non-zero result
                val output = FunctionReturnDecoder.decode(response.result, function.outputParameters)
                if (output.isNotEmpty() && output[0] is Address) {
                    val gmAddress = (output[0] as Address).value
                    println("Decoded gameMaster address: $gmAddress")
                    gmAddress // Return the decoded address string
                } else {
                    println("Failed to decode Factory gameMaster response or unexpected type: ${output?.firstOrNull()?.typeAsString}")
                    null // Return null if decoding fails
                }
            }
        } catch (e: Exception) {
            println("Exception calling Factory gameMaster: ${e.message}")
            null // Return null on exception
        }
    }

    // Add this function INSIDE the Blockchain object
    fun printDerivedAddresses() {
        try {
            println("--- Address Verification ---")
            // Accessing .address triggers the lazy initialization and Credentials.create()
            val p1Address = credentialsPlayer1.address
            println("Derived Player 1 Address: $p1Address")

            val p2Address = credentialsPlayer2.address
            println("Derived Player 2 Address: $p2Address")
            println("--------------------------")
        } catch (e: Exception) {
            println("!!! ERROR during address verification: ${e.message}")
            // This might happen if a key in .env is fundamentally invalid (e.g., wrong length)
        }
    }

    const val ALCHEMY_API_KEY = "xQr4u0HS5Fyivvu4-IxgOf1qlUdgDz2Z"
    // --- Configuration ---
//    private const val RPC_URL = "https://rpc.sepolia.chainstacklabs.com" // Public Sepolia RPC
//    private const val RPC_URL = "https://ethereum-sepolia.publicnode.com"

    private const val RPC_URL = "https://eth-sepolia.g.alchemy.com/v2/$ALCHEMY_API_KEY"

    //alchemy api key alcht_rtaSOxjYCgcX3By346i11uETELJ20I
    //alchemy real api key xQr4u0HS5Fyivvu4-IxgOf1qlUdgDz2Z
//    private const val RPC_URL = "https://rpc.ankr.com/eth_sepolia"
    // Load dotenv configuration
    private val dotenv = dotenv {
        ignoreIfMissing = true // Don't crash if .env is missing
    }

    // Load Private Keys from .env or throw error if missing
    private val PRIVATE_KEY_PLAYER1 = dotenv["PRIVATE_KEY_PLAYER1"]
        ?: throw RuntimeException("PRIVATE_KEY_PLAYER1 not found in .env file or system environment.")
    private val PRIVATE_KEY_PLAYER2 = dotenv["PRIVATE_KEY_PLAYER2"]
        ?: throw RuntimeException("PRIVATE_KEY_PLAYER2 not found in .env file or system environment.")

    // Factory address - Replace with your deployed Factory address
    const val FACTORY_CONTRACT_ADDRESS = "0xe7281d0e4c2284c4a644ab3284b0dfe4c1527083"

    // ABI Paths - Ensure these are correct
    private const val FACTORY_ABI_PATH = "/Users/josephmalone/tic-tac-toe-smart-contract/abis/TicTacToeFactory.abi"
    private const val GAME_ABI_PATH = "/Users/josephmalone/tic-tac-toe-smart-contract/abis/MultiPlayerTicTacToe.abi"

    // Current game address - track the active game
    private var currentGameContractAddress: String? = null

    // --- Web3j Instances (Lazy initialized) ---
    private val web3j: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL))
    }

    // --- Credentials for each player (Lazy initialized) ---
    val credentialsPlayer1: Credentials by lazy {
        try {
            Credentials.create(PRIVATE_KEY_PLAYER1)
        } catch (e: Exception) {
            println("!!! ERROR creating credentials for Player 1: ${e.message}")
            throw RuntimeException("Invalid PRIVATE_KEY_PLAYER1 format or other error.", e)
        }
    }

//
//    // Add this function INSIDE the Blockchain object in Blockchain.kt
//    suspend fun getFactoryGameMasterAddress(): String? = withContext(Dispatchers.IO) {
//        val function = Function(
//            "gameMaster", // Function name from Factory ABI
//            emptyList(),
//            listOf(TypeReference.create(Address::class.java)) // Expects Address output
//        )
//        val encodedFunction = FunctionEncoder.encode(function)
//
//        println("Calling gameMaster view function on Factory $FACTORY_CONTRACT_ADDRESS...")
//        return@withContext try {
//            val response = web3j.ethCall(
//                Transaction.createEthCallTransaction(credentialsPlayer1.address, FACTORY_CONTRACT_ADDRESS, encodedFunction),
//                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
//            ).send()
//
//            if (response.hasError()) {
//                println("View call to Factory gameMaster failed: ${response.error.message}")
//                null
//            } else if (response.result == null || response.result.equals("0x", ignoreCase = true) || response.result.equals("0x0000000000000000000000000000000000000000000000000000000000000000", ignoreCase = true)) {
//                // Handle cases where the node might return empty '0x' or actual zero address for uninitialized storage
//                println("View call to Factory gameMaster returned empty or zero address result.")
//                "0x0000000000000000000000000000000000000000" // Return zero address explicitly
//            }
//            else {
//                val output = FunctionReturnDecoder.decode(response.result, function.outputParameters)
//                if (output.isNotEmpty() && output[0] is Address) {
//                    (output[0] as Address).value
//                } else {
//                    println("Failed to decode Factory gameMaster response or unexpected type: ${output?.firstOrNull()?.typeAsString}")
//                    null
//                }
//            }
//        } catch (e: Exception) {
//            println("Exception calling Factory gameMaster: ${e.message}")
//            null
//        }
//    }

    val credentialsPlayer2: Credentials by lazy {
        try {
            Credentials.create(PRIVATE_KEY_PLAYER2)
        } catch (e: Exception) {
            println("!!! ERROR creating credentials for Player 2: ${e.message}")
            throw RuntimeException("Invalid PRIVATE_KEY_PLAYER2 format or other error.", e)
        }
    }

    // --- Default Gas Provider ---
    private val gasProvider: DefaultGasProvider by lazy {
        DefaultGasProvider()
    }

    //    // --- ABI Loading (Lazy initialized from file) ---
//    private val factoryAbiString: String by lazy {
//        try { File(FACTORY_ABI_PATH).readText() }
//        catch (e: Exception) { throw RuntimeException("Failed to load Factory ABI from $FACTORY_ABI_PATH", e) }
//    }
    private const val gameAbiString: String = """
        [ 
						{
							"inputs": [],
							"stateMutability": "nonpayable",
							"type": "constructor"
						},
						{
							"anonymous": false,
							"inputs": [],
							"name": "GameDraw",
							"type": "event"
						},
						{
							"anonymous": false,
							"inputs": [
								{
									"indexed": true,
									"internalType": "address",
									"name": "winner",
									"type": "address"
								}
							],
							"name": "GameWon",
							"type": "event"
						},
						{
							"anonymous": false,
							"inputs": [
								{
									"indexed": true,
									"internalType": "address",
									"name": "player",
									"type": "address"
								},
								{
									"indexed": false,
									"internalType": "uint8",
									"name": "row",
									"type": "uint8"
								},
								{
									"indexed": false,
									"internalType": "uint8",
									"name": "col",
									"type": "uint8"
								}
							],
							"name": "MoveMade",
							"type": "event"
						},
						{
							"inputs": [
								{
									"internalType": "uint8",
									"name": "",
									"type": "uint8"
								},
								{
									"internalType": "uint8",
									"name": "",
									"type": "uint8"
								}
							],
							"name": "board",
							"outputs": [
								{
									"internalType": "address",
									"name": "",
									"type": "address"
								}
							],
							"stateMutability": "view",
							"type": "function"
						},
						{
							"inputs": [],
							"name": "gameEnded",
							"outputs": [
								{
									"internalType": "bool",
									"name": "",
									"type": "bool"
								}
							],
							"stateMutability": "view",
							"type": "function"
						},
						{
							"inputs": [],
							"name": "getBoardState",
							"outputs": [
								{
									"internalType": "address[3][3]",
									"name": "",
									"type": "address[3][3]"
								}
							],
							"stateMutability": "view",
							"type": "function"
						},
						{
							"inputs": [],
							"name": "lastPlayer",
							"outputs": [
								{
									"internalType": "address",
									"name": "",
									"type": "address"
								}
							],
							"stateMutability": "view",
							"type": "function"
						},
						{
							"inputs": [
								{
									"internalType": "uint8",
									"name": "row",
									"type": "uint8"
								},
								{
									"internalType": "uint8",
									"name": "col",
									"type": "uint8"
								}
							],
							"name": "makeMove",
							"outputs": [],
							"stateMutability": "nonpayable",
							"type": "function"
						},
						{
							"inputs": [],
							"name": "winner",
							"outputs": [
								{
									"internalType": "address",
									"name": "",
									"type": "address"
								}
							],
							"stateMutability": "view",
							"type": "function"
						}
					] 
    """ // End triple quote

    private const val factoryAbiString: String = """
        [ 
						{
							"inputs": [],
							"stateMutability": "nonpayable",
							"type": "constructor"
						},
						{
							"inputs": [],
							"name": "FailedDeployment",
							"type": "error"
						},
						{
							"inputs": [
								{
									"internalType": "uint256",
									"name": "balance",
									"type": "uint256"
								},
								{
									"internalType": "uint256",
									"name": "needed",
									"type": "uint256"
								}
							],
							"name": "InsufficientBalance",
							"type": "error"
						},
						{
							"anonymous": false,
							"inputs": [
								{
									"indexed": true,
									"internalType": "address",
									"name": "gameAddress",
									"type": "address"
								}
							],
							"name": "GameCreated",
							"type": "event"
						},
						{
							"inputs": [],
							"name": "createGame",
							"outputs": [
								{
									"internalType": "address",
									"name": "",
									"type": "address"
								}
							],
							"stateMutability": "nonpayable",
							"type": "function"
						},
						{
							"inputs": [],
							"name": "gameMaster",
							"outputs": [
								{
									"internalType": "address",
									"name": "",
									"type": "address"
								}
							],
							"stateMutability": "view",
							"type": "function"
						}
					] 
    """ // End triple quote

//    private val gameAbiString: String by lazy {
//        try { File(GAME_ABI_PATH).readText() }
//        catch (e: Exception) { throw RuntimeException("Failed to load Game ABI from $GAME_ABI_PATH", e) }
//    }

    // --- Functions to interact with the blockchain ---


    /**
     * Calls the createGame function on the Factory contract using Player 1's credentials.
     * Manually sends the transaction with an increased gas limit and waits for the receipt.
     * @return The transaction hash.
     */
    suspend fun createGameByPlayer1(): String = withContext(Dispatchers.IO) {
        val function = Function(
            "createGame",
            emptyList(),
            listOf(TypeReference.create(Address::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        // Create a RawTransactionManager specific to Player 1
        val txManagerPlayer1 = RawTransactionManager(web3j, credentialsPlayer1, 11155111L) // Sepolia Chain ID

        println("Sending createGame transaction via RawTransactionManager as Player 1 (${credentialsPlayer1.address})...")

        // --- Step 1: Send the transaction using the manager with Manual Gas Limit ---
        val ethSendTransaction: EthSendTransaction
        try {
            ethSendTransaction = txManagerPlayer1.sendTransaction(
                gasProvider.gasPrice,             // Use default/fetched gas price
                // --- Manually Increased Gas Limit ---
                BigInteger.valueOf(1_000_000L), // Use a fixed higher limit (e.g., 1 million)
                // --- End Manual Gas Limit ---
                FACTORY_CONTRACT_ADDRESS,       // The contract to call
                encodedFunction,                // The encoded function data
                BigInteger.ZERO                 // Value (ETH) to send
            )
        } catch (e: IOException) {
            // Catch potential IO exceptions during sending (e.g., network issues before getting hash)
            println("Error sending transaction (IO): ${e.message}")
            throw RuntimeException("Failed to send transaction", e)
        }


        // Check for immediate errors from sending response
        if (ethSendTransaction.hasError()) {
            throw RuntimeException("Error response from node when sending transaction: ${ethSendTransaction.error.message}")
        }

        // Get the transaction hash
        val txHash = ethSendTransaction.transactionHash
        if (txHash == null) {
            throw RuntimeException("Transaction sent but no hash received.")
        }
        println("Transaction sent. Tx Hash: $txHash. Waiting for receipt...")

        // --- Step 2: Wait for the transaction receipt ---
        val receiptProcessor = PollingTransactionReceiptProcessor(
            web3j,
            TransactionManager.DEFAULT_POLLING_FREQUENCY, // e.g., 15 seconds
            TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH // e.g., 40 attempts
        )

        val transactionReceipt: TransactionReceipt
        try {
            // This blocks the coroutine until receipt or timeout
            transactionReceipt = receiptProcessor.waitForTransactionReceipt(txHash)
        } catch (e: Exception) {
            println("Error waiting for transaction receipt: ${e.message}")
            throw RuntimeException("Failed to get transaction receipt for $txHash", e)
        }

        // --- Step 3: Process the Receipt ---
        val status: String = transactionReceipt.getStatus()
        val blockNumber: BigInteger = transactionReceipt.getBlockNumber()

        println("Receipt received. Tx Hash: $txHash")
        println("Transaction Status: $status. Block: $blockNumber")

        // Check if the transaction failed on-chain
        if (status != "0x1") {
            throw RuntimeException("Transaction failed on chain (Status was $status). Check block explorer for $txHash.")
        }

        // Transaction succeeded
        println("Transaction executed successfully.")

        // TODO: Parse the GameCreated event from transactionReceipt.logs
        // using the factoryAbiString to get the new game address and potentially call
        // setCurrentGameAddress(newGameAddress)

        return@withContext txHash // Return the transaction hash
    }

//
//    /**
//     * Calls the createGame function on the Factory contract using Player 1's credentials.
//     * @return The transaction hash.
//     */
//    suspend fun createGameByPlayer1(): String = withContext(Dispatchers.IO) {
//        val function = Function(
//            "createGame",
//            emptyList(),
//            listOf(TypeReference.create(Address::class.java))
//        )
//        val encodedFunction = FunctionEncoder.encode(function)
//
//        // Use Player 1's credentials
//        val transaction = Transaction.createFunctionCallTransaction(
//            credentialsPlayer1.address, // Sender is Player 1
//            null, // nonce
//            gasProvider.gasPrice,
//            gasProvider.gasLimit,
//            FACTORY_CONTRACT_ADDRESS, // Target Factory
//            BigInteger.ZERO, // value
//            encodedFunction
//        )
//
//        println("Sending createGame transaction as Player 1 (${credentialsPlayer1.address})...")
//        val response = web3j.ethSendTransaction(transaction).send()
//
//        if (response.hasError()) {
//            throw RuntimeException("Transaction failed: ${response.error.message}")
//        }
//        val txHash = response.transactionHash
//        println("Transaction sent. Tx Hash: $txHash")
//
//        // TODO: Implement robust waiting for receipt and event parsing
//        // to automatically get and set the currentGameContractAddress
//
//        return@withContext txHash
//    }

    /**
     * Calls the gameMaster view function on the current game contract.
     * Uses Player 1's address as the 'from' address for the call.
     * @return The address of the game master, or null if address not set or error.
     */
    suspend fun getGameMaster(): String? = withContext(Dispatchers.IO) {
        val addressToCall = currentGameContractAddress ?: run {
            println("Error: Game contract address is not set. Cannot call getGameMaster.")
            return@withContext null
        }

        val function = Function(
            "gameMaster",
            emptyList(),
            listOf(TypeReference.create(Address::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        println("Calling gameMaster view function on $addressToCall...")
        // Use Player 1's address for the eth_call 'from' field
        val response = web3j.ethCall(
            Transaction.createEthCallTransaction(credentialsPlayer1.address, addressToCall, encodedFunction),
            org.web3j.protocol.core.DefaultBlockParameterName.LATEST
        ).send()

        if (response.hasError()) {
            println("View call failed: ${response.error.message}")
            return@withContext null // Return null on error
        }

        // Decode the output
        return@withContext try {
            val output = FunctionReturnDecoder.decode(response.result, function.outputParameters)
            if (output.isNotEmpty() && output[0] is Address) {
                val gameMasterAddress = (output[0] as Address).value
                println("GameMaster Address: $gameMasterAddress")
                gameMasterAddress
            } else {
                println("Failed to decode gameMaster response or unexpected type.")
                null
            }
        } catch (e: Exception) {
            println("Error decoding gameMaster response: ${e.message}")
            null
        }
    }

    /**
     * Sets the game address for subsequent calls like getGameMaster or makeMove.
     */
    fun setCurrentGameAddress(address: String?) {
        println("Setting current game address to: $address")
        currentGameContractAddress = address
    }

    /**
     * Gets the currently tracked game address.
     */
    fun getCurrentGameAddress(): String? {
        return currentGameContractAddress
    }


    // --- TODO: Add makeMove function ---
    // This function would need to accept which player (1 or 2) is making the move
    // and use the corresponding credentials (credentialsPlayer1 or credentialsPlayer2)
    /*
    suspend fun makeMove(playerIndex: Int, row: Int, col: Int): String = withContext(Dispatchers.IO) {
        val addressToCall = currentGameContractAddress ?: throw IllegalStateException("Game address not set")
        val credentials = if (playerIndex == 1) credentialsPlayer1 else credentialsPlayer2

        val function = Function(
            "makeMove",
            listOf(
                org.web3j.abi.datatypes.generated.Uint8(row.toLong()), // Use Uint8 if that matches ABI
                org.web3j.abi.datatypes.generated.Uint8(col.toLong())
            ),
            emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val transaction = Transaction.createFunctionCallTransaction(
             credentials.address, // Use the selected player's address
             null, // nonce
             gasProvider.gasPrice,
             gasProvider.gasLimit,
             addressToCall, // Target the game contract
             BigInteger.ZERO, // value
             encodedFunction
         )

         println("Sending makeMove transaction as Player $playerIndex (${credentials.address})...")
         val response = web3j.ethSendTransaction(transaction).send()
         // Handle response...
         return@withContext response.transactionHash
    }
    */

}