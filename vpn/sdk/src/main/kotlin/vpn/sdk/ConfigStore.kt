package vpn.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * The xray config built from the link the user added - the only config this SDK ever runs.
 *
 * Same prefs file and key the removed backend client used, so a server the user already had
 * selected keeps working after the update. Everything else that client kept there (a device id,
 * a backend-issued key, a remotely fetched endpoint config) is wiped on [init].
 */
internal object ConfigStore {

    private const val PREFS_NAME = "vpn_sdk_prefs"
    private const val KEY_XRAY_CONFIG_JSON = "xray_config_json"
    private val LEGACY_KEYS = arrayOf("device_id", "cached_app_config", "v2ray_key")

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        if (LEGACY_KEYS.any { p.contains(it) }) {
            val editor = p.edit()
            LEGACY_KEYS.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun getXrayConfig(): String? = prefs?.getString(KEY_XRAY_CONFIG_JSON, null)?.takeIf { it.isNotBlank() }

    fun setXrayConfig(json: String) {
        prefs?.edit()?.putString(KEY_XRAY_CONFIG_JSON, json)?.apply()
    }

    fun clearXrayConfig() {
        prefs?.edit()?.remove(KEY_XRAY_CONFIG_JSON)?.apply()
    }
}
