package app.pastillero

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.pastillero.alarm.NotificationFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastillero.domain.AppSettings
import app.pastillero.ui.nav.PastilleroNavHost
import app.pastillero.ui.theme.PastilleroTheme
import app.pastillero.ui.theme.useDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openTodayRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent) {
        if (intent.action == NotificationFactory.ACTION_OPEN_TODAY) {
            openTodayRequest++
            // Consume only this delivered Intent, so rotation does not navigate again.
            intent.action = Intent.ACTION_MAIN
        }
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("open_today_request", openTodayRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val container = (application as PastilleroApp).container
        container.scope.launch { container.medications.ensureDoses() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        openTodayRequest = savedInstanceState?.getInt("open_today_request") ?: 0
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        val container = (application as PastilleroApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = useDarkTheme(settings.theme)
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.isNavigationBarContrastEnforced = false
            }
            PastilleroTheme(theme = settings.theme) { PastilleroNavHost(container.medications, openTodayRequest) }
        }
    }
}
