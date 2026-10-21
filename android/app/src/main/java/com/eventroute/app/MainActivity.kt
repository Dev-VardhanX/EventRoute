package com.eventroute.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eventroute.app.ui.app.EventRouteRoot
import com.eventroute.app.ui.app.RootViewModel
import com.eventroute.app.ui.theme.EventRouteTheme

class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val startTime = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            !rootViewModel.isReady.value && elapsed < 1500L
        }
        enableEdgeToEdge()
        setContent {
            EventRouteTheme {
                EventRouteRoot(viewModel = rootViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
