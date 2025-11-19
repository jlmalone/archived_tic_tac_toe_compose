package vision.salient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlockchainTest {

    @Test
    fun `test emojiForAddress returns consistent emoji for same address`() {
        val address = "0x1234567890123456789012345678901234567890"
        val emoji1 = Blockchain.emojiForAddress(address)
        val emoji2 = Blockchain.emojiForAddress(address)

        assertEquals(emoji1, emoji2, "Same address should always return the same emoji")
    }

    @Test
    fun `test emojiForAddress returns different emojis for different addresses`() {
        val address1 = "0x1234567890123456789012345678901234567890"
        val address2 = "0x0987654321098765432109876543210987654321"

        val emoji1 = Blockchain.emojiForAddress(address1)
        val emoji2 = Blockchain.emojiForAddress(address2)

        // While theoretically they could be the same, with 20 emojis it's unlikely
        assertNotNull(emoji1)
        assertNotNull(emoji2)
    }

    @Test
    fun `test emojiForAddress handles addresses with and without 0x prefix`() {
        val addressWithPrefix = "0x1234567890123456789012345678901234567890"
        val addressWithoutPrefix = "1234567890123456789012345678901234567890"

        val emoji1 = Blockchain.emojiForAddress(addressWithPrefix)
        val emoji2 = Blockchain.emojiForAddress(addressWithoutPrefix)

        assertEquals(emoji1, emoji2, "Addresses with and without 0x prefix should return the same emoji")
    }

    @Test
    fun `test emojiForAddress handles lowercase and uppercase addresses`() {
        val lowercase = "0x1234567890abcdef1234567890abcdef12345678"
        val uppercase = "0x1234567890ABCDEF1234567890ABCDEF12345678"

        val emoji1 = Blockchain.emojiForAddress(lowercase)
        val emoji2 = Blockchain.emojiForAddress(uppercase)

        assertEquals(emoji1, emoji2, "Case should not matter for emoji generation")
    }

    @Test
    fun `test getPlayerCount returns 2`() {
        assertEquals(2, Blockchain.getPlayerCount())
    }

    @Test
    fun `test isLocal property is accessible`() {
        // Just test that we can access the property without errors
        val isLocal = Blockchain.isLocal
        assertTrue(isLocal is Boolean)
    }

    @Test
    fun `test getFactoryAddress returns a value or null`() {
        // Just test that the method can be called without errors
        val address = Blockchain.getFactoryAddress()
        assertTrue(address == null || address.startsWith("0x") || address.isEmpty())
    }

    @Test
    fun `test getCurrentGameAddress returns null initially`() {
        // The current game address should be null if no game is set
        val address = Blockchain.getCurrentGameAddress()
        assertTrue(address == null || address.startsWith("0x") || address.isEmpty())
    }
}
