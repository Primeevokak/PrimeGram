package vpn.sdk

data class AppUpdateResult(
    val version: String,
    val versionCode: Int,
    val fileUrl: String,
    val changelog: String?,
    val isRequired: Boolean,
)