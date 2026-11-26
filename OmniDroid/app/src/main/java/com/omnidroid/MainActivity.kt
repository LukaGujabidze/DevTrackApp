package com.omnidroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnidroid.agent.AgentOrchestrator
import com.omnidroid.agent.AgentResult
import com.omnidroid.agent.AndroidToolRegistry
import com.omnidroid.agent.CommandParser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniDroidApp() }
    }
}

private data class ChatLine(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniDroidApp() {
    val context = LocalContext.current
    val registry = remember(context) { AndroidToolRegistry(context) }
    val agent = remember(registry) { AgentOrchestrator(CommandParser(), registry) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            ChatLine("OmniDroid MVP online. Try: “open settings”, “open calculator”, or “list apps”.", false)
        )
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("OmniDroid", style = MaterialTheme.typography.titleLarge)
                            Text("Personal Android operator · MVP 0.1", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Tell OmniDroid what to do…") },
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (input.isNotBlank() && !busy) {
                                    val command = input.trim()
                                    messages.add(ChatLine(command, true))
                                    input = ""
                                    busy = true
                                    val result = agent.handle(command)
                                    messages.add(ChatLine(result.message, false))
                                    busy = false
                                }
                            })
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            enabled = input.isNotBlank() && !busy,
                            onClick = {
                                val command = input.trim()
                                messages.add(ChatLine(command, true))
                                input = ""
                                busy = true
                                val result = agent.handle(command)
                                messages.add(ChatLine(result.message, false))
                                busy = false
                            }
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Run command")
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("What works in this build", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text("• Launch apps by their visible name\n• Open Android system settings\n• List launchable apps\n• Deterministic command parsing and tool verification")
                            Spacer(Modifier.height(6.dp))
                            Text("No cloud calls, no API key, no hidden actions.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(messages) { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (line.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (line.isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Text(line.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
}
