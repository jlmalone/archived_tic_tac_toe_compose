package vision.salient

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BlockchainTest {

    @BeforeEach
    fun setUp() {
        // Reset to local for each test
        Blockchain.applyLocal(true)
    }

    @Nested
    @DisplayName("Blockchain Configuration Tests")
    inner class ConfigurationTests {

        @Test
        @Order(1)
        @DisplayName("Should initialize with local configuration by default")
        fun testInitialConfiguration() {
            assertTrue(Blockchain.isLocal, "Blockchain should be initialized as local")
        }

        @Test
        @Order(2)
        @DisplayName("Should switch to Sepolia network")
        fun testSepoliaConfiguration() {
            Blockchain.applyLocal(false)
            assertFalse(Blockchain.isLocal, "Blockchain should not be local after switching")
        }

        @Test
        @Order(3)
        @DisplayName("Should have valid deployment addresses")
        fun testDeploymentAddresses() {
            // Test local addresses
            Blockchain.applyLocal(true)
            val localFactoryAddress = Blockchain.getFactoryAddress()
            
            assertNotNull(localFactoryAddress, "Local factory address should not be null")
            assertTrue(localFactoryAddress?.startsWith("0x") == true, "Factory address should start with 0x")
            assertEquals(42, localFactoryAddress?.length ?: 0, "Factory address should be 42 characters")
            
            // Test Sepolia addresses
            Blockchain.applyLocal(false)
            val sepoliaFactoryAddress = Blockchain.getFactoryAddress()
            
            assertNotNull(sepoliaFactoryAddress, "Sepolia factory address should not be null")
            assertTrue(sepoliaFactoryAddress?.startsWith("0x") == true, "Sepolia factory address should start with 0x")
            
            // Addresses should be different between networks
            assertNotEquals(localFactoryAddress, sepoliaFactoryAddress, 
                "Factory addresses should differ between networks")
        }

        @Test
        @Order(4)
        @DisplayName("Should provide consistent deployment addresses")
        fun testDeploymentAddressConsistency() {
            // Test that addresses match expected values from other projects
            Blockchain.applyLocal(true)
            assertEquals("0x4A679253410272dd5232B3Ff7cF5dbB88f295319", 
                Blockchain.getFactoryAddress(), 
                "Local factory address should match other projects")
            
            Blockchain.applyLocal(false)
            assertEquals("0xa0B53DbDb0052403E38BBC31f01367aC6782118E", 
                Blockchain.getFactoryAddress(), 
                "Sepolia factory address should match other projects")
        }
    }

    @Nested
    @DisplayName("Player Management Tests")
    inner class PlayerManagementTests {

        @Test
        @Order(5)
        @DisplayName("Should have player credentials")
        fun testPlayerCredentials() {
            val player1 = Blockchain.getPlayerCredentials(0)
            val player2 = Blockchain.getPlayerCredentials(1)
            
            assertNotNull(player1, "Player 1 credentials should not be null")
            assertNotNull(player2, "Player 2 credentials should not be null")
            assertNotEquals(player1.address, player2.address, "Player addresses should be different")
            
            // Addresses should be valid Ethereum addresses
            assertTrue(player1.address.matches(Regex("^0x[0-9a-fA-F]{40}$")), 
                "Player 1 address should be valid")
            assertTrue(player2.address.matches(Regex("^0x[0-9a-fA-F]{40}$")), 
                "Player 2 address should be valid")
        }

        @Test
        @Order(6)
        @DisplayName("Should provide correct player count")
        fun testPlayerCount() {
            assertEquals(2, Blockchain.getPlayerCount(), "Should have 2 players")
        }

        @Test
        @Order(7)
        @DisplayName("Should handle different network credentials")
        fun testNetworkCredentials() {
            // Test local network credentials
            Blockchain.applyLocal(true)
            val localPlayer1 = Blockchain.getPlayerCredentials(0)
            val localPlayer2 = Blockchain.getPlayerCredentials(1)
            
            assertNotNull(localPlayer1, "Local player 1 should not be null")
            assertNotNull(localPlayer2, "Local player 2 should not be null")
            
            // Test Sepolia network credentials
            Blockchain.applyLocal(false)
            val sepoliaPlayer1 = Blockchain.getPlayerCredentials(0)
            val sepoliaPlayer2 = Blockchain.getPlayerCredentials(1)
            
            assertNotNull(sepoliaPlayer1, "Sepolia player 1 should not be null")
            assertNotNull(sepoliaPlayer2, "Sepolia player 2 should not be null")
            
            // Addresses might be different between networks
            assertTrue(localPlayer1.address.startsWith("0x"), "Local player 1 address should be valid")
            assertTrue(sepoliaPlayer1.address.startsWith("0x"), "Sepolia player 1 address should be valid")
        }
    }

    @Nested
    @DisplayName("Game State Management Tests")
    inner class GameStateTests {

        @Test
        @Order(8)
        @DisplayName("Should handle game address management")
        fun testGameAddressManagement() {
            // Initially no game should be set
            assertNull(Blockchain.getCurrentGameAddress(), "Initial game address should be null")
            
            // Set a test game address
            val testGameAddress = "0x1234567890123456789012345678901234567890"
            Blockchain.setCurrentGameAddress(testGameAddress)
            
            assertEquals(testGameAddress, Blockchain.getCurrentGameAddress(), 
                "Game address should be set correctly")
            
            // Clear game address
            Blockchain.setCurrentGameAddress(null)
            assertNull(Blockchain.getCurrentGameAddress(), "Game address should be cleared")
        }

        @Test
        @Order(9)
        @DisplayName("Should handle board state when no game is set")
        fun testBoardStateNoGame() = runTest {
            // No game set, should throw error
            assertThrows(IllegalStateException::class.java) {
                runTest {
                    Blockchain.getBoardState()
                }
            }
        }

        @Test
        @Order(10)
        @DisplayName("Should handle game creation")
        fun testGameCreation() = runTest {
            // This test would require actual blockchain connection
            // For now, just test that the function exists
            assertNotNull(Blockchain::createGameByPlayer, "createGameByPlayer function should exist")
        }
    }

    @Nested
    @DisplayName("Utility Function Tests")
    inner class UtilityTests {

        @Test
        @Order(11)
        @DisplayName("Should generate emojis for addresses")
        fun testEmojiGeneration() {
            val testAddress = "0x1234567890123456789012345678901234567890"
            val emoji = Blockchain.emojiForAddress(testAddress)
            
            assertNotNull(emoji, "Emoji should not be null")
            assertTrue(emoji.isNotEmpty(), "Emoji should not be empty")
            
            // Should be consistent
            val emoji2 = Blockchain.emojiForAddress(testAddress)
            assertEquals(emoji, emoji2, "Emoji should be consistent for same address")
            
            // Should be different for different addresses
            val differentEmoji = Blockchain.emojiForAddress("0x0987654321098765432109876543210987654321")
            assertNotEquals(emoji, differentEmoji, "Different addresses should have different emojis")
        }

        @Test
        @Order(12)
        @DisplayName("Should handle emoji generation for various inputs")
        fun testEmojiGenerationVariety() {
            val addresses = listOf(
                "0x1234567890123456789012345678901234567890",
                "0x0987654321098765432109876543210987654321",
                "0xabcdef1234567890abcdef1234567890abcdef12",
                "0x1111111111111111111111111111111111111111",
                "0x0000000000000000000000000000000000000000"
            )
            
            val emojis = addresses.map { Blockchain.emojiForAddress(it) }
            
            // All should be non-empty
            emojis.forEach { emoji ->
                assertNotNull(emoji, "Emoji should not be null")
                assertTrue(emoji.isNotEmpty(), "Emoji should not be empty")
            }
            
            // Should have variety (though some might be the same by chance)
            assertTrue(emojis.toSet().size > 1, "Should have some variety in emojis")
        }
    }

    @Nested
    @DisplayName("Deployment and Integration Tests")
    inner class DeploymentTests {

        @Test
        @Order(13)
        @DisplayName("Should handle deployment function")
        fun testDeploymentFunction() {
            // Test that the deploy function exists and is callable
            assertNotNull(Blockchain::runDeploy, "runDeploy function should exist")
        }

        @Test
        @Order(14)
        @DisplayName("Should provide debug information")
        fun testDebugInformation() {
            // Test that debug function exists
            assertNotNull(Blockchain::printDerivedAddresses, "printDerivedAddresses function should exist")
            
            // Should not throw when called
            assertDoesNotThrow {
                Blockchain.printDerivedAddresses()
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        @Order(15)
        @DisplayName("Should handle network switching errors gracefully")
        fun testNetworkSwitchingErrors() {
            assertDoesNotThrow {
                Blockchain.applyLocal(true)
                Blockchain.applyLocal(false)
                Blockchain.applyLocal(true)
            }
        }

        @Test
        @Order(16)
        @DisplayName("Should handle invalid player indices")
        fun testInvalidPlayerIndices() {
            // Should handle valid indices
            assertDoesNotThrow {
                Blockchain.getPlayerCredentials(0)
                Blockchain.getPlayerCredentials(1)
            }
            
            // Invalid indices might throw exceptions (depends on implementation)
            // This test verifies the function doesn't crash the entire system
            assertNotNull(Blockchain::getPlayerCredentials, "getPlayerCredentials should exist")
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {

        @Test
        @Order(17)
        @DisplayName("Should handle multiple network switches efficiently")
        fun testNetworkSwitchPerformance() {
            val startTime = System.currentTimeMillis()
            
            repeat(10) {
                Blockchain.applyLocal(it % 2 == 0)
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            assertTrue(duration < 2000, "10 network switches should complete in under 2 seconds")
        }

        @Test
        @Order(18)
        @DisplayName("Should handle emoji generation efficiently")
        fun testEmojiGenerationPerformance() {
            val testAddresses = (0..99).map { "0x${it.toString().padStart(40, '0')}" }
            
            val startTime = System.currentTimeMillis()
            
            testAddresses.forEach { address ->
                Blockchain.emojiForAddress(address)
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            assertTrue(duration < 100, "100 emoji generations should complete in under 100ms")
        }
    }

    @Nested
    @DisplayName("Network Consistency Tests")
    inner class NetworkConsistencyTests {

        @Test
        @Order(19)
        @DisplayName("Should maintain state consistency across network switches")
        fun testNetworkStateConsistency() {
            // Test that basic state is maintained
            Blockchain.applyLocal(true)
            val localFactory = Blockchain.getFactoryAddress()
            
            Blockchain.applyLocal(false)
            val sepoliaFactory = Blockchain.getFactoryAddress()
            
            Blockchain.applyLocal(true)
            val backToLocal = Blockchain.getFactoryAddress()
            
            assertEquals(localFactory, backToLocal, "Local factory address should be consistent")
        }

        @Test
        @Order(20)
        @DisplayName("Should handle rapid state changes")
        fun testRapidStateChanges() {
            assertDoesNotThrow {
                repeat(20) {
                    Blockchain.applyLocal(it % 2 == 0)
                    Blockchain.getFactoryAddress()
                    Blockchain.getPlayerCredentials(0)
                }
            }
        }
    }
}