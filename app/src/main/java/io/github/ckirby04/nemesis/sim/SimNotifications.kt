package io.github.ckirby04.nemesis.sim

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.ckirby04.nemesis.MainActivity
import io.github.ckirby04.nemesis.R
import io.github.ckirby04.nemesis.data.SimState

/** Notification channels and builders for the simulation foreground service. */
object SimNotifications {
    const val CHANNEL_PROGRESS = "simulation_progress"
    const val CHANNEL_RESULTS = "simulation_results"
    const val ID_PROGRESS = 1
    const val ID_RESULT = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, "Simulation progress", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while a simulation is running" },
                NotificationChannel(CHANNEL_RESULTS, "Finished simulations", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Lets you know when results are ready while the app is in the background" },
            ),
        )
    }

    /** Opens the app; with [runId], straight to that run's results. */
    private fun openApp(context: Context, runId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (runId != null) putExtra(MainActivity.EXTRA_RUN_ID, runId)
        }
        return PendingIntent.getActivity(
            context, runId?.toInt() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelRun(context: Context): PendingIntent = PendingIntent.getService(
        context, 0,
        Intent(context, SimulationService::class.java).setAction(SimulationService.ACTION_CANCEL),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun progress(context: Context, state: SimState): Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_nemesis)
            .setContentTitle("Simulating your table")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .addAction(0, "Cancel", cancelRun(context))
        if (state is SimState.Running) {
            val pct = (state.progress * 100).toInt()
            b.setContentText("${state.villainTag} · $pct%").setProgress(100, pct, false)
        } else {
            b.setContentText("Reading your table…").setProgress(0, 0, true)
        }
        return b.build()
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Post "Results ready" / "Simulation failed" for a terminal [state], but only while the user isn't
     * looking at the app (in the foreground it navigates to the results itself). Call on the main thread.
     */
    @Suppress("MissingPermission") // guarded by canNotify()
    fun announceIfBackground(context: Context, state: SimState) {
        val n = when (state) {
            is SimState.Done -> done(context, state.runId)
            is SimState.Failed -> failed(context, state.message)
            else -> return
        }
        val inBackground = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inBackground && canNotify(context)) NotificationManagerCompat.from(context).notify(ID_RESULT, n)
    }

    fun done(context: Context, runId: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_nemesis)
            .setContentTitle("Results ready")
            .setContentText("Tap to see which hands print against this table.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context, runId))
            .build()

    fun failed(context: Context, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_nemesis)
            .setContentTitle("Simulation failed")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
}
