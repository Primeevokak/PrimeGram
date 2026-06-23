package vpn.network

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path

class RegisterApi(
    private val httpClient: HttpClient,
    private val configRepository: AppConfigRepository,
) {
    suspend fun register(request: RegisterRequest): HttpResponse {
        val baseUrl = configRepository.getRegisterBaseUrl()
        val path = configRepository.getRegisterPath()
        return httpClient.post(baseUrl) {
            url { path(path) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
