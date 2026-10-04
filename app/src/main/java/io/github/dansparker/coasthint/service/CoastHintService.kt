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
import io.github.dansparker.coasthint.core.Calibration
import io.github.dansparker.coasthint.core.CalibrationResult
import io.github.dansparker.coasthint.core.CalibrationSample
import io.github.dansparker.coasthint.core.CoastAdvisor
import io.github.dansparker.coasthint.core.Evaluation
import io.github.dansparker.coasthint.core.SpeedFilter
import io.github.dansparker.coasthint.core.mpsToKmh
import io.github.dansparker.coasthint.location.GpsSpeedSource
import io.github.dansparker.coasthint.location.PositionFix
import io.github.dansparker.coasthint.log.TripLogEntry
import io.github.dansparker.coasthint.log.TripLogger
import io.github.dansparker.coasthint.osmand.OsmAndConnection
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import io.github.dansparker.coasthint.output.CueOutputs
import io.github.dansparker.coasthint.settings.AppSettings
import io.github.dansparker.coasthint.settings.SettingsRepository
import io.github.dansparker.coasthint.speedlimit.Maxspeed
import io.github.dansparker.coasthint.speedlimit.OverpassClient
import io.github.dansparker.coasthint.speedlimit.CorridorSpeedLimitProvider
import io.github.dansparker.coasthint.speedlimit.OfflineLoader
import io.github.dansparker.coasthint.speedlimit.OfflineRoads
import io.github.dansparker.coasthint.speedlimit.OverpassLoader
import io.github.dansparker.coasthint.speedlimit.SelectingLoader
import io.github.dansparker.coasthint.speedlimit.SpeedLimitProvider
import io.github.dansparker.coasthint.ui.describeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Runs while driving: combines OsmAnd's next maneuver and the speed limits ahead with the own
 * GPS speed, asks the [CoastAdvisor] and gives the cue. A foreground service so it keeps working
 * with the screen off and OsmAnd in front. Also records coast-down runs for calibration and
 * writes the trip log.
 */
class CoastHintService : LifecycleService() {
    private lateinit var osmAnd: OsmAndConnection
    private lateinit var cueOutputs: CueOutputs
    private lateinit var speedLimits: SpeedLimitProvider
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tripLogger: TripLogger
    private val advisor = CoastAdvisor()
    private val speedFilter = SpeedFilter()
    private val cueLock = Mutex()
    private var settings = AppSettings()
    private var calibrationSamples: MutableList<CalibrationSample>? = null
    private var started = false
    private var shownNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        osmAnd = OsmAndConnection(this, lifecycleScope)
        cueOutputs = CueOutputs(this)
        settingsRepository = SettingsRepository(this)
        tripLogger = TripLogger(tripDirectory(this))
        val offlineRoads = OfflineRoads.get(this)
        // Setting and server are read on every request, so changes apply to the next load.
        speedLimits = CorridorSpeedLimitProvider(
            scope = lifecycleScope,
            loader = SelectingLoader(
                setting = { settings.speedLimitSource },
                offlineBounds = { withContext(Dispatchers.IO) { runCatching { offlineRoads.info()?.bounds }.getOrNull() } },
                online = OverpassLoader({ query -> OverpassClient(settings.overpassServer).fetch(query) }),
                offline = OfflineLoader(offlineRoads),
            ),
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    @SuppressLint("InlinedApi") // ServiceCompat ignores the service type below API 29.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CALIBRATION_START -> startCalibration()
            ACTION_CALIBRATION_STOP -> stopCalibration()
        }
        if (!hasLocationPermission(this)) {
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
            settingsRepository.settings.collect(::applySettings)
        }
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
            speedLimits.status.collect { status -> CoastHintRuntime.update { it.copy(speedLimitStatus = status) } }
        }
        lifecycleScope.launch {
            GpsSpeedSource(this@CoastHintService).fixes().collect(::onFix)
        }
    }

    private fun applySettings(new: AppSettings) {
        val wasLogging = tripLogger.currentFile != null
        settings = new
        advisor.settings = new.effectiveCoast
        cueOutputs.settings = new.output
        if (new.tripLogEnabled && !wasLogging) {
            tripLogger.start(System.currentTimeMillis())
        } else if (!new.tripLogEnabled && wasLogging) {
            tripLogger.stop()
        }
    }

    private fun onFix(fix: PositionFix) {
        val speed = fix.speedMps ?: return
        calibrationSamples?.let { samples ->
            samples += CalibrationSample(fix.elapsedMillis, speed)
            CoastHintRuntime.update { it.copy(calibration = it.calibration.copy(samples = samples.size)) }
        }
        val driving = speedFilter.update(fix.elapsedMillis, speed)
        // OsmAnd stops sending when navigation ends; ignore a stale last maneuver.
        val nav = osmAnd.navigation.value
            ?.takeIf { fix.elapsedMillis - it.receivedAtMillis <= NAV_MAX_AGE_MILLIS }
        val speedLimit = speedLimits.lookup(fix, mpsToKmh(driving.speedMps))
        val evaluation = advisor.evaluate(driving, nav?.info, speedLimit.ahead)
        // No cues while measuring: the driver is already coasting on purpose.
        val cue = evaluation.cue?.takeIf { calibrationSamples == null }
        CoastHintRuntime.update {
            it.copy(
                driving = driving,
                speedLimit = speedLimit,
                evaluation = evaluation,
                lastCue = cue?.let { c -> CueRecord(c.event, SystemClock.elapsedRealtime()) } ?: it.lastCue,
            )
        }
        if (cue != null) {
            lifecycleScope.launch { cueLock.withLock { cueOutputs.announce(cue) } }
        }
        tripLogger.append(
            TripLogEntry(
                wallTimeMillis = System.currentTimeMillis(),
                elapsedMillis = fix.elapsedMillis,
                lat = fix.lat,
                lon = fix.lon,
                speedMps = driving.speedMps,
                accelerationMps2 = driving.accelerationMps2,
                currentLimit = speedLimit.current,
                navDistanceM = nav?.info?.distanceToM,
                navTurnType = nav?.info?.turnType,
                evaluation = evaluation.copy(cue = cue),
            ),
        )
        refreshNotification()
    }

    private fun startCalibration() {
        calibrationSamples = mutableListOf()
        CoastHintRuntime.update {
            it.copy(calibration = CalibrationState(active = true, mode = settings.coast.mode))
        }
    }

    private fun stopCalibration() {
        val samples = calibrationSamples ?: return
        calibrationSamples = null
        val mode = settings.coast.mode
        val result = Calibration.analyze(samples)
        CoastHintRuntime.update {
            it.copy(calibration = CalibrationState(active = false, mode = mode, lastResult = result))
        }
        if (result is CalibrationResult.Success) {
            lifecycleScope.launch { settingsRepository.addMeasurement(mode, result.measurement) }
        }
    }

    private fun refreshNotification() {
        val state = CoastHintRuntime.state.value
        val osmAndText = getString(
            if (state.osmAnd is OsmAndStatus.Connected) R.string.notification_osmand_ok else R.string.notification_osmand_missing,
        )
        val limitText = when (val limit = state.speedLimit?.current) {
            is Maxspeed.Limit -> getString(R.string.notification_limit, limit.kmh)
            Maxspeed.Unlimited -> getString(R.string.notification_unlimited)
            null -> getString(R.string.notification_limit_unknown)
        }
        val text = "$osmAndText · $limitText · ${describeNext(state.evaluation)}"
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
        tripLogger.stop()
        cueOutputs.shutdown()
        CoastHintRuntime.reset()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "coast_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.dansparker.coasthint.STOP"
        private const val ACTION_CALIBRATION_START = "io.github.dansparker.coasthint.CALIBRATION_START"
        private const val ACTION_CALIBRATION_STOP = "io.github.dansparker.coasthint.CALIBRATION_STOP"
        private const val NAV_MAX_AGE_MILLIS = 5_000L
        private const val VOICE_LOG_SIZE = 10
        private const val DISTANCE_STEP_M = 10

        fun tripDirectory(context: Context) = File(context.filesDir, "trips")

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CoastHintService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoastHintService::class.java))
        }

        /** Only valid while the service is running. */
        fun startCalibration(context: Context) {
            context.startService(Intent(context, CoastHintService::class.java).setAction(ACTION_CALIBRATION_START))
        }

        fun stopCalibration(context: Context) {
            context.startService(Intent(context, CoastHintService::class.java).setAction(ACTION_CALIBRATION_STOP))
        }
    }
}
