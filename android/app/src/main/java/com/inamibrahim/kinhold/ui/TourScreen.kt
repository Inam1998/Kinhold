package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class Point(val icon: ImageVector, val tone: Color, val title: String, val text: String)

private class TourStep(
    val icon: ImageVector,
    val tone: Color,
    /** Two smaller icons that orbit the big one, so each page has its own picture. */
    val orbit: Pair<ImageVector, ImageVector>,
    val orbitTones: Pair<Color, Color>,
    val title: String,
    val text: String,
    val points: List<Point>,
)

private val steps = listOf(
    TourStep(
        Icons.Filled.Favorite, Tones.Rose, Icons.Filled.Person to Icons.Filled.Home, Tones.Violet to Tones.Amber,
        "Your family, kept by you",
        "A private family tree with no account and no subscription.",
        listOf(
            Point(Icons.Filled.Lock, Tones.Mint, "Private", "Your tree stays on this phone. Nothing is uploaded."),
            Point(Icons.Filled.Star, Tones.Amber, "Free to use", "No subscription and no lost trees."),
            Point(Icons.Filled.Person, Tones.Sky, "Made for real families", "Names in any language, Hijri dates, and relations like chacha and mamu."),
        ),
    ),
    TourStep(
        Icons.Filled.Person, Tones.Violet, Icons.Filled.Favorite to Icons.Filled.Check, Tones.Rose to Tones.Mint,
        "Build it in minutes",
        "Start with yourself and grow outward.",
        listOf(
            Point(Icons.Filled.Star, Tones.Coral, "Quick add", "Type just a name to add a child, spouse, parent or sibling."),
            Point(Icons.Filled.Person, Tones.Sky, "Tap to explore", "Tap anyone in the tree to see their side of the family."),
            Point(Icons.Filled.Search, Tones.Teal, "Find anyone", "Search by name and jump straight to them."),
        ),
    ),
    TourStep(
        Icons.Filled.Notifications, Tones.Amber, Icons.Filled.Favorite to Icons.Filled.Info, Tones.Rose to Tones.Violet,
        "More than a chart",
        "Details that make the tree useful.",
        listOf(
            Point(Icons.Filled.Notifications, Tones.Amber, "Dates to remember", "Birthdays and anniversaries, with an optional morning reminder."),
            Point(Icons.Filled.Favorite, Tones.Rose, "How are we related?", "Pick two people and see the relationship."),
            Point(Icons.Filled.Check, Tones.Mint, "Tree checks", "Spot impossible dates and people entered twice."),
        ),
    ),
    TourStep(
        Icons.Filled.Share, Tones.Teal, Icons.Filled.Person to Icons.Filled.Lock, Tones.Sky to Tones.Mint,
        "Share with relatives",
        "Build the tree together, without giving it away.",
        listOf(
            Point(Icons.Filled.Share, Tones.Teal, "Send a branch", "Share just the part of the tree a relative needs."),
            Point(Icons.Filled.Check, Tones.Mint, "You decide", "When you merge a file, you choose what to keep, person by person."),
            Point(Icons.Filled.Lock, Tones.Violet, "Private details stay out", "Phone numbers and addresses are left out unless you add them."),
        ),
    ),
    TourStep(
        Icons.Filled.Lock, Tones.Mint, Icons.Filled.Check to Icons.Filled.Star, Tones.Teal to Tones.Amber,
        "Never lose your tree",
        "Your tree is your own, so Kinhold protects it.",
        listOf(
            Point(Icons.Filled.Check, Tones.Mint, "Automatic backups", "Saved after changes. Pick a folder in More to keep copies outside the app."),
            Point(Icons.Filled.Lock, Tones.Violet, "Optional PIN lock", "Turn it on in Settings if others use your phone."),
            Point(Icons.Filled.Info, Tones.Sky, "Help for every feature", "Open More, then How Kinhold works, any time."),
        ),
    ),
)

/** A first-run introduction: swipe or tap Next through five pages. It can be shown again from More. */
@Composable
fun TourScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == steps.lastIndex
    BackHandler { if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } else onDone() }
    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) DialogButton("Skip", onDone, kind = ButtonKind.Neutral)
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i -> TourPage(steps[i]) }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == page) Palette.Accent else Palette.Text.copy(alpha = 0.2f)))
                }
            }
            PrimaryButton(
                if (last) "Start" else "Next",
                { if (last) onDone() else scope.launch { pager.animateScrollToPage(page + 1) } },
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun TourPage(s: TourStep) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(200.dp, 180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).clip(CircleShape).background(s.tone.copy(alpha = 0.10f)))
            Box(Modifier.size(130.dp).clip(CircleShape).background(s.tone.copy(alpha = 0.14f)))
            ToneIcon(s.icon, s.tone, 96.dp)
            ToneIcon(s.orbit.first, s.orbitTones.first, 46.dp, Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 6.dp))
            ToneIcon(s.orbit.second, s.orbitTones.second, 40.dp, Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = (-6).dp))
        }
        Text(s.title, style = MaterialTheme.typography.headlineMedium, color = Palette.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text(s.text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                s.points.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(p.icon, p.tone, 40.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(p.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            Text(p.text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
