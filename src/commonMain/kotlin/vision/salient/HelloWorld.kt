package vision.salient

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun HelloWorld() {
    MaterialTheme {
        Text("👋 Hello, Tic-Tac-Toe Compose MPP!", modifier = androidx.compose.ui.Modifier.padding(24.dp))
    }
}
