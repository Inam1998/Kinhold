package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.PlaceBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The places already used in the tree, for the suggestions under every place box. Empty until it has been worked out. */
val LocalPlaces = compositionLocalOf { PlaceBook.Empty }

/** Works the place list out off the main thread whenever the tree changes, and offers it to every place box below. */
@Composable
fun ProvidePlaces(family: Family, content: @Composable () -> Unit) {
    val book by produceState(PlaceBook.Empty, family) {
        value = withContext(Dispatchers.Default) { PlaceBook.of(family) }
    }
    CompositionLocalProvider(LocalPlaces provides book, content = content)
}

/** A text box for a place. While something is typed, places already used in the tree that match it are offered below; tapping one fills it in. */
@Composable
fun PlaceField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val book = LocalPlaces.current
    Column(modifier) {
        Field(label, value, onChange, Modifier.fillMaxWidth())
        val suggestions = remember(book, value) { book.suggest(value) }
        if (suggestions.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (place in suggestions) {
                    Text(
                        place,
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Accent,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Accent.copy(alpha = if (Palette.dark) 0.18f else 0.12f))
                            .clickable { onChange(place) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
