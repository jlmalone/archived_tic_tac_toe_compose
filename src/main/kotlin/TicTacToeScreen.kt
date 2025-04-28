//package io.tictactoe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.tictactoe.Blockchain
//import java.lang.reflect.Modifier

@Composable
fun TicTacToeScreen() {
    var contractAddress by remember { mutableStateOf("0xYOUR_CONTRACT_ADDRESS") }
    var transactionHash by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // UI
    Column {
        Text(text = "Contract Address:")
        Text(text = contractAddress)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        Blockchain.createGame(contractAddress)
                        transactionHash = "Transaction submitted successfully."
                    } catch (e: Exception) {
                        transactionHash = "Error: ${e.localizedMessage}"
                    }
                }
            }
        ) {
            Text("Create Game")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (transactionHash != null) {
            Text(text = "Result: $transactionHash")
        }
    }
}
