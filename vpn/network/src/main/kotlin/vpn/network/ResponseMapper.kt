package vpn.network

import android.util.Log
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import kotlinx.serialization.json.Json

private const val TAG = "VpnNetwork"

suspend inline fun <reified T> safeMapDirectResponse(call: suspend () -> HttpResponse): NetworkResult<T> {
    val decoder = Json { ignoreUnknownKeys = true }
    return try {
        val response = call()
        if (response.status.value in 200..299) {
            val text = response.bodyAsText()
            val data = decoder.decodeFromString<T>(text)
            NetworkResult.Success(data)
        } else {
            mapErrorResponse(response)
        }
    } catch (e: Exception) {
        Log.e("VpnNetwork", "Network request failed", e)
        NetworkResult.Failure(
            code = 999,
            body = "network request fail: ${e.message}",
        )
    }
}

suspend fun mapErrorResponse(response: HttpResponse): NetworkResult<Nothing> {
    return when (response.status.value) {
        in 400..499 -> {
            Log.e(TAG, "Client error ${response.status.value}: ${response.request.url}")
            NetworkResult.Error(message = response.bodyAsText())
        }
        else -> {
            Log.e(TAG, "Server error ${response.status.value}: ${response.request.url}")
            NetworkResult.Failure(
                code = response.status.value,
                body = response.bodyAsText(),
            )
        }
    }
}
