package vpn.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.path
import vpn.util.splitPathAndQueryParams

class VpnApi(
    private val httpClient: HttpClient,
    private val configRepository: AppConfigRepository,
) {
    companion object {
        private const val TAG = "VpnApi"
    }

    suspend fun getAnonymousKey(): NetworkResult<String> {
        val baseUrl = configRepository.getBaseUrl()
        val keyPath = configRepository.getAnonymousKeyPath()
        val (path, queryParams) = splitPathAndQueryParams(keyPath)
        return try {
            val response = httpClient.get(baseUrl) {
                url {
                    path(path)
                    for (q in queryParams) {
                        parameters.append(q.key, q.value)
                    }
                }
            }
            if (response.status.value in 200..299) {
                NetworkResult.Success(data = response.bodyAsText())
            } else {
                mapErrorResponse(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get anonymous key: $baseUrl$keyPath", e)
            NetworkResult.Failure(
                code = 999,
                body = "network request fail: ${e.message}",
            )
        }
    }
}
