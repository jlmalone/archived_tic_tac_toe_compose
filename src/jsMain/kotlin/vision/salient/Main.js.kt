
package vision.salient

import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    println("Starting JS Hello World") // Simple console log
    renderComposable(rootElementId = "root") {
        Div { // Use basic HTML elements via Compose HTML
            Text("Hello Web!") // Compose HTML TextNode
        }
        println("JS Hello World Rendered")
    }
}