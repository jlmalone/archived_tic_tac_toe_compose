import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.DefaultGasProvider
//import io.tictactoe.contracts.TicTacToeFactory



fun main() = application {

    Window(onCloseRequest = ::exitApplication) {
        TicTacToeScreen()
    }
//
//    val PRIVATE_KEY = System.getenv("PRIVATE_KEY") // Don't hardcode for now
//    val RPC_URL = System.getenv("POLYGON_RPC_URL") ?: "https://rpc-mumbai.maticvigil.com"
//
//    val web3 = Web3j.build(HttpService(RPC_URL))
//    val creds = Credentials.create(PRIVATE_KEY)
//    val gasProvider = DefaultGasProvider()
//
//    val factory = TicTacToeFactory.load(
//        "0xYOUR_FACTORY_CONTRACT_ADDRESS",
//        web3,
//        creds,
//        gasProvider
//    )
//
//    Window(onCloseRequest = ::exitApplication, title = "Tic-Tac-Toe") {
//        MaterialTheme {
//            TicTacToeBoard()
//        }
//    }
}

@Composable
fun TicTacToeBoard() {
    var board by remember { mutableStateOf(Array(3) { Array(3) { "" } }) }
    var currentPlayer by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Tic-Tac-Toe", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(16.dp))

        for (i in 0..2) {
            Row {
                for (j in 0..2) {
                    Button(
                        onClick = {
                            if (board[i][j] == "" && winner == null) {
                                board[i][j] = currentPlayer
                                currentPlayer = if (currentPlayer == "X") "O" else "X"
                                winner = checkWinner(board)
                            }
                        },
                        modifier = Modifier.size(96.dp).padding(4.dp),
                        enabled = board[i][j] == "" && winner == null
                    ) {
                        Text(board[i][j])
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            winner != null -> Text("Winner: $winner!", style = MaterialTheme.typography.h5)
            board.all { row -> row.all { it != "" } } -> Text("It's a draw!", style = MaterialTheme.typography.h5)
            else -> Text("Current Player: $currentPlayer", style = MaterialTheme.typography.h6)
        }
    }
}

fun checkWinner(board: Array<Array<String>>): String? {
    val lines = listOf(
        listOf(board[0][0], board[0][1], board[0][2]),
        listOf(board[1][0], board[1][1], board[1][2]),
        listOf(board[2][0], board[2][1], board[2][2]),
        listOf(board[0][0], board[1][0], board[2][0]),
        listOf(board[0][1], board[1][1], board[2][1]),
        listOf(board[0][2], board[1][2], board[2][2]),
        listOf(board[0][0], board[1][1], board[2][2]),
        listOf(board[0][2], board[1][1], board[2][0])
    )
    return lines.firstOrNull { it.all { cell -> cell == "X" } }?.first()
        ?: lines.firstOrNull { it.all { cell -> cell == "O" } }?.first()
}
