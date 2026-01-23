package com.minescope.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.ads.MobileAds
import com.minescope.app.ui.DashboardScreen
import com.minescope.app.ui.SettingsScreen
import com.minescope.app.ui.theme.MineScopeTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Wait for Ad Load (Max 3.0 sec)
        val startTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            val app = application as? MyApplication
            val isAdReady = app?.isAdCheckComplete == true
            val isTimeout = (System.currentTimeMillis() - startTime) > 3000
            
            !(isAdReady || isTimeout)
        }
        
        // Enable Edge-to-Edge for Android 15 (Target SDK 35)
        enableEdgeToEdge()

        setContent {
            MineScopeTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf("dashboard") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("民eスコープ", color = Color.White)
                        Text("v1.2.9 (Build 270)", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    if (currentScreen == "dashboard") {
                        IconButton(onClick = { currentScreen = "settings" }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        if (currentScreen == "dashboard") {
            DashboardScreen(modifier = Modifier.padding(innerPadding))
        } else {
            SettingsScreen(
                onBackCompete = { currentScreen = "dashboard" },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
