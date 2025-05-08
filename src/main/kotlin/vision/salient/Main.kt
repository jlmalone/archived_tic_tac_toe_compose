package vision.salient

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import vision.salient.theme.TicTacToeMatrixColors

fun main() = application {

    Blockchain.printDerivedAddresses() // Will show address loaded from file

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tic-Tac-Toe Matrix" // Updated title to reflect theme
    ) {
        // Apply the Matrix theme at the root
        MaterialTheme(colors = TicTacToeMatrixColors) {
            // Add a Surface to ensure the background color is applied correctly to the window
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background // Use the theme's background color
            ) {
                TicTacToeScreen() // Display the main UI
            }
        }
    }
}