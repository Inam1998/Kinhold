package com.inamibrahim.kinhold

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import com.inamibrahim.kinhold.ui.AppLock
import com.inamibrahim.kinhold.ui.AppSettings
import com.inamibrahim.kinhold.ui.LockScreen
import com.inamibrahim.kinhold.ui.KinholdApp
import com.inamibrahim.kinhold.ui.KinholdTheme
import com.inamibrahim.kinhold.ui.isDark

class MainActivity : ComponentActivity() {
    private val vm: com.inamibrahim.kinhold.data.KinholdViewModel by viewModels()

    /** Going to the background is the moment to save a copy if anything changed, in case the app is not opened again for a while. */
    override fun onStart() {
        super.onStart()
        AppLock.onReturn()
    }

    override fun onStop() {
        AppLock.onLeave()
        super.onStop()
        vm.autoBackupIfChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        AppLock.load(this)
        setContent {
            val mode = AppSettings.mode
            val dark = isDark(mode, isSystemInDarkTheme())
            // Light theme needs dark status and navigation icons, so the bars follow the theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                // The window behind the app is the app's own background in both themes, so nothing dark can flash between screens.
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(com.inamibrahim.kinhold.ui.Palette.Background.toArgb()))
                onDispose {}
            }
            // The lock covers the app but leaves it composed underneath, so the person returns to where they were.
            DisposableEffect(AppLock.enabled) {
                if (AppLock.enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {}
            }
            KinholdTheme(mode) {
                androidx.compose.foundation.layout.Box {
                    KinholdApp()
                    if (AppLock.enabled && !AppLock.unlocked) LockScreen()
                }
            }
        }
    }
}
