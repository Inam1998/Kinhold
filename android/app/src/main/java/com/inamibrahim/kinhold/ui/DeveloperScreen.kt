package com.inamibrahim.kinhold.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.FamilyDb
import com.inamibrahim.kinhold.model.Authorship
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.Family
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's name, version and build, read from the installed package. */
data class AppVersion(val name: String, val code: Long, val debug: Boolean) {
    companion object {
        fun of(context: Context): AppVersion {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val code = if (info == null) 0L else if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            return AppVersion(info?.versionName ?: "unknown", code, AppSettings.debuggable)
        }
    }
}

/**
 * Hidden page, opened by holding the version in Settings for ten seconds. It says who made the app, lets the author prove it by typing
 * the private phrase set with scripts/set-phrase.mjs (only a salted slow hash of it is in the app), and lists build facts for reporting a problem.
 * It changes nothing.
 */
@Composable
fun DeveloperScreen(family: Family, onBack: () -> Unit) {
    val context = LocalContext.current
    val v = AppVersion.of(context)
    var phrase by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf("") } // "", "match" or "no"
    val scope = rememberCoroutineScope()
    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ScreenHeader("Hidden page", "Developer", Modifier.padding(top = 12.dp, bottom = 8.dp), onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Palette.OnAccent, modifier = Modifier.size(38.dp))
                }
                Text("Kinhold v${v.name}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 10.dp))
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToneIcon(Icons.Filled.Person, Tones.Violet, 52.dp)
                    Text("Author and developer", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 10.dp))
                    Text(Authorship.NAME, style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
                    Text(Authorship.EMAIL, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                    Text("Started ${Authorship.STARTED} · © ${Authorship.YEAR} ${Authorship.NAME}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    if (Authorship.isSet) {
                        Text("Confirm authority", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text("Type the author's secret phrase. Capitals and extra spaces do not matter.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        Field("Secret phrase", phrase, { phrase = it; result = "" }, Modifier.fillMaxWidth(), hidden = true)
                        DialogButton(
                            if (busy) "Checking…" else "Confirm",
                            {
                                busy = true
                                result = ""
                                val typed = phrase
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) { Authorship.check(typed) }
                                    result = if (ok) "match" else "no"
                                    busy = false
                                }
                            },
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            kind = ButtonKind.Primary,
                            enabled = phrase.isNotBlank() && !busy,
                            icon = Icons.Filled.Check,
                        )
                        when (result) {
                            "match" -> Text("Matches. This is the author.", style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
                            "no" -> Text("That is not the phrase.", style = MaterialTheme.typography.titleSmall, color = Palette.Danger, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
                        }
                    } else {
                        Text("No private phrase has been set yet.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}
