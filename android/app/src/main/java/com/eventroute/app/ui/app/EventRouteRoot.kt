package com.eventroute.app.ui.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.eventroute.app.ui.navigation.Screen

/**
 * Owns the root [NavHostController], [RootViewModel], and global session-logout collection.
 */
@Composable
fun EventRouteRoot(viewModel: RootViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent ->
            navController.handleDeepLink(intent)
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    val start by viewModel.startDestination.collectAsStateWithLifecycle()
    if (start == null) return

    LaunchedEffect(Unit) {
        viewModel.loggedOut.collect {
            navController.navigate(Screen.AuthGraph.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    AppScaffold(
        navController = navController,
        startDestination = start!!
    )
}
