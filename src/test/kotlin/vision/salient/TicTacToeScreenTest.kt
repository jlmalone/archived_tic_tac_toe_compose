package vision.salient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TicTacToeScreenTest {

    @Test
    fun `test ZERO_ADDRESS constant is valid`() {
        assertEquals("0x0000000000000000000000000000000000000000", ZERO_ADDRESS)
        assertEquals(42, ZERO_ADDRESS.length) // 0x + 40 hex chars
        assertTrue(ZERO_ADDRESS.startsWith("0x"))
    }

    @Test
    fun `test ZERO_ADDRESS is all zeros`() {
        val addressPart = ZERO_ADDRESS.substring(2) // Remove "0x"
        assertTrue(addressPart.all { it == '0' })
    }
}
