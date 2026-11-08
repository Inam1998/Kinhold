package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Relationship
import com.inamibrahim.kinhold.model.Relationships

/** The sentence for a relationship, from the first person's side: "Ahmed is Inam's uncle." */
fun Relationship.sentence(a: String, b: String): String =
    if (english == "The same person") "$a and $b are the same person." else "$b is $a's ${english.replaceFirstChar(Char::lowercase)}."

/** Pick any two people and see how they are related, in English and, if chosen, in the family words of another language. */
@Composable
fun RelationScreen(family: Family, firstId: Int, secondId: Int, onBack: () -> Unit) {
    var first by rememberSaveable { mutableIntStateOf(firstId) }
    var second by rememberSaveable { mutableIntStateOf(secondId) }
    var picking by rememberSaveable { mutableIntStateOf(0) }
    val a = family.person(if (first in family) first else family.people.first().id)
    val b = family.person(if (second in family) second else family.people.first().id)
    val result = remember(family, a.id, b.id) { Relationships.between(family, a.id, b.id) }

    ScreenBackground {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Relationship", "How are they related?", Modifier.padding(top = 12.dp, bottom = 8.dp), onBack = onBack)
            PersonSlot("First person", a.name, a.lifeDates()) { picking = 1 }
            PersonSlot("Second person", b.name, b.lifeDates()) { picking = 2 }
            DialogButton("Swap them", { val t = first; first = second; second = t }, kind = ButtonKind.Neutral)

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Family words", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    KinLanguageChoice()
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (result == null) {
                        Text("The tree does not connect ${a.name} and ${b.name}.", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "They may be related in a way you have not entered yet, such as through a parent or a marriage that is missing from the tree.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                        )
                    } else {
                        Text(result.sentence(a.name, b.name), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        AppSettings.kinLanguage?.let { lang ->
                            val word = result.wordIn(lang)
                            if (word != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ToneIcon(Icons.Filled.Favorite, Tones.Rose, 32.dp)
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text("In ${lang.label}", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                                        Text(word, style = MaterialTheme.typography.titleMedium, color = Tones.Rose)
                                    }
                                }
                            } else {
                                Text("No ${lang.label} family word for this one, or the tree does not say enough (such as a parent's sex).", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                        }
                        if (result.path.size > 2) {
                            Text("Through", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                            Text(result.pathNames().joinToString("  →  "), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                        }
                        Text(
                            if (result.blood) "A blood relation." else "A relation by marriage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
    if (picking != 0) {
        PersonPickerDialog(
            family,
            if (picking == 1) "First person" else "Second person",
            { if (picking == 1) first = it else second = it; picking = 0 },
            { picking = 0 },
        )
    }
}

/** Off, or one language whose family words are shown beside the English relationship. The choice is saved on the phone. */
@Composable
fun KinLanguageChoice() {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("English only", AppSettings.kinLanguage == null, { AppSettings.chooseKinLanguage(null) })
        for (l in com.inamibrahim.kinhold.model.KinLanguage.entries) Pill(l.label, AppSettings.kinLanguage == l, { AppSettings.chooseKinLanguage(l) })
    }
}

@Composable
private fun PersonSlot(label: String, name: String, dates: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(Icons.Filled.Person, Tones.Violet, 40.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Text(name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                if (dates.isNotEmpty()) LifeText(dates, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
        }
    }
}
