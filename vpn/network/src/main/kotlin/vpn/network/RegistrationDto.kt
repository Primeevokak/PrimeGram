package vpn.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RegisterRequest(
    @SerialName("deviceId")
    val deviceId: String,
)

@Serializable
data class RegisterResponse(
    @SerialName("v2rayKey")
    val v2rayKey: String,
    @SerialName("config")
    val config: JsonObject,
)
