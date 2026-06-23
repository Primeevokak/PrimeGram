package vpn.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class RegistrationRepository(
    private val api: RegisterApi,
    private val deviceIdRepository: DeviceIdRepository,
    private val storage: VpnPrefsStorage,
) {
    companion object {
        private const val TAG = "RegistrationRepo"
    }

    suspend fun register(): NetworkResult<Unit> {
        val deviceId = deviceIdRepository.getDeviceId()
        val result = safeMapDirectResponse<RegisterResponse> {
            api.register(RegisterRequest(deviceId))
        }
        return when (result) {
            is NetworkResult.Success -> {
                val config = result.data.config
                if (!hasUsableOutbounds(config)) {
                    Log.w(TAG, "Register response has empty outbounds — rejecting")
                    return NetworkResult.Error("register response missing outbounds")
                }
                val configJson = config.toString()
                storage.saveXrayConfigJson(configJson)
                storage.saveV2rayKey(result.data.v2rayKey)
                Log.d(TAG, "Register success, v2rayKey cached, config size=${configJson.length}")
                NetworkResult.Success(Unit)
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "Register error: ${result.message}")
                result
            }
            is NetworkResult.Failure -> {
                Log.w(TAG, "Register failure: code=${result.code} body=${result.body}")
                result
            }
        }
    }

    private fun hasUsableOutbounds(config: JsonObject): Boolean {
        val outbounds = config["outbounds"] as? JsonArray ?: return false
        return outbounds.isNotEmpty()
    }

    /**
     * Returns the cached xray config JSON, or null if the cache is missing
     * or unusable (empty outbounds, malformed JSON). Self-heals by clearing
     * a poisoned cache so the next register cycle replaces it.
     */
    fun getCachedConfigJson(): String? {
        val raw = storage.getXrayConfigJson() ?: return null
        return try {
            val parsed = Json.parseToJsonElement(raw).jsonObject
            if (hasUsableOutbounds(parsed)) {
                raw
            } else {
                Log.w(TAG, "Cached config has empty outbounds — clearing")
                storage.clearXrayConfigJson()
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Cached config is malformed JSON — clearing: ${e.message}")
            storage.clearXrayConfigJson()
            null
        }
    }

    fun getCachedV2rayKey(): String? = storage.getV2rayKey()

    fun clearCachedConfig() {
        storage.clearXrayConfigJson()
    }
}
