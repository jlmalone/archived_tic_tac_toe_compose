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

@Composable
fun TicTacToeScreen() {
    var status by remember { mutableStateOf<String?>(null) }
    var factoryAddr by remember { mutableStateOf<String?>(null) }
    var gameAddr by remember { mutableStateOf<String?>(null) }
    var board by remember { mutableStateOf<List<List<String>>?>(null) }
    var rowInput by remember { mutableStateOf("0") }
    var colInput by remember { mutableStateOf("0") }
    val scope = rememberCoroutineScope()

    // Whenever we get a new game address, immediately load its (empty) board
    LaunchedEffect(gameAddr) {
        if (!gameAddr.isNullOrBlank()) {
            status = "Loading board…"
            board = try {
                withContext(Dispatchers.IO) { Blockchain.getBoardState() }
            } catch (e: Exception) {
                null
            }
            status = if (board != null) "Board loaded" else "Failed to load board"
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tic-Tac-Toe Web3", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            scope.launch {
                Blockchain.printDerivedAddresses()
                status = "Addresses printed"
            }
        }) {
            Text("Print Addresses")
        }
        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            factoryAddr = Blockchain.getFactoryAddress()
            status = "Factory: $factoryAddr"
        }) {
            Text("Load Factory")
        }
        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                status = "Creating game…"
                val addr = try {
                    withContext(Dispatchers.IO) { Blockchain.createGameByPlayer1() }
                } catch (e: Exception) {
                    null
                }
                if (addr != null) {
                    gameAddr = addr
                    status = "Game: $addr"
                } else {
                    status = "Create game failed"
                }
            }
        }, enabled = !factoryAddr.isNullOrBlank()) {
            Text("Create Game (P1)")
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = gameAddr ?: "",
            onValueChange = { gameAddr = it },
            label = { Text("Game Address") },
            singleLine = true
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = {
            Blockchain.setCurrentGameAddress(gameAddr)
            status = "Joined $gameAddr"
        }, enabled = !gameAddr.isNullOrBlank()) {
            Text("Join Game")
        }
        Spacer(Modifier.height(16.dp))

        // Inputs + Make Move button
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = rowInput,
                onValueChange = { rowInput = it.filter { it.isDigit() } },
                label = { Text("Row") },
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = colInput,
                onValueChange = { colInput = it.filter { it.isDigit() } },
                label = { Text("Col") },
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    val r = rowInput.toIntOrNull() ?: -1
                    val c = colInput.toIntOrNull() ?: -1
                    if (r in 0..2 && c in 0..2 && !gameAddr.isNullOrBlank()) {
                        status = "Submitting move ($r,$c)…"
                        val tx = try {
                            withContext(Dispatchers.IO) {
                                Blockchain.makeMove(Blockchain.credentialsPlayer1, r, c)
                            }
                        } catch (e: Exception) {
                            null
                        }
                        if (tx != null) {
                            status = "Move sent: $tx"
                            // refresh board immediately
                            board = try {
                                withContext(Dispatchers.IO) { Blockchain.getBoardState() }
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            status = "Move failed"
                        }
                    } else {
                        status = "Invalid cell or no game"
                    }
                }
            }, enabled = !gameAddr.isNullOrBlank()) {
                Text("Make Move")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Render the 3×3 grid
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
                            Card(
                                modifier = Modifier
                                    .size(60.dp)
                                    .padding(2.dp),
                                border = BorderStroke(1.dp, Color.Gray)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(mark, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Footer status
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text("Status: $it", modifier = Modifier.padding(8.dp))
        }
    }
}
// Helper object (already provided)
object ethers {
    fun isAddress(s: String): Boolean {
        return s.matches("^0x[a-fA-F0-9]{40}$".toRegex())
    }
}