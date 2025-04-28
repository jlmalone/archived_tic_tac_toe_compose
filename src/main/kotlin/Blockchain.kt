import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.Contract
import java.io.File
import java.math.BigInteger

object Blockchain {

    private val web3j: Web3j by lazy {
        // Replace with your Web3j instance, like Alchemy, Infura, local RPC etc.
        Web3j.build(org.web3j.protocol.http.HttpService("https://your-rpc-url"))
    }

    private val credentials: Credentials by lazy {
        // Load your private key here
        Credentials.create("YOUR_PRIVATE_KEY_HERE")
    }

    private val contractAddress = "0xYOUR_DEPLOYED_CONTRACT_ADDRESS"

    private val abi: String by lazy {
        File("/absolute/path/to/MultiPlayerTicTacToe.abi").readText()
    }

    private val transactionManager by lazy {
        RawTransactionManager(web3j, credentials)
    }

    private val gasProvider by lazy {
        DefaultGasProvider()
    }

    fun createGame() {
        val function = org.web3j.abi.datatypes.Function(
            "createGame",
            listOf(),
            listOf(org.web3j.abi.TypeReference.create(org.web3j.abi.datatypes.Address::class.java))
        )

        val encodedFunction = org.web3j.abi.FunctionEncoder.encode(function)

        val transaction = Transaction.createFunctionCallTransaction(
            credentials.address,
            null,
            gasProvider.gasPrice,
            gasProvider.gasLimit,
            contractAddress,
            BigInteger.ZERO,
            encodedFunction
        )

        val txHash = web3j.ethSendTransaction(transaction).send().transactionHash
        println("Transaction hash: $txHash")

        // Optional: wait for receipt
        val receipt: TransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.orElseThrow {
            RuntimeException("Transaction receipt not found")
        }
        println("Transaction successful: ${receipt.isStatusOK}")
    }
}
