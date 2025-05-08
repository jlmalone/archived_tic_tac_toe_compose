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
import androidx.compose.foundation.layout.Arrangement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Removed java.io.File import as it's not directly used in this composable
// Removed java.security.MessageDigest import as Blockchain.emojiForAddress is used

const val ZERO_ADDRESS ="0x0000000000000000000000000000000000000000"

// Helper function for TextField colors, styled for the Matrix theme
@Composable
fun matrixTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = MaterialTheme.colors.onSurface,
    backgroundColor = MaterialTheme.colors.surface, // Or Color.Transparent if you prefer window bg to show through
    cursorColor = MaterialTheme.colors.primary,
    errorCursorColor = MaterialTheme.colors.error,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f), // Dimmer green
    disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
    errorBorderColor = MaterialTheme.colors.error,
    focusedLabelColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
    unfocusedLabelColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.medium),
    disabledLabelColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.disabled),
    placeholderColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.medium),
    disabledPlaceholderColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.disabled)
)

@Composable
fun TicTacToeScreen() {
    var isLoading by remember { mutableStateOf(false) }

    // ───────────────────────── UI state ─────────────────────────
    var status           by remember { mutableStateOf<String?>(null) }
    var factoryAddr      by remember { mutableStateOf<String?>(null) }
    var gameAddr         by remember { mutableStateOf<String?>(null) }
    var board            by remember { mutableStateOf<List<List<String>>?>(null) }
    var currentPlayerIdx by remember { mutableStateOf(0) }
    var rowInput         by remember { mutableStateOf("0") }  // 0-based grid
    var colInput         by remember { mutableStateOf("0") }
    val players by remember(Blockchain.isLocal) {
        mutableStateOf(List(Blockchain.getPlayerCount()) { Blockchain.getPlayerCredentials(it) })
    }

    val scope = rememberCoroutineScope()

    // Get the themed colors for text fields
    val customTextFieldColors = matrixTextFieldColors()

    LaunchedEffect(gameAddr) {
        if (!gameAddr.isNullOrBlank()) {
            status = "Loading board…"
            board = try { withContext(Dispatchers.IO) { Blockchain.getBoardState() } } catch (_: Exception) { null }
            status = board?.let { "Board loaded" } ?: "Failed to load board"
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(), // Background is handled by Surface in Main.kt
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tic-Tac-Toe Web3", style = MaterialTheme.typography.h5) // Color will be onBackground (MatrixGreen)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = if (Blockchain.isLocal) "LOCAL ✔" else "SEPOLIA ✔"
            Button(onClick = {
                scope.launch {
                    val newFlag = !Blockchain.isLocal
                    Blockchain.applyLocal(newFlag)
                    factoryAddr = null
                    gameAddr    = null
                    board       = null
                    currentPlayerIdx = 0
                    status = "Switched to ${if (newFlag) "LOCAL" else "SEPOLIA"} mode"
                }
            }) { Text(label) } // Button text will be onPrimary (MatrixBlack), background primary (MatrixGreen)

            Spacer(Modifier.width(8.dp))
            Button(onClick = { scope.launch { Blockchain.printDerivedAddresses() } }) { Text("Print Addrs") }
        }
        Spacer(Modifier.height(8.dp))

        if (Blockchain.isLocal) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        status    = "Deploying…"
                        val ok    = Blockchain.runDeploy()
                        status    = if (ok) "Deploy OK – Load Factory" else "Deploy failed 💥"
                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator( // Color will be primary (MatrixGreen)
                        modifier     = Modifier.size(16.dp),
                        strokeWidth  = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Deploying…")
                } else {
                    Text("Deploy (npx tsx)")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = {
            factoryAddr = Blockchain.getFactoryAddress()
            status      = "Factory: $factoryAddr"
        }) { Text("Load Factory") }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    status = "Creating game…"
                    val addr = try { withContext(Dispatchers.IO) { Blockchain.createGameByPlayer(0) } } catch (_: Exception) { null }
                    gameAddr = addr
                    status   = addr ?: "Create failed"
                }
            },
            enabled = !factoryAddr.isNullOrBlank()
        ) { Text("Create Game (P1)") }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value       = gameAddr ?: "",
            onValueChange = { gameAddr = it },
            label       = { Text("Game Address") },
            singleLine  = true,
            colors      = customTextFieldColors // Apply themed colors
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick  = {
                Blockchain.setCurrentGameAddress(gameAddr)
                status = "Joined $gameAddr"
            },
            enabled = !gameAddr.isNullOrBlank()
        ) { Text("Join Game") }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentPlayerIdx = (currentPlayerIdx + 1) % players.size }) {
                Text("Signer: P${currentPlayerIdx + 1}")
            }
            Spacer(Modifier.width(12.dp))

            OutlinedTextField(
                rowInput,
                { rowInput = it.filter(Char::isDigit) },
                label = { Text("Row") },
                modifier = Modifier.width(70.dp),
                singleLine = true,
                colors = customTextFieldColors // Apply themed colors
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                colInput,
                { colInput = it.filter(Char::isDigit) },
                label = { Text("Col") },
                modifier = Modifier.width(70.dp),
                singleLine = true,
                colors = customTextFieldColors // Apply themed colors
            )
            Spacer(Modifier.width(6.dp))

            Button(
                onClick = {
                    scope.launch {
                        val r = rowInput.toIntOrNull() ?: -1
                        val c = colInput.toIntOrNull() ?: -1
                        if (r !in 0..2 || c !in 0..2) {
                            status = "Bad cell"
                            return@launch
                        }
                        status = "Submitting move…"
                        try {
                            Blockchain.makeMove(currentPlayerIdx, r, c)
                            board = Blockchain.getBoardState()
                            val ended  = Blockchain.readBool("gameEnded")
                            val winner = Blockchain.readAddress("winner")
                            status = if (ended) {
                                when (winner.lowercase()) {
                                    ZERO_ADDRESS -> "Draw!"
                                    Blockchain.getPlayerCredentials(currentPlayerIdx).address.lowercase() -> "You won!"
                                    Blockchain.getPlayerCredentials(0).address.lowercase() -> "Player 1 wins!"
                                    Blockchain.getPlayerCredentials(1).address.lowercase() -> "Player 2 wins!"
                                    else -> "Game over"
                                }
                            } else { "Move OK" }
                        } catch (e: Exception) {
                            println("Error: ${e.localizedMessage ?: e.message}")
                            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
                            status = when {
                                msg.contains("revert",  true) -> "Invalid move: out of turn / cell taken"
                                msg.contains("0x",      true) -> "Contract returned invalid data"
                                else -> msg
                            }
                        }
                    }
                },
                enabled = !gameAddr.isNullOrBlank()
            ) { Text("Make Move") }
        }
        Spacer(Modifier.height(16.dp))

        board?.let { rows ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                rows.forEach { row ->
                    Row {
                        row.forEach { owner ->
                            val mark = when (owner.lowercase()) {
                                "0x0000000000000000000000000000000000000000" -> ""
                                else -> Blockchain.emojiForAddress(owner)
                            }
                            Card( // Card background will be surface (MatrixBlack)
                                modifier = Modifier.size(60.dp).padding(2.dp),
                                border   = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.7f)), // Themed border
                                elevation = 2.dp // Optional: add some elevation if desired
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(mark, fontSize = 24.sp) // Text color will be onSurface (MatrixGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, modifier = Modifier.padding(4.dp)) // Color will be onBackground (MatrixGreen)
        }
        Spacer(Modifier.height(16.dp))
        Text("Debug Calls", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Reading board…"
                        board = try { Blockchain.getBoardState() } catch (e: Exception) { println("Board error: ${e.message}"); null }
                        status = board?.let { "Board refreshed" } ?: "Board fetch failed"
                    }
                }
            ) { Text("🔄 Refresh Board") }

            Button(onClick = {
                scope.launch {
                    status = try { "Ended? ${Blockchain.readBool("gameEnded")}" } catch (e: Exception) { "Error: ${e.message}" }
                }
            }) { Text("🏁 gameEnded") }
        }
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                scope.launch {
                    status = try { "Last: ${Blockchain.readAddress("lastPlayer")}" } catch (e: Exception) { "Error: ${e.message}" }
                }
            }) { Text("👤 lastPlayer") }

            Button(onClick = {
                scope.launch {
                    status = try { "Winner: ${Blockchain.readAddress("winner")}" } catch (e: Exception) { "Error: ${e.message}" }
                }
            }) { Text("🏆 winner") }

            Button(onClick = {
                scope.launch {
                    status = try { val addr = Blockchain.getCurrentGameAddress() ?: "<none>"; "Current game address: $addr" } catch (e: Exception) { "Error: ${e.message}" }
                }
            }) { Text("📍 currentGame") }

            Button(onClick = {
                scope.launch {
                    status = try { val factory = Blockchain.getFactoryAddress() ?: "<none>"; "Factory address: $factory" } catch (e: Exception) { "Error: ${e.message}" }
                }
            }) { Text("🏭 factoryAddr") }
        }
    }
}
/* deterministic emoji palette (same as Blockchain.emojiForAddress uses) */
private val EMOJI = listOf(
    "😀","🐱","🐶","🦊","🐸","🐵","🐼","🐯","🐰","🦁",
    "🐮","🐔","🐧","🐨","🐙","🦄","🐝","🐢","🐞","🐳"
)

