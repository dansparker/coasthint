package io.github.dansparker.coasthint.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.dansparker.coasthint.MainActivity
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.CoastAdvisor
import io.github.dansparker.coasthint.core.Evaluation
import io.github.dansparker.coasthint.core.SpeedFilter
import io.github.dansparker.coasthint.location.GpsSpeedSource
import io.github.dansparker.coasthint.location.SpeedSample
import io.github.dansparker.coasthint.osmand.OsmAndConnection
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import io.github.dansparker.coasthint.output.CueOutput
import io.github.dansparker.coasthint.output.ToneCueOutput
import io.github.dansparker.coasthint.ui.describeEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Runs while driving: combines OsmAnd's next maneuver with the own GPS speed, asks the
 * [CoastAdvisor] and plays the cue. A foreground service so it keeps working with the screen
 * off and OsmAnd in front.
 */
class CoastHintService : LifecycleService() {
    private lateinit var osmAnd: OsmAndConnection
    private lateinit var cueOutput: CueOutput
    private val advisor = CoastAdvisor()
    private val speedFilter = SpeedFilter()
    private val cueLock = Mutex()
    private var started = false
    private var shownNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        osmAnd = OsmAndConnection(this, lifecycleScope)
        cueOutput = ToneCueOutput(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    @SuppressLint("InlinedApi") // ServiceCompat ignores the service type below API 29.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(getString(R.string.notification_starting)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            startPipeline()
        }
        return START_NOT_STICKY
    }

    private fun startPipeline() {
        CoastHintRuntime.update { it.copy(running = true) }
        osmAnd.start()
        lifecycleScope.launch {
            osmAnd.status.collect { status ->
                CoastHintRuntime.update { it.copy(osmAnd = status) }
                refreshNotification()
            }
        }
        lifecycleScope.launch {
            osmAnd.navigation.collect { nav -> CoastHintRuntime.update { it.copy(navigation = nav) } }
        }
        lifecycleScope.launch {
            osmAnd.voiceMessages.collect { prompts ->
                CoastHintRuntime.update { it.copy(voiceLog = (prompts + it.voiceLog).take(VOICE_LOG_SIZE)) }
            }
        }
        lifecycleScope.launch {
            GpsSpeedSource(this@CoastHintService).speeds().collect(::onSpeed)
        }
    }

    private fun onSpeed(sample: SpeedSample) {
        val driving = speedFilter.update(sample.elapsedMillis, sample.speedMps)
        // OsmAnd stops sending when navigation ends; ignore a stale last maneuver.
        val nav = osmAnd.navigation.value
            ?.takeIf { sample.elapsedMillis - it.receivedAtMillis <= NAV_MAX_AGE_MILLIS }
        val evaluation = advisor.evaluate(driving, nav?.info, null)
        val cue = evaluation.cue
        CoastHintRuntime.update {
            it.copy(
                driving = driving,
                evaluation = evaluation,
                lastCue = cue?.let { c -> CueRecord(c.event, SystemClock.elapsedRealtime()) } ?: it.lastCue,
            )
        }
        if (cue != null) {
            lifecycleScope.launch { cueLock.withLock { cueOutput.announce(cue) } }
        }
        refreshNotification()
    }

    private fun refreshNotification() {
        val state = CoastHintRuntime.state.value
        val osmAndText = getString(
            if (state.osmAnd is OsmAndStatus.Connected) R.string.notification_osmand_ok else R.string.notification_osmand_missing,
        )
        val text = "$osmAndText · ${describeNext(state.evaluation)}"
        if (text == shownNotificationText) return
        shownNotificationText = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun describeNext(evaluation: Evaluation?): String {
        val next = evaluation?.next ?: return getString(R.string.notification_no_event)
        val distance = (next.event.distanceM / DISTANCE_STEP_M).roundToInt() * DISTANCE_STEP_M
        return getString(R.string.notification_next_event, resources.describeEvent(next.event), distance)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CoastHintService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_coast)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        osmAnd.stop()
        CoastHintRuntime.reset()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "coast_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.dansparker.coasthint.STOP"
        private const val NAV_MAX_AGE_MILLIS = 5_000L
        private const val VOICE_LOG_SIZE = 10
        private const val DISTANCE_STEP_M = 10

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CoastHintService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoastHintService::class.java))
        }
    }
}
