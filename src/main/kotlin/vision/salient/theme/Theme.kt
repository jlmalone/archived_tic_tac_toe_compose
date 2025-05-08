
package vision.salient.theme

import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

val MatrixGreen = Color(0xFF00FF00)
val MatrixBlack = Color(0xFF000000)

val TicTacToeMatrixColors = darkColors(
    primary = MatrixGreen,
    primaryVariant = MatrixGreen, // Can be a darker shade if desired, but same for this theme
    secondary = MatrixGreen,
    secondaryVariant = MatrixGreen,
    background = MatrixBlack,
    surface = MatrixBlack, // Background for components like Card, TextField
    error = Color(0xFFCF6679), // A standard dark theme error color
    onPrimary = MatrixBlack,   // Text on primary-colored surfaces (e.g., buttons)
    onSecondary = MatrixBlack, // Text on secondary-colored surfaces
    onBackground = MatrixGreen, // Main text color on app background
    onSurface = MatrixGreen,   // Text on surfaced components (e.g., text in cards)
    onError = MatrixBlack      // Text on error-colored surfaces
)