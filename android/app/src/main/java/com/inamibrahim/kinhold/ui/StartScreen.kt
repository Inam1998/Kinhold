@file:OptIn(ExperimentalLayoutApi::class)

package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel

private val SampleSizes = listOf(60, 300, 1000, 3000)

/** Shown while the tree has nobody in it. */
@Composable
fun StartScreen(vm: KinholdViewModel, onHelp: () -> Unit, onSettings: () -> Unit,onAddFirst: () -> Unit, onSample: (Int) -> Unit) {
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val openGedcom = rememberGedcomImporter(vm, currentPeople = 0) { text, isError -> message = text to isError }
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader("Kinhold", "Your family tree", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))

            HeroCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                Column(Modifier.padding(26.dp)) {
                    Text(
                        "Begin with one person",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Text(
                        "Add yourself, or the oldest ancestor you know, and build outward from there: parents, spouses and " +
                            "children. Your tree and its photos are kept on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
                    )
                    // White on the gradient, so it reads as the one action here.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .clickable(onClick = onAddFirst),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Add the first person",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.AccentDeep,
                        )
                    }
                }
            }

            FlowRow(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Import a GEDCOM file", selected = false, onClick = openGedcom)
                Pill("How Kinhold works", selected = false, onClick = onHelp)
                Pill("Settings", selected = false, onClick = onSettings)
            }
            Text(
                "Already have a tree in Ancestry, MyHeritage or another program? Export it as GEDCOM there and open it here.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp),
            )

            message?.let { (text, isError) ->
                AppCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isError) Palette.Danger else Palette.Accent,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            Text(
                "Just looking around?",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 28.dp, start = 24.dp, end = 20.dp),
            )
            Text(
                "Fill the tree with an invented family to try the app. Sample people are real entries you can edit or remove.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp, start = 24.dp, end = 20.dp),
            )
            // The padding sits inside the scrolling row so the buttons slide to the screen edge, not to the margin.
            FlowRow(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (n in SampleSizes) Pill("%,d people".format(n), selected = false, onClick = { onSample(n) })
            }
            Column(Modifier.padding(bottom = 24.dp)) {}
        }
    }
}
