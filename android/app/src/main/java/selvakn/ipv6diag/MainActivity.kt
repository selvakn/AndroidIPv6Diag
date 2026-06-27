package selvakn.ipv6diag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import selvakn.ipv6diag.ui.history.HistoryScreen
import selvakn.ipv6diag.ui.home.HomeScreen
import selvakn.ipv6diag.ui.network.NetworkInfoScreen
import selvakn.ipv6diag.ui.results.ResultsScreen
import selvakn.ipv6diag.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController = navController)
                        }
                        composable("results/{sessionId}") { backStack ->
                            val sessionId = backStack.arguments?.getString("sessionId") ?: return@composable
                            ResultsScreen(sessionId = sessionId, navController = navController)
                        }
                        composable("networkInfo") {
                            NetworkInfoScreen(navController = navController)
                        }
                        composable("history") {
                            HistoryScreen(navController = navController)
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
