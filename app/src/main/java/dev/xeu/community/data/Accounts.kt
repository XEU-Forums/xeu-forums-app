package dev.xeu.community.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore("xeu_accounts")

/** A saved forum login. The session cookies are kept encrypted with a key in the Android Keystore. */
data class SavedAccount(
    val userId: Int,
    val username: String,
    val avatarUrl: String?,
    internal val encryptedCookies: String,
)

/** AES-GCM with a non-exportable Keystore key. */
private object Vault {
    private const val ALIAS = "xeu_accounts_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(enc: String): String? = runCatching {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()
}

/**
 * Multi-account support. The forum session lives in WebView cookies (shared with OkHttp), so
 * switching accounts means swapping the xiaomi.eu cookie set.
 */
class Accounts(private val context: Context) {
    private object K {
        val list = stringPreferencesKey("accounts")
        val active = intPreferencesKey("active_user_id")
    }

    val accounts: Flow<List<SavedAccount>> = context.accountStore.data.map { decode(it[K.list]) }
    val activeId: Flow<Int?> = context.accountStore.data.map { it[K.active] }

    suspend fun list() = accounts.first()
    suspend fun active(): SavedAccount? {
        val id = activeId.first() ?: return null
        return list().firstOrNull { it.userId == id }
    }

    private val cm get() = CookieManager.getInstance()
    private val cookieUrls = listOf("https://www.xiaomi.eu/", "https://xiaomi.eu/")

    private fun currentCookies(): String =
        cookieUrls.mapNotNull { cm.getCookie(it) }.flatMap { it.split(';') }.map { it.trim() }
            .filter { it.isNotBlank() }.distinctBy { it.substringBefore('=') }.joinToString("; ")

    private suspend fun clearCookies() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont -> cm.removeAllCookies { cont.resume(Unit) } }
        cm.flush()
    }

    private suspend fun restoreCookies(raw: String) = withContext(Dispatchers.Main) {
        raw.split(';').map { it.trim() }.filter { '=' in it }.forEach { kv ->
            // One year; the forum itself decides when the session is really over.
            cm.setCookie("https://www.xiaomi.eu/", "$kv; Domain=.xiaomi.eu; Path=/; Secure; Max-Age=31536000")
        }
        cm.flush()
    }

    /** Saves (or refreshes) the currently logged-in session as an account and makes it active. */
    suspend fun saveCurrent(me: Me) {
        val cookies = withContext(Dispatchers.Main) { currentCookies() }
        if (cookies.isBlank() || me.userId == 0) return
        val acc = SavedAccount(me.userId, me.username, me.avatarUrl, Vault.encrypt(cookies))
        context.accountStore.edit { p ->
            val cur = decode(p[K.list]).filter { it.userId != me.userId }
            p[K.list] = encode(cur + acc)
            p[K.active] = me.userId
        }
    }

    suspend fun switchTo(userId: Int): Boolean {
        val acc = list().firstOrNull { it.userId == userId } ?: return false
        val cookies = Vault.decrypt(acc.encryptedCookies) ?: return false
        // Keep the current session's latest cookies before leaving it.
        active()?.let { cur ->
            val now = withContext(Dispatchers.Main) { currentCookies() }
            if (now.isNotBlank()) updateCookies(cur.userId, now)
        }
        clearCookies()
        restoreCookies(cookies)
        context.accountStore.edit { it[K.active] = userId }
        Http.loggedIn.value = null
        return true
    }

    private suspend fun updateCookies(userId: Int, cookies: String) = context.accountStore.edit { p ->
        p[K.list] = encode(decode(p[K.list]).map {
            if (it.userId == userId) it.copy(encryptedCookies = Vault.encrypt(cookies)) else it
        })
    }

    /** Prepares for logging into another account: the current one stays saved. */
    suspend fun startAddAccount() {
        active()?.let { cur ->
            val now = withContext(Dispatchers.Main) { currentCookies() }
            if (now.isNotBlank()) updateCookies(cur.userId, now)
        }
        clearCookies()
        context.accountStore.edit { it.remove(K.active) }
        Http.loggedIn.value = false
    }

    /**
     * Signs out on the forum (ending the session server-side), forgets the account and
     * switches to another saved one if there is one. Returns the account now active, if any.
     */
    suspend fun logout(userId: Int): SavedAccount? {
        val wasActive = activeId.first() == userId
        if (wasActive) runCatching { ForumRepository.logoutOnForum() }
        context.accountStore.edit { p ->
            p[K.list] = encode(decode(p[K.list]).filter { it.userId != userId })
            if (p[K.active] == userId) p.remove(K.active)
        }
        if (!wasActive) return active()
        clearCookies()
        Http.loggedIn.value = false
        val next = list().firstOrNull() ?: return null
        return if (switchTo(next.userId)) next else null
    }

    private fun encode(list: List<SavedAccount>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.userId).put("name", it.username)
                .put("avatar", it.avatarUrl ?: "").put("c", it.encryptedCookies))
        }
    }.toString()

    private fun decode(s: String?): List<SavedAccount> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SavedAccount(o.getInt("id"), o.getString("name"), o.optString("avatar").ifBlank { null }, o.getString("c"))
        }
    }.getOrDefault(emptyList())
}
