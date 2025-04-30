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
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TicTacToeScreen() {
    var status       by remember { mutableStateOf<String?>(null) }
    var factoryAddr  by remember { mutableStateOf<String?>(null) }
    var gameAddr     by remember { mutableStateOf<String?>(null) }
    var board        by remember { mutableStateOf<List<List<String>>?>(null) }
    var rowInput     by remember { mutableStateOf("0") }
    var colInput     by remember { mutableStateOf("0") }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tic‑Tac‑Toe Web3", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(12.dp))

        // ───────────── util buttons row ─────────────
        Row {
            Button(onClick = { scope.launch { Blockchain.printDerivedAddresses(); status = "Logged addresses" } }) {
                Text("Print Addrs")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    val ok = Blockchain.runLocalDeploy(File("/Users/josephmalone/tic-tac-toe-smart-contract"))
                    status = if (ok) "Deploy finished" else "Deploy script failed"
                    factoryAddr = Blockchain.reloadDeployment()?.factoryAddress
                }
            }) { Text("Deploy Contracts") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                factoryAddr = Blockchain.reloadDeployment()?.factoryAddress
                status = "Factory reloaded: $factoryAddr"
            }) { Text("Load Factory") }
        }

        Spacer(Modifier.height(12.dp))

        // ───────────── create / join game ─────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                scope.launch {
                    status = "Creating game…"
                    val addr = try { withContext(Dispatchers.IO) { Blockchain.createGameByPlayer1() } } catch (e: Exception) {
                        e.printStackTrace(); null
                    }
                    if (addr != null) {
                        gameAddr = addr
                        status   = "Game created @ $addr"
                    } else status = "Create failed"
                }
            }, enabled = !factoryAddr.isNullOrBlank()) { Text("Create Game") }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(gameAddr ?: "", onValueChange = { gameAddr = it }, label = { Text("Game Addr") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                Blockchain.setCurrentGameAddress(gameAddr)
                status = "Joined $gameAddr"
            }, enabled = !gameAddr.isNullOrBlank()) { Text("Join") }
        }

        Spacer(Modifier.height(16.dp))

        // ───────────── board + move ─────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(rowInput, { rowInput = it.filter(Char::isDigit) }, label = { Text("Row") }, modifier = Modifier.width(70.dp))
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(colInput, { colInput = it.filter(Char::isDigit) }, label = { Text("Col") }, modifier = Modifier.width(70.dp))
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                scope.launch {
                    val r = rowInput.toIntOrNull() ?: -1
                    val c = colInput.toIntOrNull() ?: -1
                    if (r !in 0..2 || c !in 0..2) { status = "Bad cell"; return@launch }
                    status = "Sending move ($r,$c)…"
                    val tx = try { withContext(Dispatchers.IO) { Blockchain.makeMove(Blockchain.credentialsPlayer1, r, c) } } catch (e: Exception) {
                        e.printStackTrace(); null
                    }
                    status = tx?.let { "Tx $it" } ?: "Move failed"
                    board = withContext(Dispatchers.IO) { Blockchain.getBoardState() }
                }
            }, enabled = !gameAddr.isNullOrBlank()) { Text("Move") }
        }

        Spacer(Modifier.height(12.dp))

        board?.let { rows ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                rows.forEach { row ->
                    Row {
                        row.forEach { owner ->
                            val mark = when (owner.lowercase()) {
                                Blockchain.credentialsPlayer1.address.lowercase() -> "X"
                                Blockchain.credentialsPlayer2.address.lowercase() -> "O"
                                else -> ""
                            }
                            Card(Modifier.size(60.dp).padding(1.dp), border = BorderStroke(1.dp, Color.DarkGray)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(mark, fontSize = 20.sp) }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        status?.let { Text(it) }
    }
}