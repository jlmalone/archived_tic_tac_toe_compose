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
import java.io.File
import java.security.MessageDigest

@Composable
fun TicTacToeScreen() {
    // ───────────────────────── UI state ─────────────────────────
    var status           by remember { mutableStateOf<String?>(null) }
    var factoryAddr      by remember { mutableStateOf<String?>(null) }
    var gameAddr         by remember { mutableStateOf<String?>(null) }
    var board            by remember { mutableStateOf<List<List<String>>?>(null) }
//    var rowInput         by remember { mutableStateOf("1") }
//    var colInput         by remember { mutableStateOf("1") }
    var currentPlayerIdx by remember { mutableStateOf(0) }
    var rowInput         by remember { mutableStateOf("0") }  // 0-based grid
    var colInput         by remember { mutableStateOf("0") }
    // players list is rebuilt whenever we switch local⇄remote
    val players by remember(Blockchain.isLocal) {
        mutableStateOf(List(Blockchain.getPlayerCount()) { Blockchain.getPlayerCredentials(it) })
    }

    val scope = rememberCoroutineScope()

    // refresh board when gameAddr changes
    LaunchedEffect(gameAddr) {
        if (!gameAddr.isNullOrBlank()) {
            status = "Loading board…"
            board = try { withContext(Dispatchers.IO) { Blockchain.getBoardState() } } catch (_: Exception) { null }
            status = board?.let { "Board loaded" } ?: "Failed to load board"
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tic-Tac-Toe Web3", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(12.dp))

        // LOCAL ⇄ SEPOLIA toggle   +   Print addresses
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = if (Blockchain.isLocal) "LOCAL ✔" else "SEPOLIA ✔"
            Button(onClick = {
                scope.launch {
                    // flip runtime
                    val newFlag = !Blockchain.isLocal
                    Blockchain.applyLocal(newFlag)
                    // wipe UI-side state
                    factoryAddr = null
                    gameAddr    = null
                    board       = null
                    currentPlayerIdx = 0
                    status = "Switched to ${if (newFlag) "LOCAL" else "SEPOLIA"} mode"
                }
            }) { Text(label) }

            Spacer(Modifier.width(8.dp))
            Button(onClick = { scope.launch { Blockchain.printDerivedAddresses() } }) { Text("Print Addrs") }
        }
        Spacer(Modifier.height(8.dp))

        // Deploy script trigger (only when LOCAL)
        if (Blockchain.isLocal) {
            Button(onClick = {
                scope.launch {
                    status = "Deploying…"
                    val ok = Blockchain.runDeploy()//File("../tic-tac-toe-smart-contract")

//                    val ok = Blockchain.runLocalDeploy(File("../tic-tac-toe-smart-contract"))
                    status = if (ok) "Deploy OK – Load Factory" else "Deploy failed 💥"
                }
            }) { Text("Deploy (npx tsx)") }
            Spacer(Modifier.height(8.dp))
        }

        // Load factory
        Button(onClick = {
            factoryAddr = Blockchain.getFactoryAddress()
            status      = "Factory: $factoryAddr"
        }) { Text("Load Factory") }
        Spacer(Modifier.height(8.dp))

        // Create game (player-1 signer)
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

        // Join game manual
        OutlinedTextField(
            value       = gameAddr ?: "",
            onValueChange = { gameAddr = it },
            label       = { Text("Game Address") },
            singleLine  = true
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

        // player selector / move controls
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
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                colInput,
                { colInput = it.filter(Char::isDigit) },
                label = { Text("Col") },
                modifier = Modifier.width(70.dp),
                singleLine = true
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
                            status = "Move OK"
                        } catch (e: Exception) {
                            status = e.localizedMessage
                        }
                    }
                },
                enabled = !gameAddr.isNullOrBlank()
            ) { Text("Make Move") }
        }
        Spacer(Modifier.height(16.dp))

        // Board grid
        board?.let { rows ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                rows.forEach { row ->
                    Row {
                        row.forEach { owner ->
                            val mark = when (owner.lowercase()) {
                                "0x0000000000000000000000000000000000000000" -> ""
                                else -> Blockchain.emojiForAddress(owner)
                            }
                            Card(
                                modifier = Modifier.size(60.dp).padding(2.dp),
                                border   = BorderStroke(1.dp, Color.Gray)
                            ) { Box(contentAlignment = Alignment.Center) { Text(mark, fontSize = 24.sp) } }
                        }
                    }
                }
            }
        }

        // Status footer
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, modifier = Modifier.padding(4.dp))
        }
    }
}

/* deterministic emoji palette (same as Blockchain.emojiForAddress uses) */
private val EMOJI = listOf(
    "😀","🐱","🐶","🦊","🐸","🐵","🐼","🐯","🐰","🦁",
    "🐮","🐔","🐧","🐨","🐙","🦄","🐝","🐢","🐞","🐳"
)

