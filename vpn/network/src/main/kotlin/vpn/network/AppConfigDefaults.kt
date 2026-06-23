package vpn.network

object AppConfigDefaults {

    val config = AppConfig(
        baseUrl = "https://web-api.vpgram.click",
        anonymousKeyPath = "/client-api/v1/download-anonymous-key",
        registerBaseUrl = "https://vpg.colgonet.top",
        registerPath = "/auth/register",
    )
}
