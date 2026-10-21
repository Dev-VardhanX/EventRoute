package com.eventroute.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.domain.model.*
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFilterBottomSheet(
    query: EventQuery,
    onApply: (EventQuery) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(query) { mutableStateOf(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ClayBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter & Sort", style = ClayType.Title.copy(fontSize = 22.sp))
                Text(
                    text = "Reset All",
                    style = ClayType.BodyStrong.copy(color = ClayPinkText, fontSize = 13.sp),
                    modifier = Modifier.clayPress({
                        draft = EventQuery(userLat = draft.userLat, userLng = draft.userLng)
                        onReset()
                    }, true, remember { MutableInteractionSource() })
                )
            }

            Spacer(Modifier.height(18.dp))

            // Sort Section
            Text("SORT BY", style = ClayType.Label.copy(color = ClayBlueDeep))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChoiceChip(
                    text = "Soonest",
                    selected = draft.sort == SortOrder.SOONEST,
                    onClick = { draft = draft.copy(sort = SortOrder.SOONEST) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Nearest",
                    selected = draft.sort == SortOrder.NEAREST,
                    onClick = { draft = draft.copy(sort = SortOrder.NEAREST) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Fastest Travel",
                    selected = draft.sort == SortOrder.SHORTEST_TRAVEL_TIME,
                    onClick = { draft = draft.copy(sort = SortOrder.SHORTEST_TRAVEL_TIME) },
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(Modifier.height(18.dp))

            // Participation Type Section
            Text("PARTICIPATION", style = ClayType.Label.copy(color = ClayBlueDeep))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChoiceChip(
                    text = "All",
                    selected = draft.participation == ParticipationFilter.ALL,
                    onClick = { draft = draft.copy(participation = ParticipationFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Solo",
                    selected = draft.participation == ParticipationFilter.SOLO,
                    onClick = { draft = draft.copy(participation = ParticipationFilter.SOLO) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Team",
                    selected = draft.participation == ParticipationFilter.TEAM,
                    onClick = { draft = draft.copy(participation = ParticipationFilter.TEAM) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Either",
                    selected = draft.participation == ParticipationFilter.EITHER,
                    onClick = { draft = draft.copy(participation = ParticipationFilter.EITHER) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            // Pricing Section
            Text("PRICING", style = ClayType.Label.copy(color = ClayBlueDeep))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChoiceChip(
                    text = "All",
                    selected = draft.feeFilter == FeeFilter.ALL,
                    onClick = { draft = draft.copy(feeFilter = FeeFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Free Only",
                    selected = draft.feeFilter == FeeFilter.FREE,
                    onClick = { draft = draft.copy(feeFilter = FeeFilter.FREE) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "Paid",
                    selected = draft.feeFilter == FeeFilter.PAID,
                    onClick = { draft = draft.copy(feeFilter = FeeFilter.PAID) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            // Max Distance Section
            Text("MAX DISTANCE", style = ClayType.Label.copy(color = ClayBlueDeep))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChoiceChip(
                    text = "Any",
                    selected = draft.maxDistanceKm == null,
                    onClick = { draft = draft.copy(maxDistanceKm = null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "10 km",
                    selected = draft.maxDistanceKm == 10.0,
                    onClick = { draft = draft.copy(maxDistanceKm = 10.0) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "25 km",
                    selected = draft.maxDistanceKm == 25.0,
                    onClick = { draft = draft.copy(maxDistanceKm = 25.0) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "50 km",
                    selected = draft.maxDistanceKm == 50.0,
                    onClick = { draft = draft.copy(maxDistanceKm = 50.0) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            // Max Travel Time Section
            Text("MAX TRAVEL TIME", style = ClayType.Label.copy(color = ClayBlueDeep))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChoiceChip(
                    text = "Any",
                    selected = draft.maxTravelMin == null,
                    onClick = { draft = draft.copy(maxTravelMin = null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "30 min",
                    selected = draft.maxTravelMin == 30,
                    onClick = { draft = draft.copy(maxTravelMin = 30) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "45 min",
                    selected = draft.maxTravelMin == 45,
                    onClick = { draft = draft.copy(maxTravelMin = 45) },
                    modifier = Modifier.weight(1f)
                )
                FilterChoiceChip(
                    text = "60 min",
                    selected = draft.maxTravelMin == 60,
                    onClick = { draft = draft.copy(maxTravelMin = 60) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(28.dp))

            // Apply Button
            ClayPillButton(
                text = "Apply Filters",
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) ClayBluePrimary else Color.White
    val textCol = if (selected) ClayBlueText else ClayTextMuted
    val depth = if (selected) 6.dp else 4.dp

    Box(
        modifier = modifier
            .clay(RoundedCornerShape(16.dp), bg, depth = depth)
            .clayPress(onClick, true, remember { MutableInteractionSource() })
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ClayType.BodyStrong.copy(
                fontSize = 11.sp,
                color = textCol
            )
        )
    }
}
