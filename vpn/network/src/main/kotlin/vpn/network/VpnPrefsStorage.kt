package vpn.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

// TODO: унести отсюда
class VpnPrefsStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "vpn_sdk_prefs"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CACHED_CONFIG = "cached_app_config"
        private const val KEY_XRAY_CONFIG_JSON = "xray_config_json"
        private const val KEY_V2RAY_KEY = "v2ray_key"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun saveDeviceId(id: String) {
        prefs.edit { putString(KEY_DEVICE_ID, id) }
    }

    fun getCachedConfig(): String? = prefs.getString(KEY_CACHED_CONFIG, null)

    fun saveCachedConfig(json: String) {
        prefs.edit { putString(KEY_CACHED_CONFIG, json) }
    }

    fun getXrayConfigJson(): String? = prefs.getString(KEY_XRAY_CONFIG_JSON, null)

    fun saveXrayConfigJson(json: String) {
        prefs.edit { putString(KEY_XRAY_CONFIG_JSON, json) }
    }

    fun clearXrayConfigJson() {
        prefs.edit { remove(KEY_XRAY_CONFIG_JSON) }
    }

    fun getV2rayKey(): String? = prefs.getString(KEY_V2RAY_KEY, null)

    fun saveV2rayKey(key: String) {
        prefs.edit { putString(KEY_V2RAY_KEY, key) }
    }
}
