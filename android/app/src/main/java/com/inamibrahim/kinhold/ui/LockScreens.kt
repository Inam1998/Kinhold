package com.inamibrahim.kinhold.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.PinLock
import kotlinx.coroutines.delay

/** A row of dots showing how many digits have been typed, so the PIN itself is never on screen. */
@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
        repeat(maxOf(count, PinLock.MIN_DIGITS)) { i ->
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (i < count) Palette.Accent else Palette.Text.copy(alpha = 0.18f)),
            )
        }
    }
}

/** The number keys, with delete and OK. [canSubmit] says whether OK should do anything yet. */
@Composable
private fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit, canSubmit: Boolean, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (c in row) Key(enabled, { onDigit(c) }) { Text("$c", style = MaterialTheme.typography.headlineSmall, color = Palette.Text) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Key(enabled, onDelete, "Delete the last digit") { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Palette.Text) }
            Key(enabled, { onDigit('0') }) { Text("0", style = MaterialTheme.typography.headlineSmall, color = Palette.Text) }
            Key(enabled && canSubmit, onSubmit, "OK", accent = true) { Icon(Icons.Filled.Check, null, tint = if (enabled && canSubmit) Palette.OnAccent else Palette.TextMuted) }
        }
    }
}

@Composable
private fun Key(enabled: Boolean, onClick: () -> Unit, description: String? = null, accent: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (accent && enabled) Palette.Accent else Palette.SurfaceHigh)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Asks the phone's own screen lock (PIN, pattern or password) to prove who the person is. Returns whether it could be asked. */
private fun confirmIntent(context: Context) =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
        ?.createConfirmDeviceCredentialIntent("Unlock Kinhold", "Use your phone's screen lock to open Kinhold and remove the PIN.")

/** Covers the app until the right PIN is typed. A forgotten PIN can be reset with the phone's own screen lock. */
@Composable
fun LockScreen() {
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val wait = AppLock.secondsToWait(now)
    LaunchedEffect(AppLock.blockedUntil) {
        while (AppLock.secondsToWait(System.currentTimeMillis()) > 0) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val reset = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) AppLock.turnOff()
    }
    fun submit() {
        if (AppLock.tryPin(pin)) return
        wrong = true
        pin = ""
    }

    androidx.activity.compose.BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    // The lock sits over the app, so it takes every touch itself: nothing underneath can be reached.
    ScreenBackground(Modifier.androidx_blockTouches()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ToneIcon(Icons.Filled.Lock, Tones.Mint, 64.dp)
            Text("Kinhold is locked", style = MaterialTheme.typography.headlineSmall, color = Palette.Text, modifier = Modifier.padding(top = 16.dp))
            Text(
                when {
                    wait > 0 -> "Too many wrong tries. Wait ${wait} seconds."
                    wrong -> "That PIN is not right. Try again."
                    else -> "Enter your PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (wait > 0 || wrong) Palette.Danger else Palette.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            PinDots(pin.length)
            PinPad(
                onDigit = { if (pin.length < PinLock.MAX_DIGITS) { pin += it; wrong = false } },
                onDelete = { pin = pin.dropLast(1) },
                onSubmit = { submit() },
                canSubmit = pin.length >= PinLock.MIN_DIGITS,
                enabled = wait == 0,
            )
            Spacer(Modifier.weight(1f))
            val intent = remember { confirmIntent(context) }
            if (intent != null) {
                DialogButton("Forgot the PIN? Use my phone's screen lock", { reset.launch(intent) }, Modifier.padding(bottom = 24.dp), kind = ButtonKind.Neutral)
            } else {
                Text(
                    "Your phone has no screen lock, so a forgotten PIN cannot be reset here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

/** What the setup screen is doing: turning the lock on, changing the PIN, or turning the lock off. */
enum class LockSetupMode { On, Change, Off }

/** Steps through choosing a PIN twice (and typing the old one first when changing or turning off). Nothing changes until the last step succeeds. */
@Composable
fun LockSetupScreen(mode: LockSetupMode, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val needsOld = mode != LockSetupMode.On
    // Steps: 0 = the phone has no screen lock (warning), 1 = old PIN, 2 = new PIN, 3 = new PIN again.
    val noDeviceLock = mode == LockSetupMode.On && !AppLock.deviceHasLock(context)
    var step by rememberSaveable { mutableStateOf(if (noDeviceLock) 0 else if (needsOld) 1 else 2) }
    var pin by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun next() {
        when (step) {
            1 -> if (AppLock.checkOnly(pin)) {
                if (mode == LockSetupMode.Off) { AppLock.turnOff(); onDone() } else { step = 2; message = null }
                pin = ""
            } else {
                message = "That is not your current PIN."
                pin = ""
            }
            2 -> { first = pin; pin = ""; step = 3; message = null }
            3 -> if (pin == first) {
                AppLock.setPin(pin)
                onDone()
            } else {
                message = "The two PINs do not match. Start again."
                pin = ""; first = ""; step = 2
            }
        }
    }

    ScreenBackground {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("App lock", when (mode) { LockSetupMode.On -> "Turn on the lock"; LockSetupMode.Change -> "Change the PIN"; LockSetupMode.Off -> "Turn off the lock" }, Modifier.fillMaxWidth().padding(top = 12.dp))
            if (step == 0) {
                AppCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Your phone has no screen lock", style = MaterialTheme.typography.titleMedium, color = Tones.Amber)
                        Text(
                            "If you forget the PIN, the only way back in is to clear Kinhold's data, which deletes the tree on this phone. " +
                                "Set a screen lock in your phone's settings first, or keep a backup folder (Backup, Automatic backup) so you could restore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        DialogButton("I understand, continue", { step = 2 }, Modifier.padding(top = 16.dp), kind = ButtonKind.Primary)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    when (step) { 1 -> "Enter your current PIN"; 2 -> "Choose a PIN (4 to 8 digits)"; else -> "Enter the PIN again" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(message ?: " ", style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                PinDots(pin.length)
                PinPad(
                    onDigit = { if (pin.length < PinLock.MAX_DIGITS) pin += it },
                    onDelete = { pin = pin.dropLast(1) },
                    onSubmit = { next() },
                    canSubmit = pin.length >= PinLock.MIN_DIGITS,
                )
                Spacer(Modifier.weight(1f))
            }
            DialogButton("Cancel", onCancel, Modifier.padding(vertical = 16.dp), kind = ButtonKind.Neutral)
        }
    }
}

/** Consumes every touch that reaches it, so a screen drawn over the app cannot be seen through or tapped through. */
private fun Modifier.androidx_blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}
