package ito.telegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import ito.telegram.ui.ItoTheme
import ito.telegram.ui.screens.AdultScreen
import ito.telegram.ui.screens.AgeScanScreen
import ito.telegram.ui.screens.ChatScreen
import ito.telegram.ui.screens.ChatsScreen
import ito.telegram.ui.screens.DebugScreen
import ito.telegram.ui.screens.FeaturesScreen
import ito.telegram.ui.screens.HonestyScreen
import ito.telegram.ui.screens.LabScreen
import ito.telegram.ui.screens.LoginScreen
import ito.telegram.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("chats", "چت‌ها", Icons.Filled.Chat),
    Tab("features", "قابلیت‌ها", Icons.Filled.Tune),
    Tab("lab", "آزمایشگاه", Icons.Filled.Science),
    Tab("adult", "+۱۸", Icons.Filled.Lock),
    Tab("settings", "تنظیمات", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ItoRoot()
                }
            }
        }
    }
}

@Composable
private fun ItoRoot() {
    val nav = rememberNavController()
    var selected by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            selected = index
                            nav.navigate(tab.route) {
                                popUpTo("chats") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            NavHost(navController = nav, startDestination = "chats") {
                composable("chats") { ChatsScreen(nav) }
                composable("features") { FeaturesScreen(nav) }
                composable("lab") { LabScreen() }
                composable("adult") { AdultScreen(nav) }
                composable("settings") { SettingsScreen(nav) }
                composable("login") { LoginScreen(nav) }
                composable("honesty") { HonestyScreen() }
                composable("debug") { DebugScreen() }
                composable("agescan") { AgeScanScreen(nav) }
                composable(
                    "chat/{chatId}",
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { entry ->
                    ChatScreen(nav, entry.arguments?.getLong("chatId") ?: 0L)
                }
            }
        }
    }
}
