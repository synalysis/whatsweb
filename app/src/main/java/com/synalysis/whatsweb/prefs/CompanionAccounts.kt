package com.synalysis.whatsweb.prefs

import android.content.Context
import android.content.Intent
import android.telephony.PhoneNumberUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.synalysis.whatsweb.MainActivity
import com.synalysis.whatsweb.R
import com.synalysis.whatsweb.web.EngineProfiles
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Names and the selected account. The WhatsApp session itself lives in the
 * browser profile for that account, not in these preferences.
 */
class CompanionAccounts(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var items by mutableStateOf(LinkRecords.decode(prefs.getString(KEY_ACCOUNTS, null)))
        private set
    var activeId by mutableStateOf(initialActive(items))
        private set

    private var switching = false
    private var discardSelf = false

    val active: CompanionAccount
        get() = items.firstOrNull { it.id == activeId } ?: items.first()

    fun label(account: CompanionAccount): String {
        return account.name.ifBlank { appContext.getString(R.string.link_main) }
    }

    fun phoneText(account: CompanionAccount): String? {
        val digits = LinkRecords.cleanPhone(account.phone) ?: return null
        val formatted = PhoneNumberUtils.formatNumber("+$digits", Locale.getDefault().country)
        if (formatted.isNullOrBlank()) return "+$digits"
        // Keep our digits if the formatter puts a trunk prefix back in.
        if (formatted.filter { it.isDigit() } != digits) return "+$digits"
        return formatted
    }

    /**
     * The open page keeps running after [activeId] moves. Ignore its messages
     * until this process is gone, so they cannot land on the account just selected.
     */
    fun holdProfileWrites() {
        switching = true
    }

    fun releaseProfileWrites() {
        switching = false
    }

    fun rememberPhone(raw: String) {
        if (switching || discardSelf) return
        val id = EngineProfiles.installed ?: return
        val phone = LinkRecords.cleanPhone(raw) ?: return
        writePhone(id, phone)
    }

    /**
     * Drops the loaded profile's number. While it is still signed in, a message
     * already in flight is ignored until the login page is showing.
     */
    fun discardPhoneUntilLogout(untilLogout: Boolean) {
        if (switching) return
        discardSelf = untilLogout
        val id = EngineProfiles.installed ?: return
        writePhone(id, "")
    }

    fun noteLoggedOut() {
        discardSelf = false
    }

    fun find(id: String): CompanionAccount? = items.firstOrNull { it.id == id }

    fun setActive(id: String) {
        if (items.none { it.id == id } || activeId == id) return
        activeId = id
        prefs.edit().putString(KEY_ACTIVE, id).commit()
        publishShortcuts()
    }

    fun add(name: String): CompanionAccount? {
        if (items.size >= LinkRecords.MAX) return null
        val clean = LinkRecords.cleanName(name) ?: return null
        val account = CompanionAccount(newId(), clean)
        items = items + account
        activeId = account.id
        persist()
        publishShortcuts()
        return account
    }

    fun renameActive(name: String): Boolean {
        val clean = LinkRecords.cleanName(name) ?: return false
        items = items.map { account ->
            if (account.id == activeId) account.copy(name = clean) else account
        }
        persist()
        publishShortcuts()
        return true
    }

    /**
     * Drops the open account from the list and selects another. The browser
     * files are deleted on the next start, after this process lets them go.
     */
    fun removeActive(): Boolean {
        if (items.size < 2) return false
        val removing = activeId
        val remaining = items.filter { it.id != removing }
        items = remaining
        activeId = remaining.first().id
        prefs.edit()
            .putString(KEY_PENDING, removing)
            .putString(KEY_ACTIVE, activeId)
            .putString(KEY_ACCOUNTS, LinkRecords.encode(remaining))
            .commit()
        publishShortcuts()
        return true
    }

    /**
     * The id whose files should be deleted, if that delete has not succeeded yet.
     * An id that is not safe to delete is dropped. A failed delete stays pending.
     */
    fun pendingRemoval(): String? {
        val id = prefs.getString(KEY_PENDING, null) ?: return null
        if (!LinkRecords.isId(id) || id == activeId) {
            prefs.edit().remove(KEY_PENDING).commit()
            return null
        }
        return id
    }

    fun completeRemoval(id: String) {
        if (prefs.getString(KEY_PENDING, null) == id) {
            prefs.edit().remove(KEY_PENDING).commit()
        }
    }

    fun publishShortcuts() {
        try {
            if (items.size < 2) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(appContext)
                return
            }
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(appContext)
            if (max <= 0) return
            // Android caps this list. Accounts past the cap stay in settings.
            val ordered = listOf(active) + items.filter { it.id != activeId }
            val shortcuts = ordered.take(max).mapIndexed { index, account ->
                val detail = listOfNotNull(label(account), phoneText(account)).joinToString(" · ")
                ShortcutInfoCompat.Builder(appContext, account.id)
                    .setShortLabel(label(account))
                    .setLongLabel(detail)
                    .setRank(index)
                    .setIcon(IconCompat.createWithResource(appContext, appContext.applicationInfo.icon))
                    .setIntent(
                        Intent(appContext, MainActivity::class.java).apply {
                            action = Intent.ACTION_MAIN
                            putExtra(AccountRestart.EXTRA, account.id)
                        },
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(appContext, shortcuts)
        } catch (_: Exception) {
        }
    }

    private fun initialActive(accounts: List<CompanionAccount>): String {
        val stored = prefs.getString(KEY_ACTIVE, null)
        val id = if (stored != null && accounts.any { it.id == stored }) stored else accounts.first().id
        if (id != stored) prefs.edit().putString(KEY_ACTIVE, id).commit()
        return id
    }

    private fun writePhone(id: String, phone: String) {
        val current = items.firstOrNull { it.id == id } ?: return
        if (current.phone == phone) return
        items = items.map { account ->
            if (account.id == id) account.copy(phone = phone) else account
        }
        persist()
        publishShortcuts()
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_ACCOUNTS, LinkRecords.encode(items))
            .putString(KEY_ACTIVE, activeId)
            .commit()
    }

    private fun newId(): String {
        repeat(4) {
            val id = "a" + UUID.randomUUID().toString().replace("-", "").take(12)
            if (LinkRecords.isId(id) && items.none { it.id == id }) return id
        }
        return "a" + System.currentTimeMillis().toString(16)
    }

    private companion object {
        const val PREFS = "whatsweb"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ACTIVE = "account_active"
        const val KEY_PENDING = "account_pending_delete"
    }
}

object AccountRestart {
    const val EXTRA = "com.synalysis.whatsweb.extra.ACCOUNT"

    private val leaving = AtomicBoolean(false)

    /**
     * [release] closes the open page and calls its argument when that profile's
     * storage has been asked to flush. The process exits only after that.
     */
    fun rebirth(context: Context, release: ((() -> Unit) -> Unit)? = null) {
        if (!leaving.compareAndSet(false, true)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = launch?.component
        if (component == null) {
            leaving.set(false)
            return
        }
        val restart = Intent.makeRestartActivityTask(component)
        restart.setPackage(context.packageName)
        val go = {
            try {
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Throwable) {
            }
            context.startActivity(restart)
            kotlin.system.exitProcess(0)
        }
        if (release == null) go() else release(go)
    }
}
