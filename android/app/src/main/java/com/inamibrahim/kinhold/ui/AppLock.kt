package com.inamibrahim.kinhold.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.inamibrahim.kinhold.model.LockDelay
import com.inamibrahim.kinhold.model.PinLock
import com.inamibrahim.kinhold.model.StoredPin

/**
 * The optional app lock. When it is on, Kinhold asks for the PIN when it opens and when it comes back from the background
 * after the chosen delay. The PIN hash lives in its own small preferences file, not in the tree and not in backups.
 */
object AppLock {
    private const val FILE = "kinhold-lock"
    private var context: Context? = null

    var enabled by mutableStateOf(false)
        private set
    var delay by mutableStateOf(LockDelay.OneMinute)
        private set

    /** True once the PIN has been entered since the app opened or last locked. */
    var unlocked by mutableStateOf(true)
        private set

    private var stored: StoredPin? = null
    private var leftAt = 0L
    var failures by mutableStateOf(0)
        private set
    var blockedUntil by mutableStateOf(0L)
        private set

    fun load(appContext: Context) {
        context = appContext.applicationContext
        val p = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val salt = p.getString("salt", null)
        val hash = p.getString("hash", null)
        stored = if (salt != null && hash != null) StoredPin(salt, hash) else null
        enabled = stored != null && p.getBoolean("enabled", false)
        delay = LockDelay.entries.firstOrNull { it.name == p.getString("delay", null) } ?: LockDelay.OneMinute
        failures = p.getInt("failures", 0)
        blockedUntil = p.getLong("blocked_until", 0L)
        unlocked = !enabled
    }

    private fun prefs() = context!!.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Turns the lock on with a new PIN (or changes the PIN of a lock that is on). The app stays unlocked. */
    fun setPin(pin: String) {
        val s = PinLock.create(pin)
        stored = s
        enabled = true
        failures = 0
        blockedUntil = 0
        unlocked = true
        prefs().edit { putString("salt", s.salt); putString("hash", s.hash); putBoolean("enabled", true); putInt("failures", 0); putLong("blocked_until", 0L) }
    }

    fun turnOff() {
        stored = null
        enabled = false
        unlocked = true
        failures = 0
        blockedUntil = 0
        prefs().edit { clear() }
    }

    fun chooseDelay(value: LockDelay) {
        delay = value
        prefs().edit { putString("delay", value.name) }
    }

    /** Seconds still to wait before another try is allowed, 0 if tries are allowed now. */
    fun secondsToWait(now: Long = System.currentTimeMillis()): Int = ((blockedUntil - now + 999) / 1000).toInt().coerceAtLeast(0)

    /** Checks a PIN. A right PIN unlocks the app; a wrong one counts, and after five in a row makes the person wait. */
    fun tryPin(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        val s = stored ?: return true.also { unlocked = true }
        if (secondsToWait(now) > 0) return false
        return if (PinLock.verify(pin, s)) {
            failures = 0
            blockedUntil = 0
            unlocked = true
            prefs().edit { putInt("failures", 0); putLong("blocked_until", 0L) }
            true
        } else {
            failures += 1
            val wait = PinLock.waitSeconds(failures)
            blockedUntil = if (wait > 0) now + wait * 1000L else 0L
            prefs().edit { putInt("failures", failures); putLong("blocked_until", blockedUntil) }
            false
        }
    }

    /** Checks the PIN without unlocking anything or counting a failure, for turning the lock off or changing it. */
    fun checkOnly(pin: String): Boolean = stored?.let { PinLock.verify(pin, it) } ?: false

    /** The person proved who they are with the phone's own screen lock: the app unlocks and the PIN can be set again. */
    fun unlockWithDeviceLock() {
        failures = 0
        blockedUntil = 0
        unlocked = true
        prefs().edit { putInt("failures", 0); putLong("blocked_until", 0L) }
    }

    fun onLeave(now: Long = System.currentTimeMillis()) {
        leftAt = now
    }

    /** Called when the app comes back to the screen: locks again if it was away longer than the chosen delay. */
    fun onReturn(now: Long = System.currentTimeMillis()) {
        if (enabled && unlocked && leftAt > 0 && now - leftAt >= delay.millis) unlocked = false
    }

    /** Whether the phone itself has a screen lock, which can reset a forgotten PIN. */
    fun deviceHasLock(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
}
