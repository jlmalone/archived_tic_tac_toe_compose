package vision.salient

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    // Optional: Print addresses on startup for debugging
    Blockchain.printDerivedAddresses() // Will show address loaded from file

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tic-Tac-Toe Compose Web3 (Manual)" // Updated title
    ) {
        MaterialTheme {
            TicTacToeScreen() // Display the main UI
        }
    }
}