package vision.salient

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for TicTacToeScreen constants and utilities
 */
class TicTacToeScreenTest {

    @Test
    @DisplayName("ZERO_ADDRESS should be the correct Ethereum zero address")
    fun `ZERO_ADDRESS is correct`() {
        // Then
        assertEquals("0x0000000000000000000000000000000000000000", ZERO_ADDRESS)
        assertEquals(42, ZERO_ADDRESS.length, "Should be 0x + 40 hex chars")
        assertTrue(ZERO_ADDRESS.startsWith("0x"), "Should start with 0x")
        assertTrue(ZERO_ADDRESS.substring(2).all { it == '0' }, "Should be all zeros after 0x")
    }

    @Test
    @DisplayName("ZERO_ADDRESS should be lowercase")
    fun `ZERO_ADDRESS is lowercase`() {
        // Then
        assertEquals(ZERO_ADDRESS, ZERO_ADDRESS.lowercase(), "Should be lowercase")
    }
}
