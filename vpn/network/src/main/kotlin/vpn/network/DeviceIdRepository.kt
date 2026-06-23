package vpn.network

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import java.util.UUID

class DeviceIdRepository(
    private val storage: VpnPrefsStorage,
    private val contentResolver: ContentResolver,
) {
    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        val stored = storage.getDeviceId()
        if (stored != null) return stored

        val newId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotEmpty() && it != "9774d56d682e549c" }
            ?.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
            ?: UUID.randomUUID().toString()

        storage.saveDeviceId(newId)
        return newId
    }
}
