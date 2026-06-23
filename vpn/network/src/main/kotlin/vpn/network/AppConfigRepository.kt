package vpn.network

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json

class AppConfigRepository(
    private val appConfigApi: AppConfigApi,
    private val storage: VpnPrefsStorage,
) {
    companion object {
        private const val TAG = "AppConfigRepository"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Volatile
    private var cachedConfig: AppConfig? = null

    private val fetchMutex = Mutex()

    init {
        cachedConfig = loadFromStorage()
    }

    suspend fun fetchRemoteConfig(): NetworkResult<AppConfig> {
        if (!fetchMutex.tryLock()) {
            Log.d(TAG, "Fetch already in progress, returning cached config")
            return NetworkResult.Success(getConfig())
        }
        try {
            var result = safeMapDirectResponse<AppConfig> { appConfigApi.getGithubConfig() }
            if (result !is NetworkResult.Success) {
                result = safeMapDirectResponse<AppConfig> { appConfigApi.getGoogleConfig() }
                if (result !is NetworkResult.Success) {
                    result = safeMapDirectResponse<AppConfig> { appConfigApi.getYandexConfig() }
                }
            }
            if (result is NetworkResult.Success) {
                cachedConfig = result.data
                saveToStorage(result.data)
                Log.d(TAG, "Remote config fetched and cached successfully")
            } else {
                Log.w(TAG, "Failed to fetch remote config from all sources")
            }
            return result
        } finally {
            fetchMutex.unlock()
        }
    }

    fun getConfig(): AppConfig {
        return cachedConfig ?: AppConfigDefaults.config
    }

    fun getBaseUrl(): String = getConfig().baseUrl

    fun getAnonymousKeyPath(): String = getConfig().anonymousKeyPath

    fun getAnonymousKeyUrl(): String = getBaseUrl() + getAnonymousKeyPath()

    fun getRegisterPath(): String = getConfig().registerPath

    fun getRegisterBaseUrl(): String = getConfig().registerBaseUrl

    private fun saveToStorage(config: AppConfig) {
        try {
            val serialized = json.encodeToString(AppConfig.serializer(), config)
            storage.saveCachedConfig(serialized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config to storage", e)
        }
    }

    private fun loadFromStorage(): AppConfig? {
        return try {
            val serialized = storage.getCachedConfig() ?: return null
            json.decodeFromString(AppConfig.serializer(), serialized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from storage", e)
            null
        }
    }
}
