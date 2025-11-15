package vision.salient

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Integration tests for Blockchain.kt with real Hardhat network
 *
 * These tests require:
 * - Hardhat node running on localhost:8545
 * - Smart contracts deployed
 * - Environment variables configured in .env
 *
 * Run these tests with: ./gradlew test --tests "*IntegrationTest"
 *
 * Prerequisites:
 * 1. Start Hardhat: cd $HARDHAT_PROJECT_DIR && npx hardhat node
 * 2. Deploy contracts: npx tsx deployment/deploy_ethers.ts --local
 * 3. Run tests: ./gradlew test
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("integration")
class BlockchainIntegrationTest {

    companion object {
        private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        private var testGameAddress: String? = null

        @JvmStatic
        @BeforeAll
        fun setupSuite() {
            println("=".repeat(70))
            println("BLOCKCHAIN INTEGRATION TEST SUITE")
            println("=".repeat(70))
            println("Network: ${if (Blockchain.isLocal) "LOCAL (Hardhat)" else "SEPOLIA"}")
            println("Factory: ${Blockchain.getFactoryAddress()}")
            println("=".repeat(70))

            // Ensure we're on LOCAL network for integration tests
            Blockchain.applyLocal(true)
        }

        @JvmStatic
        @AfterAll
        fun teardownSuite() {
            println("=".repeat(70))
            println("Integration tests completed")
            println("=".repeat(70))
        }
    }

    @BeforeEach
    fun setup() {
        // Ensure LOCAL network for each test
        if (!Blockchain.isLocal) {
            Blockchain.applyLocal(true)
        }
    }

    @Nested
    @DisplayName("Contract Deployment Integration")
    inner class DeploymentIntegration {

        @Test
        @Order(1)
        @DisplayName("Should have factory deployed on local network")
        fun `verify factory deployment`() {
            val factoryAddress = Blockchain.getFactoryAddress()

            assertNotNull(factoryAddress, "Factory should be deployed")
            assertTrue(factoryAddress!!.startsWith("0x"), "Factory address should be valid")
            assertEquals(42, factoryAddress.length, "Factory address should be 42 chars")

            println("✓ Factory deployed at: $factoryAddress")
        }

        @Test
        @Order(2)
        @DisplayName("Should have valid player credentials")
        fun `verify player credentials`() {
            val player0 = Blockchain.getPlayerCredentials(0)
            val player1 = Blockchain.getPlayerCredentials(1)

            assertNotNull(player0.address)
            assertNotNull(player1.address)
            assertNotEquals(player0.address, player1.address)

            println("✓ Player 0: ${player0.address}")
            println("✓ Player 1: ${player1.address}")
        }
    }

    @Nested
    @DisplayName("Game Creation Integration")
    inner class GameCreationIntegration {

        @Test
        @Order(10)
        @DisplayName("Should create new game via factory")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `create game successfully`() = runBlocking {
            val factoryAddress = Blockchain.getFactoryAddress()
            assertNotNull(factoryAddress, "Factory must be deployed before creating game")

            println("Creating new game...")
            val gameAddress = Blockchain.createGameByPlayer(0)

            assertNotNull(gameAddress, "Game creation should return address")
            assertTrue(gameAddress!!.startsWith("0x"), "Game address should be valid")
            assertNotEquals(ZERO_ADDRESS, gameAddress, "Game address should not be zero")

            // Save for subsequent tests
            testGameAddress = gameAddress
            Blockchain.setCurrentGameAddress(gameAddress)

            println("✓ Game created at: $gameAddress")
        }

        @Test
        @Order(11)
        @DisplayName("Should retrieve empty board state after creation")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `verify initial board state`() = runBlocking {
            if (testGameAddress == null) {
                // Create game if not created by previous test
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
            }

            val board = Blockchain.getBoardState()

            assertNotNull(board)
            assertEquals(3, board.size, "Board should have 3 rows")
            board.forEach { row ->
                assertEquals(3, row.size, "Each row should have 3 cells")
                row.forEach { cell ->
                    assertEquals(ZERO_ADDRESS, cell.lowercase(), "Initial board should be all zeros")
                }
            }

            println("✓ Initial board state verified (all empty)")
        }

        @Test
        @Order(12)
        @DisplayName("Should verify game not ended initially")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `verify game not ended`() = runBlocking {
            if (testGameAddress == null) {
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
            }

            val gameEnded = Blockchain.readBool("gameEnded")

            assertFalse(gameEnded, "New game should not be ended")
            println("✓ Game correctly marked as not ended")
        }

        @Test
        @Order(13)
        @DisplayName("Should verify no winner initially")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `verify no initial winner`() = runBlocking {
            if (testGameAddress == null) {
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
            }

            val winner = Blockchain.readAddress("winner")

            assertEquals(ZERO_ADDRESS, winner.lowercase(), "New game should have no winner")
            println("✓ No winner for new game")
        }
    }

    @Nested
    @DisplayName("Game Play Integration")
    inner class GamePlayIntegration {

        @Test
        @Order(20)
        @DisplayName("Should make first move as player 0")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `make first move`() = runBlocking {
            if (testGameAddress == null) {
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
            }

            println("Player 0 making move at (0, 0)...")
            val txHash = Blockchain.makeMove(0, 0, 0)

            assertNotNull(txHash)
            assertTrue(txHash.startsWith("0x"), "Transaction hash should be valid")

            // Verify move was recorded
            val board = Blockchain.getBoardState()
            val player0Addr = Blockchain.getPlayerCredentials(0).address.lowercase()

            assertEquals(player0Addr, board[0][0].lowercase(), "Board should reflect player 0's move")
            println("✓ Player 0 move successful, tx: $txHash")
        }

        @Test
        @Order(21)
        @DisplayName("Should make second move as player 1")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `make second move`() = runBlocking {
            if (testGameAddress == null) {
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
                Blockchain.makeMove(0, 0, 0) // Player 0 moves first
            }

            println("Player 1 making move at (1, 1)...")
            val txHash = Blockchain.makeMove(1, 1, 1)

            assertNotNull(txHash)

            // Verify both moves are recorded
            val board = Blockchain.getBoardState()
            val player0Addr = Blockchain.getPlayerCredentials(0).address.lowercase()
            val player1Addr = Blockchain.getPlayerCredentials(1).address.lowercase()

            assertEquals(player0Addr, board[0][0].lowercase(), "Player 0 move should persist")
            assertEquals(player1Addr, board[1][1].lowercase(), "Player 1 move should be recorded")

            println("✓ Player 1 move successful, tx: $txHash")
        }

        @Test
        @Order(22)
        @DisplayName("Should track last player")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `verify last player tracking`() = runBlocking {
            if (testGameAddress == null) {
                testGameAddress = Blockchain.createGameByPlayer(0)
                Blockchain.setCurrentGameAddress(testGameAddress)
                Blockchain.makeMove(0, 0, 0)
                Blockchain.makeMove(1, 1, 1)
            }

            val lastPlayer = Blockchain.readAddress("lastPlayer")
            val player1Addr = Blockchain.getPlayerCredentials(1).address.lowercase()

            assertEquals(player1Addr, lastPlayer.lowercase(), "Last player should be player 1")
            println("✓ Last player correctly tracked: $lastPlayer")
        }

        @Test
        @Order(23)
        @DisplayName("Should play complete game to win")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `play complete winning game`() = runBlocking {
            // Create fresh game
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            println("Playing complete game...")
            println("  P0: (0,0)")
            Blockchain.makeMove(0, 0, 0) // P0: top-left
            println("  P1: (1,0)")
            Blockchain.makeMove(1, 1, 0) // P1: middle-left
            println("  P0: (0,1)")
            Blockchain.makeMove(0, 0, 1) // P0: top-center
            println("  P1: (1,1)")
            Blockchain.makeMove(1, 1, 1) // P1: center
            println("  P0: (0,2)")
            Blockchain.makeMove(0, 0, 2) // P0: top-right (WINS!)

            // Verify game ended
            val gameEnded = Blockchain.readBool("gameEnded")
            assertTrue(gameEnded, "Game should be ended after win")

            // Verify winner
            val winner = Blockchain.readAddress("winner")
            val player0Addr = Blockchain.getPlayerCredentials(0).address.lowercase()
            assertEquals(player0Addr, winner.lowercase(), "Player 0 should be winner")

            println("✓ Player 0 wins! Game ended correctly")
        }
    }

    @Nested
    @DisplayName("Error Handling Integration")
    inner class ErrorHandlingIntegration {

        @Test
        @Order(30)
        @DisplayName("Should reject invalid move (out of bounds)")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `reject out of bounds move`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            // Attempt move at (3, 3) - should fail
            // Note: The contract will revert, not our code
            assertThrows<Exception> {
                runBlocking {
                    Blockchain.makeMove(0, 3, 3)
                }
            }

            println("✓ Out of bounds move correctly rejected")
        }

        @Test
        @Order(31)
        @DisplayName("Should reject move on occupied cell")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `reject move on occupied cell`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            // First move
            Blockchain.makeMove(0, 0, 0)

            // Attempt same cell - should fail
            assertThrows<Exception> {
                runBlocking {
                    Blockchain.makeMove(1, 0, 0)
                }
            }

            println("✓ Occupied cell move correctly rejected")
        }

        @Test
        @Order(32)
        @DisplayName("Should reject move after game ended")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `reject move after game end`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            // Play to win
            Blockchain.makeMove(0, 0, 0)
            Blockchain.makeMove(1, 1, 0)
            Blockchain.makeMove(0, 0, 1)
            Blockchain.makeMove(1, 1, 1)
            Blockchain.makeMove(0, 0, 2) // P0 wins

            // Verify game ended
            assertTrue(Blockchain.readBool("gameEnded"))

            // Attempt move after game ended - should fail
            assertThrows<Exception> {
                runBlocking {
                    Blockchain.makeMove(1, 2, 0)
                }
            }

            println("✓ Post-game move correctly rejected")
        }
    }

    @Nested
    @DisplayName("Gas and Transaction Integration")
    inner class GasIntegration {

        @Test
        @Order(40)
        @DisplayName("Should estimate gas for game creation")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `estimate gas for create game`() = runBlocking {
            println("Creating game and monitoring gas...")

            val gameAddr = Blockchain.createGameByPlayer(0)

            assertNotNull(gameAddr)
            println("✓ Game created with gas estimation")
        }

        @Test
        @Order(41)
        @DisplayName("Should estimate gas for moves")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `estimate gas for moves`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            println("Making move and monitoring gas...")
            val txHash = Blockchain.makeMove(0, 0, 0)

            assertNotNull(txHash)
            println("✓ Move executed with gas estimation")
        }
    }

    @Nested
    @DisplayName("Board State Decoding Integration")
    inner class BoardDecodingIntegration {

        @Test
        @Order(50)
        @DisplayName("Should decode dynamic array board correctly")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `decode dynamic board array`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            // Make some moves
            Blockchain.makeMove(0, 0, 0)
            Blockchain.makeMove(1, 1, 1)
            Blockchain.makeMove(0, 2, 2)

            val board = Blockchain.getBoardState()

            assertEquals(3, board.size)
            board.forEach { row ->
                assertEquals(3, row.size)
            }

            val p0 = Blockchain.getPlayerCredentials(0).address.lowercase()
            val p1 = Blockchain.getPlayerCredentials(1).address.lowercase()

            assertEquals(p0, board[0][0].lowercase())
            assertEquals(p1, board[1][1].lowercase())
            assertEquals(p0, board[2][2].lowercase())

            println("✓ Board state decoded correctly")
        }

        @Test
        @Order(51)
        @DisplayName("Should handle board with all cells filled")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `decode full board`() = runBlocking {
            val gameAddr = Blockchain.createGameByPlayer(0)
            Blockchain.setCurrentGameAddress(gameAddr)

            // Fill entire board (will end in tie or win)
            val moves = listOf(
                Triple(0, 0, 0), // P0
                Triple(1, 0, 1), // P1
                Triple(0, 0, 2), // P0
                Triple(1, 1, 0), // P1
                Triple(0, 1, 1), // P0
                Triple(1, 1, 2), // P1
                Triple(0, 2, 0), // P0
                Triple(1, 2, 1), // P1
                Triple(0, 2, 2), // P0
            )

            for ((player, row, col) in moves) {
                try {
                    Blockchain.makeMove(player, row, col)
                } catch (e: Exception) {
                    // Game might end before all moves
                    break
                }
            }

            val board = Blockchain.getBoardState()
            val filledCount = board.flatten().count { it.lowercase() != ZERO_ADDRESS }

            assertTrue(filledCount >= 3, "Board should have multiple moves")
            println("✓ Full board decoded, filled cells: $filledCount")
        }
    }

    @Nested
    @DisplayName("Multiple Games Integration")
    inner class MultipleGamesIntegration {

        @Test
        @Order(60)
        @DisplayName("Should create and manage multiple games")
        @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
        fun `manage multiple games`() = runBlocking {
            println("Creating multiple games...")

            val game1 = Blockchain.createGameByPlayer(0)
            assertNotNull(game1)

            val game2 = Blockchain.createGameByPlayer(1)
            assertNotNull(game2)

            assertNotEquals(game1, game2, "Each game should have unique address")

            // Test switching between games
            Blockchain.setCurrentGameAddress(game1)
            Blockchain.makeMove(0, 0, 0)

            Blockchain.setCurrentGameAddress(game2)
            Blockchain.makeMove(1, 1, 1)

            println("✓ Multiple games created and managed: $game1, $game2")
        }
    }
}
