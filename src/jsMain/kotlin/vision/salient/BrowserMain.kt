package vision.salient

import org.jetbrains.compose.web.renderComposable
import org.jetbrains.compose.web.dom.Div

fun main() {
    renderComposable(rootElementId = "root") {
        Div { HelloWorld() }
    }
}
