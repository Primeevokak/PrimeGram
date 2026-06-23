package vpn.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class AppUpdateApi(
    private val endpoints: AppConfigEndpoints,
    private val httpClient: HttpClient,
) {
    suspend fun getGithubUpdate(): HttpResponse = httpClient.get(endpoints.githubUpdatePath)
    suspend fun getGoogleUpdate(): HttpResponse = httpClient.get(endpoints.googleUpdatePath)
    suspend fun getYandexUpdate(): HttpResponse = httpClient.get(endpoints.yandexUpdatePath)
}
