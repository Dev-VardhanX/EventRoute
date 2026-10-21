package com.eventroute.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.R
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.components.map.EventGoogleMap
import com.eventroute.app.ui.theme.*

@Composable
fun HomeScreen(
    onEventClick: (String) -> Unit,
    onOpenMapExplore: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMapView by viewModel.isMapView.collectAsStateWithLifecycle()
    val viewIndex = if (isMapView) 1 else 0
    val categories = listOf("All Events", "Hackathons", "Fests", "Robotics", "Workshops")

    val userName = uiState.userProfile?.name?.split(" ")?.firstOrNull() ?: "John"

    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .padding(top = 16.dp, start = 18.dp, end = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Welcome back, $userName!", style = ClayType.Body.copy(fontSize = 13.sp))
                Text("EventRoute", style = ClayType.Title)
            }
            ClayAvatar(userName, size = 48.dp, drawableRes = R.drawable.avatar_john)
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayTextField(
                value = uiState.currentQuery.text,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = "",
                placeholder = "Search events...",
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            ClayIconButton(
                Icons.Rounded.Tune,
                onClick = { showFilterSheet = true },
                contentDescription = "Filters",
                size = 52.dp,
                color = if (uiState.currentQuery.activeFilterCount > 0) ClayPinkPrimary else ClayBluePrimary,
                tint = if (uiState.currentQuery.activeFilterCount > 0) ClayPinkText else ClayBlueText
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val on = category == uiState.currentQuery.category
                ClaySmallButton(
                    category, { viewModel.onCategorySelect(category) },
                    backgroundColor = if (on) ClayBluePrimary else Color.White,
                    textColor = if (on) ClayBlueText else ClayTextSecondary
                )
            }
        }

        ClaySegmented(listOf("List View", "Map View"), viewIndex, { viewModel.setMapView(it == 1) })

        Spacer(Modifier.height(16.dp))

        if (viewIndex == 0) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (val eventsState = uiState.eventsState) {
                    is UiState.Loading -> {
                        item { LoadingState() }
                    }
                    is UiState.Empty -> {
                        item {
                            EmptyState(
                                title = "No events found",
                                message = "Try searching for a different category or college"
                            )
                        }
                    }
                    is UiState.Error -> {
                        item {
                            ErrorState(
                                message = eventsState.message,
                                onRetry = { viewModel.loadData() }
                            )
                        }
                    }
                    is UiState.Success -> {
                        items(eventsState.data) { event ->
                            val banner = if (event.id.contains("2222") || event.title.contains("Robo", ignoreCase = true)) {
                                R.drawable.robocup_banner
                            } else {
                                R.drawable.hackx_banner
                            }
                            EventClayCard(
                                event = event,
                                onViewDetails = { onEventClick(event.id) },
                                bannerDrawableId = banner
                            )
                        }
                    }
                }
            }
        } else {
            val events = (uiState.eventsState as? UiState.Success<List<Event>>)?.data ?: emptyList()
            val homeCameraState = rememberCameraPositionState {
                position = viewModel.cameraPosition
            }
            LaunchedEffect(homeCameraState.isMoving) {
                if (!homeCameraState.isMoving) {
                    viewModel.cameraPosition = homeCameraState.position
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clay(RoundedCornerShape(32.dp), ClayInsetBg, depth = 12.dp)
            ) {
                EventGoogleMap(
                    events = events,
                    userLocation = uiState.userLocation,
                    hasLocationPermission = false,
                    onEventClick = onEventClick,
                    cameraPositionState = homeCameraState,
                    modifier = Modifier.fillMaxSize()
                )
                ClayIconButton(
                    icon = Icons.Rounded.Fullscreen,
                    onClick = {
                        viewModel.cameraPosition = homeCameraState.position
                        onOpenMapExplore()
                    },
                    contentDescription = "Expand map",
                    size = 44.dp,
                    color = Color.White,
                    tint = ClayBlueDeep,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clay(RoundedCornerShape(50), Color.White, depth = 8.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Schedule, null, tint = ClayBlueDeep, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Arrive by 09:30 AM", style = ClayType.BodyStrong.copy(fontSize = 12.sp))
                        Text("45 MIN JOURNEY", style = ClayType.Label.copy(fontSize = 9.sp, color = ClayBlueDeep))
                    }
                }
            }
            Spacer(Modifier.height(110.dp))
        }
    }

    if (showFilterSheet) {
        EventFilterBottomSheet(
            query = uiState.currentQuery,
            onApply = { updated -> viewModel.applyFilterQuery(updated) },
            onReset = { viewModel.resetFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}
