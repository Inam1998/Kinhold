package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Countries
import com.inamibrahim.kinhold.model.Country

/**
 * A phone number with a country picker, so "+92" never has to be typed. The two parts are kept apart while editing and joined into the
 * stored form "+92 3001234567" each time either changes; a number saved earlier without a code is shown with the phone's own country
 * and is left exactly as it was until the person changes it.
 */
@Composable
fun PhoneField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val locale = java.util.Locale.getDefault().country
    val default = remember { Countries.forIso(locale) }
    val initial = remember { Countries.split(value, default) }
    var iso by rememberSaveable { mutableStateOf(initial.first?.iso ?: "") }
    var national by rememberSaveable { mutableStateOf(initial.second) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val country = if (iso.isEmpty()) null else Countries.forIso(iso)

    Column(modifier) {
        Label("Phone number", Modifier.padding(bottom = 6.dp))
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Row(
                Modifier.fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.SurfaceHigh)
                    .clickable { picking = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (country != null) "${country.flag} ${country.dial}" else "Code", style = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 16.sp, color = Palette.Text))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose the country code", tint = Palette.TextMuted, modifier = Modifier.padding(start = 2.dp).size(18.dp))
            }
            Field("", national, { national = it; onChange(Countries.join(country, it)) }, Modifier.weight(1f), KeyboardType.Phone, placeholder = "300 1234567", labelHidden = true)
        }
    }
    if (picking) {
        var query by rememberSaveable { mutableStateOf("") }
        val list = remember(query) {
            val q = query.trim().lowercase()
            Countries.sorted(default).filter { q.isEmpty() || it.name.lowercase().contains(q) || it.dial.contains(q) || it.iso.lowercase() == q }
        }
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Country code", color = Palette.Text) },
            text = {
                Column {
                    Field("", query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search a country or code", labelHidden = true, autoFocus = true)
                    LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                        items(list, key = { it.iso }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    iso = c.iso
                                    onChange(Countries.join(c, national))
                                    picking = false
                                }.padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.flag, style = MaterialTheme.typography.titleMedium)
                                Text(c.name, style = MaterialTheme.typography.bodyLarge, color = if (c.iso == iso) Palette.Accent else Palette.Text, modifier = Modifier.padding(start = 12.dp).weight(1f))
                                Text(c.dial, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.width(56.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { DialogButton("Cancel", { picking = false }, kind = ButtonKind.Neutral) },
        )
    }
}
