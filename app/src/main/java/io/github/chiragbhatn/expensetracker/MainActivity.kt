package io.github.chiragbhatn.expensetracker

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chiragbhatn.expensetracker.ui.ExpenseTrackerNavHost
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.theme.ExpenseTrackerTheme
import io.github.chiragbhatn.expensetracker.ui.theme.isAppInDarkTheme

class MainActivity : FragmentActivity() {
    /** True while the app lock is showing. */
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ExpenseTrackerApp).container
        locked = savedInstanceState?.getBoolean(KEY_LOCKED, true) ?: true
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            val dark = isAppInDarkTheme(current.themeMode)
            // Turning the lock on doesn't lock the app straight away; it locks next time it opens.
            LaunchedEffect(current.appLock) { if (!current.appLock) locked = false }
            LaunchedEffect(dark) {
                // Status and navigation bar icons follow the app's theme, not just the system's.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            ExpenseTrackerTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    if (current.appLock && locked && canAuthenticate()) {
                        LockScreen(onUnlock = ::authenticate)
                        LaunchedEffect(Unit) { authenticate() }
                    } else {
                        ExpenseTrackerNavHost()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onRestart() {
        super.onRestart()
        // Lock again after a while away, but not when coming back from a file picker or the camera.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) locked = true
    }

    // The lock uses the system's own prompt, available from Android 9.
    private fun canAuthenticate(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val RELOCK_AFTER_MS = 5 * 60 * 1000L
        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Text("Expense Tracker is locked", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
