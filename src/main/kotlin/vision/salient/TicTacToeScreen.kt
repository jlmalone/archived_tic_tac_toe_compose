// src/main/kotlin/vision/salient/TicTacToeScreen.kt
package vision.salient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TicTacToeScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Observe addresses directly from Blockchain state
    val factoryAddress by remember { derivedStateOf { Blockchain.getFactoryAddress() } }
    val gameAddress by remember { derivedStateOf { Blockchain.getCurrentGameAddress() } }

    // Local state for manual address input
    var manualFactoryInput by remember { mutableStateOf(factoryAddress ?: "") }
    var manualGameInput by remember { mutableStateOf(gameAddress ?: "") }

    // State for the game board fetched from blockchain
    var boardState by remember { mutableStateOf<List<List<String>>?>(null) }
    // State for whose turn it is (maybe derived from lastPlayer + game rules)
    var currentPlayerTurn by remember { mutableStateOf<Int?>(null) } // 1 or 2
    var gameStatus by remember { mutableStateOf<String?>(null) } // e.g., "Player 1 Wins!", "Draw!", "Player 2's Turn"


    // Function to fetch full game state (board, turn, end status)
    suspend fun fetchAndUpdateGameState() {
        val addr = Blockchain.getCurrentGameAddress() ?: return // Need game address
        isLoading = true
        statusMessage = "Fetching game state from $addr..."
        try {
            // Fetch all relevant states in parallel or sequence
            val fetchedBoard = Blockchain.getBoardState()
            val isEnded = Blockchain.isGameEnded() // Assuming you add this function
            // val lastPlayer = Blockchain.getLastPlayer() // Add this
            // val winner = Blockchain.getWinner() // Add this

            if (fetchedBoard != null) {
                boardState = fetchedBoard
                // Determine current player based on lastPlayer (needs game logic)
                // Determine gameStatus based on isEnded and winner
                statusMessage = "Game state updated."
            } else {
                statusMessage = "Failed to fetch board state."
            }
        } catch (e: Exception) {
            statusMessage = "Error fetching game state: ${e.message}"
        } finally {
            isLoading = false
        }
    }


    // Fetch game state whenever the gameAddress changes
    LaunchedEffect(gameAddress) {
        if (gameAddress != null) {
            fetchAndUpdateGameState()
        } else {
            // Reset local game state if gameAddress becomes null
            boardState = null
            currentPlayerTurn = null
            gameStatus = null
        }
    }


    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tic-Tac-Toe Web3 (Manual ABI)", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        // --- Factory Configuration ---
        if (factoryAddress == null) {
            Text("Set Deployed Factory Address:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualFactoryInput,
                    onValueChange = { manualFactoryInput = it },
                    label = { Text("Factory Address") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (ethers.isAddress(manualFactoryInput)) {
                        Blockchain.setFactoryAddress(manualFactoryInput)
                        statusMessage = "Factory address set."
                    } else { statusMessage = "Invalid address format." }
                }, enabled = manualFactoryInput.isNotBlank()) { Text("Set") }
            }
        } else {
            Text("Using Factory: $factoryAddress")
            // --- Game Creation ---
            if (gameAddress == null) {
                Button(
                    onClick = {
                        if (!isLoading) {
                            coroutineScope.launch(Dispatchers.IO) {
                                isLoading = true
                                statusMessage = "Requesting new game..."
                                try {
                                    val newGameAddr = Blockchain.createGameByPlayer1()
                                    if (newGameAddr != null) {
                                        statusMessage = "New game started: $newGameAddr"
                                        // setCurrentGameAddress is called internally if event parsed
                                    } else {
                                        statusMessage = "Game created (tx succeeded), but event parsing failed to find address."
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Game creation failed: ${e.message}"
                                } finally { isLoading = false }
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(if (isLoading && statusMessage?.startsWith("Requesting") == true) "Creating..." else "Create New Game (P1)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Manual Join Game
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = manualGameInput, onValueChange = { manualGameInput=it }, label={Text("Join Existing Game Addr")}, modifier=Modifier.weight(1f), singleLine = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if(ethers.isAddress(manualGameInput)) Blockchain.setCurrentGameAddress(manualGameInput) else statusMessage="Invalid address" }, enabled = manualGameInput.isNotBlank()) { Text("Join") }
                }
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        // --- Active Game Area ---
        if (gameAddress != null) {
            Text("Current Game: $gameAddress", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Display the actual Blockchain-connected board
            BlockchainBoardDisplay(
                boardState = boardState, // Pass fetched state
                isLoading = isLoading,
                onMove = { row, col ->
                    // Determine acting player based on UI logic (e.g., toggle button)
                    val playerToAct = 1 // TODO: Replace with actual logic (e.g., check lastPlayer)
                    coroutineScope.launch(Dispatchers.IO) {
                        isLoading = true
                        statusMessage = "Making move ($row, $col) as Player $playerToAct..."
                        try {
                            val txHash = Blockchain.makeMove(playerToAct, row, col)
                            statusMessage = "Move sent ($txHash). Refreshing board..."
                            // Wait a bit for block confirmation then refresh
                            kotlinx.coroutines.delay(5000) // Crude delay
                            fetchAndUpdateGameState()
                        } catch (e: Exception) {
                            statusMessage = "Error making move: ${e.message}"
                            isLoading = false // Reset loading only on error here
                        }
                        // isLoading will be reset by fetchAndUpdateGameState on success
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { coroutineScope.launch { fetchAndUpdateGameState() } }, enabled=!isLoading) {
                Text("Refresh Game State")
            }
            // TODO: Display gameStatus (whose turn, winner, draw) based on fetched state
            if(gameStatus != null) {
                Text(gameStatus!!)
            }

        } else {
            Text("No active game.")
        }


        Spacer(modifier = Modifier.height(24.dp))
        // --- Status Display ---
        if (statusMessage != null) {
            Text("Status: $statusMessage")
        }
    }
}

// --- Board Display Composable ---
@Composable
fun BlockchainBoardDisplay(
    boardState: List<List<String>>?,
    isLoading: Boolean,
    onMove: (row: Int, col: Int) -> Unit
) {
    if (boardState == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Loading board state...")
        }
        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        (0..2).forEach { i ->
            Row {
                (0..2).forEach { j ->
                    val cellOwner = boardState[i][j]
                    val marker = when (cellOwner.lowercase()) {
                        Blockchain.credentialsPlayer1.address.lowercase() -> "X"
                        Blockchain.credentialsPlayer2.address.lowercase() -> "O"
                        "0x0000000000000000000000000000000000000000" -> ""
                        else -> "?" // Unknown player
                    }
                    Button(
                        modifier = Modifier.size(80.dp).padding(4.dp),
                        onClick = { if (!isLoading && marker == "") onMove(i, j) },
                        enabled = !isLoading && marker == "", // Only allow moves on empty cells when not loading
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Text(marker, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}


// Helper object (already provided)
object ethers {
    fun isAddress(s: String): Boolean {
        return s.matches("^0x[a-fA-F0-9]{40}$".toRegex())
    }
}