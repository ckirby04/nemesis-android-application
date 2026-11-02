package io.github.ckirby04.nemesis.sim

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ckirby04.nemesis.NemesisApplication
import io.github.ckirby04.nemesis.data.SimState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while a simulation runs, with a progress
 * notification and a Cancel action. The work itself lives in [io.github.ckirby04.nemesis.data.SimulationRunner];
 * this service only mirrors its progress. The finished/failed notification is posted by the app container
 * ([SimNotifications.announceIfBackground]), so it doesn't depend on the service still running.
 *
 * Type `shortService`: a user-started job that finishes in seconds to a couple of minutes. If the
 * system's time limit is ever hit, the service steps down and the run continues in-process.
 */
class SimulationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runner = (application as NemesisApplication).container.simulation
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            if (watcher == null) stopSelf()
            return START_NOT_STICKY
        }
        promote(SimNotifications.progress(this, runner.state.value))
        if (watcher == null) {
            watcher = scope.launch {
                runner.state.collect { s ->
                    when (s) {
                        SimState.Parsing, is SimState.Running -> notifyProgress(SimNotifications.progress(this@SimulationService, s))
                        is SimState.Done, is SimState.Failed, SimState.Idle -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun promote(n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SimNotifications.ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(SimNotifications.ID_PROGRESS, n)
        }
    }

    private var lastProgressUpdate = 0L

    /** Android drops notification updates past ~5/second, so publish at most two per second. */
    @Suppress("MissingPermission") // guarded by canNotify()
    private fun notifyProgress(n: android.app.Notification) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressUpdate < 500 || !SimNotifications.canNotify(this)) return
        lastProgressUpdate = now
        NotificationManagerCompat.from(this).notify(SimNotifications.ID_PROGRESS, n)
    }

    /**
     * The run ended, or the shortService time limit was reached: leave the foreground and stop. After a
     * timeout the run keeps going in-process, and its result is still announced by the app container.
     */
    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        watcher?.cancel()
        watcher = null
        stopSelf()
    }

    override fun onTimeout(startId: Int) = finish()
    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "io.github.ckirby04.nemesis.action.CANCEL_SIMULATION"

        /** Called when a run starts (the user just tapped Run, so the app is in the foreground). */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SimulationService::class.java))
            } catch (e: IllegalStateException) {
                // Foreground start refused (e.g. app already backgrounded): the run still completes in-process.
                Log.w("SimulationService", "Couldn't start foreground service", e)
            }
        }
    }
}
