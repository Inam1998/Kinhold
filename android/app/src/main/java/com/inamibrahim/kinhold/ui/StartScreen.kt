package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel

private val SampleSizes = listOf(60, 300, 1000, 3000)

/** Shown while the tree has nobody in it. */
@Composable
fun StartScreen(vm: KinholdViewModel, onAddFirst: () -> Unit, onSample: (Int) -> Unit) {
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val openGedcom = rememberGedcomImporter(vm, currentPeople = 0) { text, isError -> message = text to isError }
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            ScreenHeader("Kinhold", "Start your family tree", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            AppCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Begin with one person", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text(
                        "Add yourself, or the oldest ancestor you know, and build outward from there: parents, " +
                            "spouses and children. Your tree is kept on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    PrimaryButton("Add the first person", onAddFirst, Modifier.fillMaxWidth())
                    Pill("Import a GEDCOM file", selected = false, onClick = openGedcom, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    Text(
                        "Already have a tree in Ancestry, MyHeritage or another program? Export it as GEDCOM there and open it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            message?.let { (text, isError) ->
                AppCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (n in SampleSizes) Pill("%,d people".format(n), selected = false, onClick = { onSample(n) })
            }
        }
    }
}
