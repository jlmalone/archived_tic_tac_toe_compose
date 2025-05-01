package vision.salient

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

fun main() = application {
    println("Starting JVM Hello World") // Simple console log
    Window(onCloseRequest = ::exitApplication, title = "KMP Desktop Hello") {
        MaterialTheme { // Keep theme for basic styling
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Hello Desktop!")
            }
        }
    }
    println("JVM Hello World Window Shown")
}


//
//import androidx.compose.material.MaterialTheme
//import androidx.compose.ui.window.Window
//import androidx.compose.ui.window.application
//
//fun main() = application {
//    // Optional: Print addresses on startup for debugging
//    Blockchain.printDerivedAddresses() // Will show address loaded from file
//
//    Window(
//        onCloseRequest = ::exitApplication,
//        title = "Tic-Tac-Toe Compose Web3 (Manual)" // Updated title
//    ) {
//        MaterialTheme {
//            TicTacToeScreen() // Display the main UI
//        }
//    }
//}

