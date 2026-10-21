package com.eventroute.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eventroute.app.ui.theme.EventRouteTheme

class DebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventRouteTheme {
                DebugScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
