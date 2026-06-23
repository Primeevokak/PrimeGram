package vpn.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    @SerialName("version")
    val version: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("file_url")
    val fileUrl: String,
    @SerialName("changelog")
    val changelog: String? = null,
    @SerialName("is_required")
    val isRequired: Boolean = false,
)
