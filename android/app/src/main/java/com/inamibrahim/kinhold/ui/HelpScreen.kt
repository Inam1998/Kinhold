package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.HelpGuide
import com.inamibrahim.kinhold.model.HelpPage
import com.inamibrahim.kinhold.model.HelpTopic

/** Each topic's icon and colour, so a long list can be told apart at a glance. */
private fun look(topic: HelpTopic): Pair<ImageVector, Color> = when (topic) {
    HelpTopic.GettingStarted -> Icons.Filled.Star to Tones.Mint
    HelpTopic.Navigating -> Icons.Filled.Search to Tones.Sky
    HelpTopic.People -> Icons.Filled.Person to Tones.Violet
    HelpTopic.Events -> Icons.Filled.DateRange to Tones.Amber
    HelpTopic.Sharing -> Icons.Filled.Share to Tones.Teal
    HelpTopic.Relatives -> Icons.Filled.Favorite to Tones.Rose
    HelpTopic.Photos -> Icons.Filled.Face to Tones.Coral
    HelpTopic.Deleting -> Icons.Filled.Delete to Tones.Coral
    HelpTopic.Backups -> Icons.Filled.Lock to Tones.Mint
    HelpTopic.Restoring -> Icons.Filled.Refresh to Tones.Sky
    HelpTopic.Gedcom -> Icons.Filled.Send to Tones.Violet
    HelpTopic.Settings -> Icons.Filled.Settings to Tones.Sky
    HelpTopic.Pro -> Icons.Filled.Star to Tones.Amber
    HelpTopic.Statistics -> Icons.Filled.Info to Tones.Sky
    HelpTopic.Privacy -> Icons.Filled.Lock to Tones.Teal
    HelpTopic.Lock -> Icons.Filled.Lock to Tones.Mint
    HelpTopic.Checks -> Icons.Filled.Info to Tones.Teal
    HelpTopic.Upcoming -> Icons.Filled.DateRange to Tones.Amber
    HelpTopic.Relationship -> Icons.Filled.Favorite to Tones.Rose
}

/** Pages whose title, summary, sections or limits contain [query] (ignoring case), each with the line that matched. */
private fun search(query: String): List<Pair<HelpPage, String?>> {
    val q = query.trim()
    if (q.isEmpty()) return HelpGuide.pages.map { it to null }
    val out = ArrayList<Pair<HelpPage, String?>>()
    for (p in HelpGuide.pages) {
        val hit = when {
            p.title.contains(q, true) || p.summary.contains(q, true) -> ""
            else -> p.sections.firstOrNull { it.heading.contains(q, true) || it.text.contains(q, true) }?.let { "${it.heading}: ${it.text}" }
                ?: p.limits.firstOrNull { it.contains(q, true) }
        }
        if (hit != null) out += p to hit.ifEmpty { null }
    }
    return out
}

/** The in-app guide: a searchable list of topics, and a page for the one that was opened. [onClose] leaves the guide. */
@Composable
fun HelpScreen(listScroll: ScrollState, onClose: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    // Back closes an open page first, then the guide.
    BackHandler { if (open != null) open = null else onClose() }

    ScreenBackground {
        AnimatedContent(
            targetState = open,
            modifier = Modifier.fillMaxSize().background(Palette.Background),
            transitionSpec = {
                val forward = targetState != null
                val ease = FastOutSlowInEasing
                ContentTransform(
                    slideInHorizontally(tween(280, easing = ease)) { if (forward) it else -it / 4 },
                    slideOutHorizontally(tween(280, easing = ease)) { if (forward) -it / 4 else it },
                    targetContentZIndex = if (forward) 1f else -1f,
                    sizeTransform = null,
                )
            },
            label = "help",
        ) { topic ->
            val page = topic?.let { name -> HelpTopic.entries.firstOrNull { it.name == name } }?.let(HelpGuide::page)
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(if (page == null) listScroll else rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (page == null) {
                    ScreenHeader("Kinhold", "Help", Modifier.padding(top = 12.dp, bottom = 16.dp), onBack = onClose)
                    Field("Search the guide", query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 16.dp), placeholder = "Type a word, such as backup or photo")
                    val results = remember(query) { search(query) }
                    if (results.isEmpty()) {
                        Text("Nothing in the guide mentions \"${query.trim()}\".", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
                    }
                    for ((p, hit) in results) {
                        val (icon, tone) = look(p.topic)
                        AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(28.dp))
                                    .clickable { open = p.topic.name }
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToneIcon(icon, tone, 44.dp)
                                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                    Text(hit ?: p.summary, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
                            }
                        }
                    }
                } else {
                    PageContent(page, onBack = { open = null })
                }
                if (page == null) AdSlot(com.inamibrahim.kinhold.model.AdPlacement.HelpList, Modifier.padding(top = 4.dp))
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
            }
        }
    }
}

@Composable
private fun PageContent(page: HelpPage, onBack: () -> Unit) {
    val (icon, tone) = look(page.topic)
    Row(Modifier.padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Spacer(Modifier.width(14.dp))
        ToneIcon(icon, tone, 52.dp)
        Column(Modifier.padding(start = 16.dp)) {
            Text("HELP", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
            Text(page.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        }
    }
    for (s in page.sections) {
        Text(s.heading, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
        Text(
            s.text,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextMuted,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
    AppCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(Icons.Filled.Info, Tones.Amber, 36.dp)
                Text("Good to know", style = MaterialTheme.typography.titleMedium, color = Tones.Amber, modifier = Modifier.padding(start = 12.dp))
            }
            for (limit in page.limits) {
                Text(
                    limit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
