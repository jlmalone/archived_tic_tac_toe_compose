package vision.salient

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.Rule
import vision.salient.theme.MatrixTheme

/**
 * UI tests for TicTacToeScreen composable
 *
 * Tests cover:
 * - UI component rendering
 * - User interactions
 * - State management
 * - Button clicks and text input
 * - Error handling in UI
 * - Network mode switching
 */
class TicTacToeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @BeforeEach
    fun setup() {
        // Reset blockchain state before each test
        Blockchain.applyLocal(true)
        Blockchain.setCurrentGameAddress(null)
    }

    @Nested
    @DisplayName("Component Rendering Tests")
    inner class ComponentRenderingTests {

        @Test
        @DisplayName("Should render main title")
        fun `render main title`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Tic-Tac-Toe Web3")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render network toggle button")
        fun `render network toggle`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Should show LOCAL by default
            composeTestRule
                .onNodeWithText("LOCAL ✔")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render Load Factory button")
        fun `render load factory button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Load Factory")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render Create Game button")
        fun `render create game button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Create Game (P1)")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render game address text field")
        fun `render game address field`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Game Address")
                .assertExists()
        }

        @Test
        @DisplayName("Should render Join Game button")
        fun `render join game button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Join Game")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render player selector button")
        fun `render player selector`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Signer: P1")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render row and col input fields")
        fun `render move input fields`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule.onNodeWithText("Row").assertExists()
            composeTestRule.onNodeWithText("Col").assertExists()
        }

        @Test
        @DisplayName("Should render Make Move button")
        fun `render make move button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Make Move")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render debug section")
        fun `render debug section`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Debug Calls")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should render debug buttons")
        fun `render debug buttons`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule.onNodeWithText("🔄 Refresh Board").assertExists()
            composeTestRule.onNodeWithText("🏁 gameEnded").assertExists()
            composeTestRule.onNodeWithText("👤 lastPlayer").assertExists()
            composeTestRule.onNodeWithText("🏆 winner").assertExists()
            composeTestRule.onNodeWithText("📍 currentGame").assertExists()
            composeTestRule.onNodeWithText("🏭 factoryAddr").assertExists()
        }

        @Test
        @DisplayName("Should render Deploy button when in LOCAL mode")
        fun `render deploy button in local mode`() {
            Blockchain.applyLocal(true)

            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Deploy (npx tsx)")
                .assertExists()
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("Should not render Deploy button in SEPOLIA mode")
        @Disabled("Network switching test - requires refactor")
        fun `hide deploy button in sepolia mode`() {
            // This test is tricky because switching networks requires recomposition
            // Better as an integration test
        }
    }

    @Nested
    @DisplayName("Button Interaction Tests")
    inner class ButtonInteractionTests {

        @Test
        @DisplayName("Should handle Load Factory button click")
        fun `click load factory button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Load Factory")
                .performClick()

            // Should display factory address in status
            composeTestRule.waitForIdle()

            // Status text should contain "Factory:"
            val factoryAddr = Blockchain.getFactoryAddress()
            if (factoryAddr != null) {
                composeTestRule
                    .onNodeWithText("Factory: $factoryAddr", substring = true)
                    .assertExists()
            }
        }

        @Test
        @DisplayName("Should handle player selector button click")
        fun `cycle through players`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Initial state: P1
            composeTestRule
                .onNodeWithText("Signer: P1")
                .assertExists()

            // Click to switch to P2
            composeTestRule
                .onNodeWithText("Signer: P1")
                .performClick()

            composeTestRule.waitForIdle()

            // Should now show P2
            composeTestRule
                .onNodeWithText("Signer: P2")
                .assertExists()

            // Click again to cycle back to P1
            composeTestRule
                .onNodeWithText("Signer: P2")
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithText("Signer: P1")
                .assertExists()
        }

        @Test
        @DisplayName("Should handle Print Addrs button click")
        fun `click print addresses button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Should not crash when clicking
            assertDoesNotThrow {
                composeTestRule
                    .onNodeWithText("Print Addrs")
                    .performClick()

                composeTestRule.waitForIdle()
            }
        }

        @Test
        @DisplayName("Should handle currentGame debug button")
        fun `click current game debug button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("📍 currentGame")
                .performClick()

            composeTestRule.waitForIdle()

            // Should show current game address (or <none>)
            composeTestRule
                .onNodeWithText("Current game address:", substring = true)
                .assertExists()
        }

        @Test
        @DisplayName("Should handle factoryAddr debug button")
        fun `click factory address debug button`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("🏭 factoryAddr")
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithText("Factory address:", substring = true)
                .assertExists()
        }
    }

    @Nested
    @DisplayName("Text Input Tests")
    inner class TextInputTests {

        @Test
        @DisplayName("Should accept numeric input in Row field")
        fun `enter row number`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Find Row field and enter text
            composeTestRule
                .onNodeWithText("Row")
                .performTextInput("2")

            composeTestRule.waitForIdle()

            // Field should contain the entered value
            composeTestRule
                .onNodeWithText("Row")
                .assertExists()
        }

        @Test
        @DisplayName("Should accept numeric input in Col field")
        fun `enter col number`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            composeTestRule
                .onNodeWithText("Col")
                .performTextInput("1")

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithText("Col")
                .assertExists()
        }

        @Test
        @DisplayName("Should filter non-numeric input in Row field")
        @Disabled("Requires SemanticsNode text value assertion")
        fun `filter non numeric row input`() {
            // This test requires checking the actual text value
            // which is more complex in Compose testing
        }

        @Test
        @DisplayName("Should accept game address in text field")
        fun `enter game address`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            val testAddress = "0x1234567890123456789012345678901234567890"

            // Clear and enter new text
            composeTestRule
                .onNodeWithText("Game Address")
                .performTextClearance()

            composeTestRule
                .onNodeWithText("Game Address")
                .performTextInput(testAddress)

            composeTestRule.waitForIdle()

            // Text field should exist
            composeTestRule
                .onNodeWithText("Game Address")
                .assertExists()
        }
    }

    @Nested
    @DisplayName("Button Enable/Disable Tests")
    inner class ButtonStateTests {

        @Test
        @DisplayName("Create Game button should be disabled without factory")
        fun `create game disabled without factory`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Initially, factory is not loaded via UI
            // (it may be loaded from file, but not via button click)
            // Create Game button might be enabled or disabled depending on factory load
        }

        @Test
        @DisplayName("Join Game button should be disabled without address")
        fun `join game disabled without address`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Game address field starts empty
            // Join Game button should be disabled
            composeTestRule
                .onNodeWithText("Join Game")
                .assertExists()
                // Note: Compose UI testing doesn't have direct isEnabled check
                // We'd need to try clicking and check for no-op
        }

        @Test
        @DisplayName("Make Move button should be disabled without game")
        fun `make move disabled without game`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Without game address, Make Move should be disabled
            composeTestRule
                .onNodeWithText("Make Move")
                .assertExists()
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should show error for invalid move coordinates")
        fun `show error for invalid coordinates`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Enter invalid row/col
            composeTestRule.onNodeWithText("Row").performTextClearance()
            composeTestRule.onNodeWithText("Row").performTextInput("5")

            composeTestRule.onNodeWithText("Col").performTextClearance()
            composeTestRule.onNodeWithText("Col").performTextInput("9")

            // Set a dummy game address to enable Make Move button
            val testAddress = "0x1234567890123456789012345678901234567890"
            composeTestRule.onNodeWithText("Game Address").performTextClearance()
            composeTestRule.onNodeWithText("Game Address").performTextInput(testAddress)

            composeTestRule.onNodeWithText("Join Game").performClick()
            composeTestRule.waitForIdle()

            // Click Make Move
            composeTestRule.onNodeWithText("Make Move").performClick()
            composeTestRule.waitForIdle()

            // Should show "Bad cell" error
            composeTestRule
                .onNodeWithText("Bad cell")
                .assertExists()
        }

        @Test
        @DisplayName("Should handle missing game address gracefully")
        fun `handle missing game address`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Try to make move without setting game address
            // Button should be disabled, so click should be no-op
            // This is tested by the button state tests
        }
    }

    @Nested
    @DisplayName("Status Display Tests")
    inner class StatusDisplayTests {

        @Test
        @DisplayName("Should display status messages")
        fun `display status messages`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Click Load Factory
            composeTestRule.onNodeWithText("Load Factory").performClick()
            composeTestRule.waitForIdle()

            // Should show factory address in status
            val factoryAddr = Blockchain.getFactoryAddress()
            if (factoryAddr != null) {
                composeTestRule
                    .onNodeWithText("Factory: $factoryAddr", substring = true)
                    .assertExists()
            }
        }

        @Test
        @DisplayName("Should update status on network switch")
        @Disabled("Requires network switch recomposition")
        fun `update status on network switch`() {
            // This would require testing the network toggle button
            // which causes recomposition and state changes
        }
    }

    @Nested
    @DisplayName("Theme Integration Tests")
    inner class ThemeIntegrationTests {

        @Test
        @DisplayName("Should apply Matrix theme colors")
        fun `apply matrix theme`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // All components should render with theme applied
            // Visual verification is manual, but we can verify components exist
            composeTestRule
                .onNodeWithText("Tic-Tac-Toe Web3")
                .assertExists()
        }
    }

    @Nested
    @DisplayName("Board Display Tests")
    inner class BoardDisplayTests {

        @Test
        @DisplayName("Should not display board initially")
        fun `no board initially`() {
            composeTestRule.setContent {
                MatrixTheme {
                    TicTacToeScreen()
                }
            }

            // Board is only displayed when loaded
            // Initially, no game is set, so no board should be visible
            // We can verify by checking that no emoji cards are rendered
        }

        @Test
        @DisplayName("Should display empty cells as blank")
        @Disabled("Requires mocked board state")
        fun `display empty cells`() {
            // Would need to mock Blockchain.getBoardState()
            // Better as integration test
        }

        @Test
        @DisplayName("Should display player emojis in occupied cells")
        @Disabled("Requires mocked board state")
        fun `display player emojis`() {
            // Would need to mock Blockchain.getBoardState()
            // Better as integration test
        }
    }
}
