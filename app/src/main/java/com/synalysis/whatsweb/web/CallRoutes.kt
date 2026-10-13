package com.synalysis.whatsweb.web

import android.media.AudioDeviceInfo

internal enum class AudioKind {
    EARPIECE,
    SPEAKER,
    WIRED,
    BLUETOOTH,
}

internal data class FoundOutput(
    val type: Int,
    val deviceId: Int,
    val name: String,
)

internal data class AudioEndpoint(
    val id: String,
    val kind: AudioKind,
    val name: String,
)

internal object CallRoutes {
    const val EARPIECE = "earpiece"
    const val SPEAKER = "speaker"

    fun kind(type: Int): AudioKind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioKind.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioKind.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> AudioKind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> AudioKind.BLUETOOTH
        else -> null
    }

    fun idFor(type: Int, deviceId: Int): String? = when (kind(type)) {
        AudioKind.EARPIECE -> EARPIECE
        AudioKind.SPEAKER -> SPEAKER
        AudioKind.WIRED -> "wired:$deviceId"
        AudioKind.BLUETOOTH -> "bt:$deviceId"
        null -> null
    }

    fun endpoints(found: List<FoundOutput>): List<AudioEndpoint> {
        val extras = found.mapNotNull { device ->
            val id = idFor(device.type, device.deviceId) ?: return@mapNotNull null
            val kind = kind(device.type) ?: return@mapNotNull null
            if (kind == AudioKind.EARPIECE || kind == AudioKind.SPEAKER) return@mapNotNull null
            AudioEndpoint(id, kind, device.name.trim())
        }.distinctBy { it.id }
        return listOf(
            AudioEndpoint(EARPIECE, AudioKind.EARPIECE, ""),
            AudioEndpoint(SPEAKER, AudioKind.SPEAKER, ""),
        ) + extras.sortedWith(compareBy({ it.kind }, { it.name }))
    }

    fun shown(selectedId: String?, routes: List<AudioEndpoint>, systemId: String?): String? {
        if (routes.isEmpty()) return null
        if (selectedId != null && routes.any { it.id == selectedId }) return selectedId
        if (systemId != null && routes.any { it.id == systemId }) return systemId
        return routes.firstOrNull { it.id == EARPIECE }?.id ?: routes.first().id
    }
}
