package vpn.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class AppConfigApi(
    private val endpoints: AppConfigEndpoints,
    private val httpClient: HttpClient,
) {
    suspend fun getGithubConfig(): HttpResponse = httpClient.get(endpoints.githubPath)
    suspend fun getGoogleConfig(): HttpResponse = httpClient.get(endpoints.googlePath)
    suspend fun getYandexConfig(): HttpResponse = httpClient.get(endpoints.yandexPath)
}
