package vpn.network

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object VpnNetworkFactory {

    private const val TIMEOUT_SECONDS = 20L

    @Volatile
    private var httpClient: HttpClient? = null

    @Volatile
    private var appConfigRepository: AppConfigRepository? = null

    @Volatile
    private var vpnApi: VpnApi? = null

    @Volatile
    private var deviceIdRepository: DeviceIdRepository? = null

    @Volatile
    private var appUpdateRepository: AppUpdateRepository? = null

    @Volatile
    private var registrationRepository: RegistrationRepository? = null

    @Volatile
    private var isDebug: Boolean = false

    @Synchronized
    fun setup(context: Context, debug: Boolean = false) {
        httpClient?.close()

        isDebug = debug
        val storage = VpnPrefsStorage(context)
        val deviceIdRepo = DeviceIdRepository(storage, context.contentResolver)
        deviceIdRepository = deviceIdRepo

        val client = createHttpClient(deviceIdRepo)
        httpClient = client

        val endpoints = AppConfigEndpoints()
        val configApi = AppConfigApi(endpoints, client)
        val configRepo = AppConfigRepository(configApi, storage)
        appConfigRepository = configRepo

        val updateApi = AppUpdateApi(endpoints, client)
        appUpdateRepository = AppUpdateRepository(updateApi)

        vpnApi = VpnApi(client, configRepo)

        val registerApi = RegisterApi(client, configRepo)
        registrationRepository = RegistrationRepository(registerApi, deviceIdRepo, storage)
    }

    fun getHttpClient(): HttpClient {
        return httpClient ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    fun getAppConfigRepository(): AppConfigRepository {
        return appConfigRepository ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    fun getVpnApi(): VpnApi {
        return vpnApi ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    fun getDeviceIdRepository(): DeviceIdRepository {
        return deviceIdRepository ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    fun getAppUpdateRepository(): AppUpdateRepository {
        return appUpdateRepository ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    fun getRegistrationRepository(): RegistrationRepository {
        return registrationRepository ?: throw IllegalStateException("VpnNetworkFactory is not initialized. Call setup() first.")
    }

    private fun createHttpClient(deviceIdRepo: DeviceIdRepository): HttpClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }, contentType = ContentType.Any)
            }
            install(DefaultRequest) {
                header("X-Device-Id", deviceIdRepo.getDeviceId())
                header("X-Supported-Awg-Version", "1.5")
            }
            if (isDebug) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.d("VpnHttp", message)
                        }
                    }
                    level = LogLevel.ALL
                }
            }
        }
    }
}
