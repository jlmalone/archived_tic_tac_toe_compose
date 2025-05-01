package vision.salient

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import sun.jvm.hotspot.HelloWorld

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Hello Desktop") {
        HelloWorld()
    }
}
