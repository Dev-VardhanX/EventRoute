package com.eventroute.app.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.R
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

private data class OnboardingSlide(
    val title: String,
    val body: String,
    val icon: ImageVector? = null,   // null → student photo
    val tile: Color = ClayBluePrimary,
)

private val slides = listOf(
    OnboardingSlide("Discover Events", "Find the best hackathons, fests, and workshops across Delhi-NCR colleges."),
    OnboardingSlide("Know When to Leave", "Get a door-to-door route and a leave-by time for every event you join.", Icons.Rounded.Route, ClayGreenPrimary),
    OnboardingSlide("Travel as a Team", "See where teammates are, meet on the way, and arrive together.", Icons.Rounded.Groups, ClayPinkTag),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onLoginClick: () -> Unit = onGetStarted,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(pageCount = { slides.size })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))

        ClayIconBadge(
            icon = Icons.Rounded.Route,
            background = ClayBluePrimary,
            tint = Color.White,
            size = 84.dp,
            shape = RoundedCornerShape(30.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text("EventRoute", style = ClayType.Title.copy(fontSize = 30.sp))
        Spacer(Modifier.height(2.dp))
        Text("Inter-college travel, simplified.", style = ClayType.Body.copy(fontSize = 14.sp))

        Spacer(Modifier.height(28.dp))

        ClayCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(36.dp), depth = 14.dp) {
            Box(Modifier.fillMaxWidth()) {
                // soft pink glow in the corner, as in the design
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-40).dp)
                        .size(150.dp)
                        .background(Brush.radialGradient(listOf(ClayPinkTag.copy(alpha = 0.55f), Color.Transparent)))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                        val s = slides[page]
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (s.icon == null) {
                                Box(Modifier.size(112.dp).clay(RoundedCornerShape(32.dp), Color.White, depth = 10.dp).padding(5.dp).clip(RoundedCornerShape(28.dp))) {
                                    Image(
                                        painterResource(R.drawable.avatar_john),
                                        contentDescription = "Student",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                ClayIconBadge(s.icon, background = s.tile, tint = ClayBlueText, size = 112.dp, shape = RoundedCornerShape(32.dp))
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(s.title, style = ClayType.Heading.copy(fontSize = 21.sp))
                            Spacer(Modifier.height(8.dp))
                            Text(s.body, style = ClayType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(slides.size) { i ->
                            val on = pager.currentPage == i
                            Box(
                                Modifier
                                    .height(8.dp)
                                    .width(if (on) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (on) ClayBluePrimary else Color(0xFFE3EAF4))
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        ClayPillButton("Get Started", onGetStarted, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        ClayPillButton(
            "Log In", onLoginClick, Modifier.fillMaxWidth(),
            backgroundColor = Color.White, textColor = ClayTextSecondary
        )
    }
}
