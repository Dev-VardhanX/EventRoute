package com.eventroute.app.ui.screens.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.components.map.EventGoogleMap
import com.eventroute.app.ui.screens.home.EventFilterBottomSheet
import com.eventroute.app.ui.screens.home.HomeViewModel
import com.eventroute.app.ui.theme.*
import com.google.maps.android.compose.rememberCameraPositionState

private val MapTabs = listOf(
    ClayNavTab("map", "Map View", Icons.Rounded.Map),
    ClayNavTab("home", "Home", Icons.Rounded.Home),
    ClayNavTab("journey", "Journey", Icons.Rounded.Route),
    ClayNavTab("profile", "Profile", Icons.Rounded.Person),
)

/** Full-screen explorer map. Draws its own nav bar (Map View / Home / + / Journey / Profile). */
@Composable
fun MapExploreScreen(
    onTabSelected: (String) -> Unit,
    onCenterAction: () -> Unit,
    onEventClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val cameraPositionState = rememberCameraPositionState {
        position = viewModel.cameraPosition
    }

    LaunchedEffect(cameraPositionState.position) {
        viewModel.cameraPosition = cameraPositionState.position
    }

    BackHandler {
        viewModel.cameraPosition = cameraPositionState.position
        onBack()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = listOf("All Events", "Hackathons", "Fests", "Robotics", "Workshops")
    val events = (uiState.eventsState as? UiState.Success<List<Event>>)?.data ?: emptyList()
    var showFilterSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        EventGoogleMap(
            events = events,
            userLocation = uiState.userLocation,
            hasLocationPermission = false,
            onEventClick = onEventClick,
            cameraPositionState = cameraPositionState,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayIconButton(
                    Icons.Rounded.FullscreenExit,
                    onClick = {
                        viewModel.cameraPosition = cameraPositionState.position
                        onBack()
                    },
                    contentDescription = "Collapse Map",
                    size = 52.dp,
                    color = Color.White,
                    tint = ClayBlueDeep
                )
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clay(RoundedCornerShape(50), Color.White, depth = 10.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = ClayTextMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text("Search Delhi-NCR colleges...", style = ClayType.Body.copy(fontSize = 14.sp, color = ClayTextMuted))
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            singleLine = true,
                            textStyle = ClayType.BodyStrong,
                            cursorBrush = SolidColor(ClayBlueDeep),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                ClayIconButton(
                    Icons.Rounded.Tune,
                    onClick = { showFilterSheet = true },
                    contentDescription = "Filters",
                    size = 52.dp,
                    color = if (uiState.currentQuery.activeFilterCount > 0) ClayPinkPrimary else Color.White,
                    tint = if (uiState.currentQuery.activeFilterCount > 0) ClayPinkText else ClayBlueDeep
                )
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
            ) {
                items(categories) { c ->
                    val on = c == uiState.selectedCategory
                    ClaySmallButton(
                        c, { viewModel.onCategorySelect(c) },
                        backgroundColor = if (on) ClayBluePrimary else Color.White,
                        textColor = if (on) ClayBlueText else ClayTextSecondary
                    )
                }
            }
        }

        ClayBottomNavBar(
            selectedTab = "map",
            onTabSelected = onTabSelected,
            onCenterAction = onCenterAction,
            tabs = MapTabs,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showFilterSheet) {
            EventFilterBottomSheet(
                query = uiState.currentQuery,
                onApply = { updated -> viewModel.applyFilterQuery(updated) },
                onReset = { viewModel.resetFilters() },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}
