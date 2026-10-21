# EventRoute claymorphism UI: setup

1. Replace your `ui/` package with this one (same package names: com.eventroute.app.ui.*).

2. build.gradle (app) dependencies
   implementation("androidx.compose.material:material-icons-extended")   // 1.5+ (Handshake, Route)
   // foundation 1.5+ for HorizontalPager (already pulled in by the Compose BOM)

3. Fonts: put these in res/font/ (free on Google Fonts)
   quicksand_semibold.ttf, quicksand_bold.ttf
   nunito_regular.ttf, nunito_semibold.ttf, nunito_bold.ttf

4. Avatars: existing avatar_john is kept. Add avatar_sarah and avatar_alex to res/drawable
   and they are picked up automatically (looked up by first name). Without them, a pastel initials circle is shown.

5. Nav wiring for the new screens
   composable(Screen.Journey.route) {
       JourneyScreen(eventId, onBack = { nav.popBackStack() },
           onStartLiveJourney = { nav.navigate(Screen.LiveJourney.createRoute(eventId)) })
   }
   composable(Screen.LiveJourney.route) { LiveJourneyScreen(onBack = { nav.popBackStack() }) }
   composable(Screen.MapExplore.route) {
       MapExploreScreen(onTabSelected = { /* navigate by key: map/home/journey/profile */ }, onCenterAction = { })
   }
   MapExploreScreen draws its own bottom bar, so hide the host bar on that route.

Exact blurred clay shadows need API 28+. Below that the engine falls back to Modifier.shadow().
