package com.synalysis.whatsweb.ui.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.synalysis.whatsweb.web.AudioEndpoint
import com.synalysis.whatsweb.web.AudioKind
import com.synalysis.whatsweb.web.CallRoutes
import com.synalysis.whatsweb.web.FoundOutput

/**
 * Routes a call between the earpiece, the loudspeaker, and a connected headset.
 * The choice stays in effect for the rest of the call, including when the
 * microphone opens and Android switches into voice-call mode.
 */
internal class CallAudio(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listening = false
    private var applied = false
    private var chosenId: String? = null
    private var afterConnect: (() -> Unit)? = null
    private var applying = false

    internal var requestBluetooth: (() -> Unit)? = null

    var routes by mutableStateOf<List<AudioEndpoint>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    private var watch: Any? = null

    fun setActive(active: Boolean) {
        if (active == listening) return
        listening = active
        if (audio == null) return
        if (active) {
            register()
            refresh()
        } else {
            unregister()
            afterConnect = null
            if (applied) restore()
            applied = false
            chosenId = null
            routes = emptyList()
            selectedId = null
        }
    }

    fun onBluetoothResult(granted: Boolean) {
        val next = afterConnect
        afterConnect = null
        if (granted) next?.invoke()
    }

    fun select(id: String) {
        if (audio == null || routes.none { it.id == id }) return
        chosenId = id
        selectedId = id
        applied = true
        apply(id)
    }

    private fun register() {
        val manager = audio ?: return
        manager.registerAudioDeviceCallback(devices, main)
        if (Build.VERSION.SDK_INT < 31) return
        val created = RouteWatch(manager, ::onMode, ::onSystemRoute)
        created.register(ContextCompat.getMainExecutor(app))
        watch = created
    }

    private fun unregister() {
        val manager = audio ?: return
        manager.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31) (watch as? RouteWatch)?.unregister()
        watch = null
    }

    private fun refresh() {
        val manager = audio ?: return
        val next = CallRoutes.endpoints(outputs(manager))
        routes = next
        val systemId = systemRoute(manager)
        val kept = chosenId?.takeIf { id -> next.any { it.id == id } }
        selectedId = CallRoutes.shown(kept, next, systemId)
        if (applied && kept == null) {
            chosenId = selectedId
            selectedId?.let { apply(it) }
        }
    }

    private fun onMode(mode: Int) {
        if (!applied || mode != AudioManager.MODE_IN_COMMUNICATION) return
        chosenId?.let { apply(it) }
    }

    private fun onSystemRoute(device: AudioDeviceInfo?) {
        if (applied) return
        val id = device?.let { CallRoutes.idFor(it.type, it.id) } ?: return
        if (routes.any { it.id == id }) selectedId = id
    }

    private fun apply(id: String) {
        if (applying) return
        val manager = audio ?: return
        val kind = routes.firstOrNull { it.id == id }?.kind ?: return
        applying = true
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val match = communicationDevice(manager, id, kind)
                if (match != null && manager.setCommunicationDevice(match)) return
            }
            if (kind == AudioKind.BLUETOOTH && !bluetoothReady()) {
                val ask = requestBluetooth
                if (ask != null) {
                    afterConnect = { apply(id) }
                    ask()
                    return
                }
            }
            applyLegacy(manager, kind)
        } finally {
            applying = false
        }
    }

    private fun bluetoothReady(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun restore() {
        val manager = audio ?: return
        if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
        manager.isSpeakerphoneOn = false
        sco(manager, false)
    }

    @RequiresApi(31)
    private fun communicationDevice(manager: AudioManager, id: String, kind: AudioKind): AudioDeviceInfo? {
        val devices = manager.availableCommunicationDevices
        return when (kind) {
            AudioKind.EARPIECE,
            AudioKind.SPEAKER,
            -> devices.firstOrNull { CallRoutes.kind(it.type) == kind }
            AudioKind.WIRED,
            AudioKind.BLUETOOTH,
            -> devices.firstOrNull { CallRoutes.idFor(it.type, it.id) == id }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(manager: AudioManager, kind: AudioKind) {
        when (kind) {
            AudioKind.SPEAKER -> {
                sco(manager, false)
                manager.isSpeakerphoneOn = true
            }
            AudioKind.BLUETOOTH -> {
                manager.isSpeakerphoneOn = false
                sco(manager, true)
            }
            else -> {
                sco(manager, false)
                manager.isSpeakerphoneOn = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sco(manager: AudioManager, on: Boolean) {
        // setCommunicationDevice can fail on Android 12 and newer. Starting the
        // call route here is what still connects a Bluetooth headset.
        try {
            if (on) manager.startBluetoothSco() else manager.stopBluetoothSco()
        } catch (_: SecurityException) {
        }
    }

    private fun outputs(manager: AudioManager): List<FoundOutput> {
        val listed = if (Build.VERSION.SDK_INT >= 31) {
            manager.availableCommunicationDevices
        } else {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        return listed.map { device ->
            FoundOutput(device.type, device.id, device.productName?.toString().orEmpty())
        }
    }

    @Suppress("DEPRECATION")
    private fun systemRoute(manager: AudioManager): String? {
        if (Build.VERSION.SDK_INT >= 31) {
            val current = manager.communicationDevice
            if (current != null) return CallRoutes.idFor(current.type, current.id)
        }
        return if (manager.isSpeakerphoneOn) CallRoutes.SPEAKER else null
    }
}

@RequiresApi(31)
private class RouteWatch(
    private val manager: AudioManager,
    private val onMode: (Int) -> Unit,
    private val onRoute: (AudioDeviceInfo?) -> Unit,
) {
    private val modes = AudioManager.OnModeChangedListener { mode -> onMode(mode) }
    private val routes = AudioManager.OnCommunicationDeviceChangedListener { device -> onRoute(device) }

    fun register(executor: java.util.concurrent.Executor) {
        manager.addOnModeChangedListener(executor, modes)
        manager.addOnCommunicationDeviceChangedListener(executor, routes)
    }

    fun unregister() {
        manager.removeOnModeChangedListener(modes)
        manager.removeOnCommunicationDeviceChangedListener(routes)
    }
}
