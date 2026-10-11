package app.whatsweb.prefs

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun from(raw: String?): ThemeMode {
            return entries.firstOrNull { it.name == raw } ?: SYSTEM
        }
    }
}

class UserPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("whatsweb", Context.MODE_PRIVATE)

    var welcomeDone by mutableStateOf(prefs.getBoolean(KEY_WELCOME, false))
        private set
    var phoneLayout by mutableStateOf(prefs.getBoolean(KEY_PHONE, true))
        private set
    var textZoom by mutableIntStateOf(prefs.getInt(KEY_ZOOM, 100).coerceIn(80, 150))
        private set
    var dynamicColor by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC, true))
        private set
    var appLock by mutableStateOf(prefs.getBoolean(KEY_LOCK, false))
        private set
    var hideRecents by mutableStateOf(prefs.getBoolean(KEY_SECURE, false))
        private set
    var openLinks by mutableStateOf(prefs.getBoolean(KEY_LINKS, false))
        private set
    var themeMode by mutableStateOf(ThemeMode.from(prefs.getString(KEY_THEME, null)))
        private set

    init {
        applyLinkAlias(openLinks)
    }

    fun completeWelcome() = write(KEY_WELCOME, true) { welcomeDone = true }

    fun updatePhoneLayout(value: Boolean) = write(KEY_PHONE, value) { phoneLayout = value }

    fun updateTextZoom(value: Int) {
        val zoom = value.coerceIn(80, 150)
        prefs.edit().putInt(KEY_ZOOM, zoom).apply()
        textZoom = zoom
    }

    fun updateDynamicColor(value: Boolean) = write(KEY_DYNAMIC, value) { dynamicColor = value }

    fun updateAppLock(value: Boolean) = write(KEY_LOCK, value) { appLock = value }

    fun updateHideRecents(value: Boolean) = write(KEY_SECURE, value) { hideRecents = value }

    fun updateThemeMode(value: ThemeMode) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        themeMode = value
    }

    fun updateOpenLinks(value: Boolean) {
        prefs.edit().putBoolean(KEY_LINKS, value).apply()
        openLinks = value
        applyLinkAlias(value)
    }

    private fun write(key: String, value: Boolean, assign: () -> Unit) {
        prefs.edit().putBoolean(key, value).apply()
        assign()
    }

    private fun applyLinkAlias(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        appContext.packageManager.setComponentEnabledSetting(
            ComponentName(appContext, "app.whatsweb.LinkAlias"),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val KEY_WELCOME = "welcome"
        const val KEY_PHONE = "phone_layout"
        const val KEY_ZOOM = "text_zoom"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_LOCK = "app_lock"
        const val KEY_SECURE = "hide_recents"
        const val KEY_LINKS = "open_links"
        const val KEY_THEME = "theme_mode"
    }
}
