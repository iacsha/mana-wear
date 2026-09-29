package io.github.manawear.watch

import android.content.Context

data class Config(val url: String?, val token: String?)

/**
 * Merge a config intent into the stored config. A new URL without a token in the same
 * intent clears the stored token: otherwise any app on the watch could point the URL at
 * its own server and receive the existing token on the next refresh.
 */
fun applyConfig(current: Config, url: String?, token: String?): Config {
    val newUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    val newToken = token?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        newUrl != null && newUrl != current.url -> Config(newUrl, newToken)
        newToken != null -> current.copy(token = newToken)
        else -> current
    }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun load(): Config = Config(prefs.getString(KEY_URL, null), prefs.getString(KEY_TOKEN, null))

    fun save(config: Config) {
        prefs.edit()
            .putString(KEY_URL, config.url)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_TOKEN = "token"
    }
}
