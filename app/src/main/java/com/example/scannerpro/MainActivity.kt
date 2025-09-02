package com.example.scannerpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scannerpro.ui.DetailScreen
import com.example.scannerpro.ui.HomeView
import com.example.scannerpro.ui.ListScreen
import com.example.scannerpro.ui.Testvista
import com.example.scannerpro.ui.theme.ScannerPROTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerPROTheme {            // usa tu theme
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "home") {

        composable("home") {
            HomeView(
                onNavigateToList = { navController.navigate("list") },
                onNavigateToDetail = { navController.navigate("detail/0") },
                onNavigateTest = { navController.navigate("test") }
            )
        }

        composable("list") {
            ListScreen(onItemClick = { itemId ->
                navController.navigate("detail/$itemId")
            })
        }

        composable("test") {
            Testvista(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.IntType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getInt("itemId") ?: 0
            DetailScreen(itemId)
        }
    }
}

