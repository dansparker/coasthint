package io.github.dansparker.coasthint.osmand

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import io.github.dansparker.coasthint.core.NavInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.aidlapi.IOsmAndAidlCallback
import net.osmand.aidlapi.IOsmAndAidlInterface
import net.osmand.aidlapi.gpx.AGpxBitmap
import net.osmand.aidlapi.logcat.OnLogcatMessageParams
import net.osmand.aidlapi.navigation.ADirectionInfo
import net.osmand.aidlapi.navigation.ANavigationUpdateParams
import net.osmand.aidlapi.navigation.ANavigationVoiceRouterMessageParams
import net.osmand.aidlapi.navigation.OnVoiceNavigationParams
import net.osmand.aidlapi.search.SearchResult

sealed interface OsmAndStatus {
    data object Stopped : OsmAndStatus
    data object NotInstalled : OsmAndStatus
    data object Connecting : OsmAndStatus
    data class Connected(val packageName: String) : OsmAndStatus
    data class Retrying(val delayMillis: Long, val reason: String) : OsmAndStatus
}

/** Latest maneuver info from OsmAnd, stamped with [SystemClock.elapsedRealtime]. */
data class NavSample(val info: NavInfo, val receivedAtMillis: Long)

/**
 * Binds to OsmAnd's AIDL service, subscribes to navigation updates and reconnects with
 * backoff when OsmAnd is missing or the connection breaks. Must be used from the main thread;
 * [scope] should run on the main dispatcher.
 */
class OsmAndConnection(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<OsmAndStatus>(OsmAndStatus.Stopped)
    val status: StateFlow<OsmAndStatus> = _status.asStateFlow()

    private val _navigation = MutableStateFlow<NavSample?>(null)

    /** Next maneuver; null while not connected. OsmAnd only sends updates during navigation. */
    val navigation: StateFlow<NavSample?> = _navigation.asStateFlow()

    private val _voiceMessages = MutableSharedFlow<List<String>>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** OsmAnd's voice prompts, for debugging and logging. */
    val voiceMessages: SharedFlow<List<String>> = _voiceMessages.asSharedFlow()

    private val backoff = Backoff()
    private var running = false
    private var bound = false
    private var service: IOsmAndAidlInterface? = null
    private var navCallbackId = NO_CALLBACK
    private var voiceCallbackId = NO_CALLBACK
    private var retryJob: Job? = null

    // Called on binder threads; only touches thread-safe flows.
    private val callback = object : IOsmAndAidlCallback.Stub() {
        override fun updateNavigationInfo(directionInfo: ADirectionInfo?) {
            directionInfo ?: return
            _navigation.value = NavSample(
                NavInfo(directionInfo.distanceTo, directionInfo.turnType, directionInfo.isLeftSide),
                SystemClock.elapsedRealtime(),
            )
        }

        override fun onVoiceRouterNotify(params: OnVoiceNavigationParams?) {
            val played = params?.played.orEmpty().ifEmpty { params?.commands.orEmpty() }
            if (played.isNotEmpty()) _voiceMessages.tryEmit(played)
        }

        override fun onSearchComplete(resultSet: MutableList<SearchResult>?) = Unit
        override fun onUpdate() = Unit
        override fun onAppInitialized() = Unit
        override fun onGpxBitmapCreated(bitmap: AGpxBitmap?) = Unit
        override fun onContextMenuButtonClicked(buttonId: Int, pointId: String?, layerId: String?) = Unit
        override fun onKeyEvent(keyEvent: android.view.KeyEvent?) = Unit
        override fun onLogcatMessage(params: OnLogcatMessageParams?) = Unit
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val api = IOsmAndAidlInterface.Stub.asInterface(binder)
            service = api
            if (subscribe(api)) {
                backoff.reset()
                _status.value = OsmAndStatus.Connected(name.packageName)
            } else {
                reconnectLater("Anmeldung bei OsmAnd fehlgeschlagen")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // OsmAnd's process died. The binding stays, the system reconnects once it restarts.
            service = null
            navCallbackId = NO_CALLBACK
            voiceCallbackId = NO_CALLBACK
            _navigation.value = null
            _status.value = OsmAndStatus.Connecting
        }

        override fun onBindingDied(name: ComponentName) = reconnectLater("Verbindung zu OsmAnd verloren")

        override fun onNullBinding(name: ComponentName) = reconnectLater("OsmAnd lehnt die Verbindung ab")
    }

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        retryJob?.cancel()
        disconnect()
        _status.value = OsmAndStatus.Stopped
    }

    private fun connect() {
        val pkg = installedOsmAndPackage()
        if (pkg == null) {
            _status.value = OsmAndStatus.NotInstalled
            scheduleRetry()
            return
        }
        _status.value = OsmAndStatus.Connecting
        var flags = Context.BIND_AUTO_CREATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or Context.BIND_ALLOW_ACTIVITY_STARTS
        }
        bound = try {
            appContext.bindService(Intent(SERVICE_ACTION).setPackage(pkg), connection, flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "bindService rejected", e)
            false
        }
        if (!bound) reconnectLater("OsmAnd-Dienst nicht erreichbar")
    }

    private fun subscribe(api: IOsmAndAidlInterface): Boolean = try {
        navCallbackId = api.registerForNavigationUpdates(
            ANavigationUpdateParams().apply {
                setCallbackId(0)
                setSubscribeToUpdates(true)
            },
            callback,
        )
        voiceCallbackId = api.registerForVoiceRouterMessages(
            ANavigationVoiceRouterMessageParams().apply {
                setCallbackId(0)
                setSubscribeToUpdates(true)
            },
            callback,
        )
        navCallbackId != NO_CALLBACK
    } catch (e: RemoteException) {
        Log.w(TAG, "Subscribing to navigation updates failed", e)
        false
    }

    private fun unsubscribe() {
        val api = service ?: return
        try {
            if (navCallbackId != NO_CALLBACK) {
                api.registerForNavigationUpdates(
                    ANavigationUpdateParams().apply {
                        setCallbackId(navCallbackId)
                        setSubscribeToUpdates(false)
                    },
                    callback,
                )
            }
            if (voiceCallbackId != NO_CALLBACK) {
                api.registerForVoiceRouterMessages(
                    ANavigationVoiceRouterMessageParams().apply {
                        setCallbackId(voiceCallbackId)
                        setSubscribeToUpdates(false)
                    },
                    callback,
                )
            }
        } catch (e: RemoteException) {
            Log.w(TAG, "Unsubscribing failed", e)
        }
        navCallbackId = NO_CALLBACK
        voiceCallbackId = NO_CALLBACK
    }

    private fun disconnect() {
        unsubscribe()
        service = null
        if (bound) {
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "unbindService failed", e)
            }
            bound = false
        }
        _navigation.value = null
    }

    private fun reconnectLater(reason: String) {
        Log.i(TAG, reason)
        disconnect()
        if (!running) return
        _status.value = OsmAndStatus.Retrying(scheduleRetry(), reason)
    }

    private fun scheduleRetry(): Long {
        val delayMillis = backoff.next()
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMillis)
            if (running) connect()
        }
        return delayMillis
    }

    private fun installedOsmAndPackage(): String? = OSMAND_PACKAGES.firstOrNull { pkg ->
        try {
            appContext.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        const val TAG = "OsmAndConnection"
        const val SERVICE_ACTION = "net.osmand.aidl.OsmandAidlServiceV2"
        const val NO_CALLBACK = -1L

        /** In order of preference: OsmAnd+, OsmAnd, nightly build. */
        val OSMAND_PACKAGES = listOf("net.osmand.plus", "net.osmand", "net.osmand.dev")
    }
}
