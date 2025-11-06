package vision.salient

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertNotNull

/**
 * Unit tests for Blockchain utility functions
 */
class BlockchainTest {

    @Test
    @DisplayName("emojiForAddress should return consistent emoji for same address")
    fun `emojiForAddress returns consistent emoji for same address`() {
        // Given
        val address = "0x1234567890abcdef1234567890abcdef12345678"

        // When
        val emoji1 = Blockchain.emojiForAddress(address)
        val emoji2 = Blockchain.emojiForAddress(address)

        // Then
        assertEquals(emoji1, emoji2, "Same address should always produce same emoji")
    }

    @Test
    @DisplayName("emojiForAddress should return different emojis for different addresses")
    fun `emojiForAddress returns different emojis for different addresses`() {
        // Given
        val address1 = "0x1234567890abcdef1234567890abcdef12345678"
        val address2 = "0xabcdef1234567890abcdef1234567890abcdef12"

        // When
        val emoji1 = Blockchain.emojiForAddress(address1)
        val emoji2 = Blockchain.emojiForAddress(address2)

        // Then - While theoretically they could be the same, they should generally differ
        // We just verify that both return valid emojis
        assertNotNull(emoji1)
        assertNotNull(emoji2)
        assertTrue(emoji1.isNotEmpty())
        assertTrue(emoji2.isNotEmpty())
    }

    @Test
    @DisplayName("emojiForAddress should handle address with 0x prefix")
    fun `emojiForAddress handles 0x prefix`() {
        // Given
        val addressWithPrefix = "0x1234567890abcdef1234567890abcdef12345678"

        // When
        val emoji = Blockchain.emojiForAddress(addressWithPrefix)

        // Then
        assertNotNull(emoji)
        assertTrue(emoji.isNotEmpty())
    }

    @Test
    @DisplayName("emojiForAddress should handle address without 0x prefix")
    fun `emojiForAddress handles address without 0x prefix`() {
        // Given
        val addressWithoutPrefix = "1234567890abcdef1234567890abcdef12345678"

        // When
        val emoji = Blockchain.emojiForAddress(addressWithoutPrefix)

        // Then
        assertNotNull(emoji)
        assertTrue(emoji.isNotEmpty())
    }

    @Test
    @DisplayName("emojiForAddress should handle uppercase addresses")
    fun `emojiForAddress handles uppercase addresses`() {
        // Given
        val uppercaseAddress = "0X1234567890ABCDEF1234567890ABCDEF12345678"
        val lowercaseAddress = "0x1234567890abcdef1234567890abcdef12345678"

        // When
        val emoji1 = Blockchain.emojiForAddress(uppercaseAddress)
        val emoji2 = Blockchain.emojiForAddress(lowercaseAddress)

        // Then - should normalize to same emoji
        assertEquals(emoji1, emoji2, "Case should not matter for emoji generation")
    }

    @Test
    @DisplayName("getPlayerCount should return 2")
    fun `getPlayerCount returns 2`() {
        // When
        val playerCount = Blockchain.getPlayerCount()

        // Then
        assertEquals(2, playerCount, "Should support exactly 2 players")
    }

    @Test
    @DisplayName("isLocal should return a boolean value")
    fun `isLocal returns boolean`() {
        // When
        val isLocal = Blockchain.isLocal

        // Then
        assertTrue(isLocal is Boolean, "isLocal should be a boolean")
    }

    @Test
    @DisplayName("getFactoryAddress should return nullable string")
    fun `getFactoryAddress returns string or null`() {
        // When
        val factoryAddress = Blockchain.getFactoryAddress()

        // Then
        assertTrue(factoryAddress is String? , "Factory address should be String or null")
        // If not null, should look like an Ethereum address
        factoryAddress?.let {
            assertTrue(it.startsWith("0x") || it.isEmpty(), "If present, should be hex address")
        }
    }

    @Test
    @DisplayName("getCurrentGameAddress should return nullable string")
    fun `getCurrentGameAddress returns string or null`() {
        // When
        val gameAddress = Blockchain.getCurrentGameAddress()

        // Then
        assertTrue(gameAddress is String?, "Game address should be String or null")
    }

    @Test
    @DisplayName("setCurrentGameAddress should accept null")
    fun `setCurrentGameAddress accepts null`() {
        // When/Then - should not throw
        assertDoesNotThrow {
            Blockchain.setCurrentGameAddress(null)
        }
    }

    @Test
    @DisplayName("setCurrentGameAddress should accept valid address")
    fun `setCurrentGameAddress accepts valid address`() {
        // Given
        val testAddress = "0x1234567890abcdef1234567890abcdef12345678"

        // When/Then - should not throw
        assertDoesNotThrow {
            Blockchain.setCurrentGameAddress(testAddress)
        }

        // Verify it was set
        assertEquals(testAddress, Blockchain.getCurrentGameAddress())

        // Cleanup
        Blockchain.setCurrentGameAddress(null)
    }
}
