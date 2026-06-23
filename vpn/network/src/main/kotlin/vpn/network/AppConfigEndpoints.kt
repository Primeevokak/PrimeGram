package vpn.network

class AppConfigEndpoints {
    companion object {
        private const val CONFIG_FILE_NAME = "app-config.json"
    }

    val githubPath: String = "https://raw.githubusercontent.com/vpgram/public/refs/heads/main/vpgram/$CONFIG_FILE_NAME"
    val googlePath: String = "https://storage.googleapis.com/vpgram/$CONFIG_FILE_NAME"
    val yandexPath: String = "https://storage.yandexcloud.net/vpgram/vpgram/$CONFIG_FILE_NAME"

    val githubUpdatePath: String = "https://raw.githubusercontent.com/vpgram/public/refs/heads/main/vpgram/update-info.json"
    val googleUpdatePath: String = "https://storage.googleapis.com/vpgram/update-info.json"
    val yandexUpdatePath: String = "https://storage.yandexcloud.net/vpgram/vpgram/update-info.json"
}
