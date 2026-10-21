package com.eventroute.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.ui.theme.*

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val code: String? = null) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = ClayBlueText,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = ClayType.Body,
                    color = ClayTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = "Nothing here yet",
    message: String = "Check back later or try adjusting your filters",
    icon: ImageVector = Icons.Rounded.Inbox
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ClayIconBadge(
                    icon = icon,
                    size = 56.dp,
                    background = ClayGrayChip,
                    tint = ClayTextMuted
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = ClayType.Heading.copy(fontSize = 18.sp),
                    color = ClayTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = ClayType.Body,
                    color = ClayTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ClayIconBadge(
                    icon = Icons.Rounded.ErrorOutline,
                    size = 56.dp,
                    background = ClayPinkTag,
                    tint = ClayPinkText
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = ClayType.Heading.copy(fontSize = 18.sp),
                    color = ClayTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = ClayType.Body,
                    color = ClayTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                ClayPillButton(
                    text = "Try Again",
                    onClick = onRetry,
                    icon = Icons.Rounded.Refresh
                )
            }
        }
    }
}
