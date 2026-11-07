package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.HelpGuide
import com.inamibrahim.kinhold.model.HelpPage
import com.inamibrahim.kinhold.model.HelpTopic

/** The in-app guide: a list of topics, and a page for the one that was opened. [onClose] leaves the guide. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val page = open?.let { name -> HelpTopic.entries.firstOrNull { it.name == name } }?.let(HelpGuide::page)

    // Back closes an open page first, then the guide.
    BackHandler { if (page != null) open = null else onClose() }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (page == null) {
                ScreenHeader("Kinhold", "Help", Modifier.padding(top = 12.dp, bottom = 20.dp))
                for (p in HelpGuide.pages) {
                    AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { open = p.topic.name }
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                        ) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Pill("Back", false, onClose, Modifier.fillMaxWidth().padding(top = 8.dp))
            } else {
                PageContent(page, onBack = { open = null })
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun PageContent(page: HelpPage, onBack: () -> Unit) {
    ScreenHeader("Help", page.title, Modifier.padding(top = 12.dp, bottom = 16.dp))
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
            Text("Good to know", style = MaterialTheme.typography.titleMedium, color = Tones.Amber)
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
    Pill("All help topics", false, onBack, Modifier.fillMaxWidth().padding(top = 20.dp))
}
