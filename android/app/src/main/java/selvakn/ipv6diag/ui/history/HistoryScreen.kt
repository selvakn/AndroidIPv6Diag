package selvakn.ipv6diag.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import selvakn.ipv6diag.IPv6DiagApplication
import selvakn.ipv6diag.data.model.DiagnosticSession
import selvakn.ipv6diag.data.model.TestStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as IPv6DiagApplication
    val sessions by app.sessionRepository.getAllSessions().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text("No sessions yet. Run some tests to see history.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions) { session ->
                SessionHistoryCard(session) {
                    navController.navigate("results/${session.id}")
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: DiagnosticSession, onClick: () -> Unit) {
    val passed = session.testResults.count { it.status == TestStatus.PASS }
    val total = session.testResults.size
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(fmt.format(Date(session.timestamp)), style = MaterialTheme.typography.labelMedium)
                Text(session.serverEndpoint.hostname, style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                Text("${session.status}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "$passed/$total",
                style = MaterialTheme.typography.titleMedium,
                color = if (passed == total) Color(0xFF388E3C) else Color(0xFFD32F2F),
            )
        }
    }
}
