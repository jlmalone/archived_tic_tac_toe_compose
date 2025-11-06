package vision.salient.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for Matrix theme colors
 */
class ThemeTest {

    @Test
    @DisplayName("MatrixGreen should be pure green (#00FF00)")
    fun `MatrixGreen is pure green`() {
        // Then
        assertEquals(Color(0xFF00FF00), MatrixGreen)
        assertEquals(0xFF, MatrixGreen.alpha * 255, 1.0, "Alpha should be 1.0")
        assertEquals(0x00, MatrixGreen.red * 255, 1.0, "Red should be 0")
        assertEquals(0xFF, MatrixGreen.green * 255, 1.0, "Green should be 255")
        assertEquals(0x00, MatrixGreen.blue * 255, 1.0, "Blue should be 0")
    }

    @Test
    @DisplayName("MatrixBlack should be pure black (#000000)")
    fun `MatrixBlack is pure black`() {
        // Then
        assertEquals(Color(0xFF000000), MatrixBlack)
        assertEquals(0xFF, MatrixBlack.alpha * 255, 1.0, "Alpha should be 1.0")
        assertEquals(0x00, MatrixBlack.red * 255, 1.0, "Red should be 0")
        assertEquals(0x00, MatrixBlack.green * 255, 1.0, "Green should be 0")
        assertEquals(0x00, MatrixBlack.blue * 255, 1.0, "Blue should be 0")
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct primary color")
    fun `Theme has MatrixGreen as primary`() {
        // Then
        assertEquals(MatrixGreen, TicTacToeMatrixColors.primary)
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct background color")
    fun `Theme has MatrixBlack as background`() {
        // Then
        assertEquals(MatrixBlack, TicTacToeMatrixColors.background)
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct surface color")
    fun `Theme has MatrixBlack as surface`() {
        // Then
        assertEquals(MatrixBlack, TicTacToeMatrixColors.surface)
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct onPrimary color")
    fun `Theme has MatrixBlack as onPrimary`() {
        // Then
        assertEquals(MatrixBlack, TicTacToeMatrixColors.onPrimary)
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct onBackground color")
    fun `Theme has MatrixGreen as onBackground`() {
        // Then
        assertEquals(MatrixGreen, TicTacToeMatrixColors.onBackground)
    }

    @Test
    @DisplayName("TicTacToeMatrixColors should have correct onSurface color")
    fun `Theme has MatrixGreen as onSurface`() {
        // Then
        assertEquals(MatrixGreen, TicTacToeMatrixColors.onSurface)
    }

    @Test
    @DisplayName("Theme should be a dark color scheme")
    fun `Theme is dark`() {
        // A dark theme typically has a dark background
        // MatrixBlack is pure black (0, 0, 0)
        assertTrue(TicTacToeMatrixColors.isLight.not(), "Theme should be dark")
    }
}
