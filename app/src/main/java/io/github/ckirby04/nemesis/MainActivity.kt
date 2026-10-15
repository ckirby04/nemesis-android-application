package io.github.ckirby04.nemesis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ckirby04.nemesis.ui.NemesisApp
import io.github.ckirby04.nemesis.ui.theme.NemesisTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Run to open, from a "Results ready" notification tap; cleared once handled. */
    private val openRun = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readRunExtra(intent)
        setContent {
            NemesisTheme {
                NemesisApp(openRun = openRun, onRunOpened = { openRun.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readRunExtra(intent)
    }

    private fun readRunExtra(intent: Intent?) {
        intent?.getLongExtra(EXTRA_RUN_ID, -1L)?.takeIf { it >= 0 }?.let { openRun.value = it }
    }

    companion object {
        const val EXTRA_RUN_ID = "io.github.ckirby04.nemesis.extra.RUN_ID"
    }
}
