package vpn.network

import android.util.Log

class AppUpdateRepository(
    private val appUpdateApi: AppUpdateApi,
) {
    companion object {
        private const val TAG = "AppUpdateRepository"
    }

    suspend fun fetchUpdateInfo(): NetworkResult<AppUpdateInfo> {
        var result = safeMapDirectResponse<AppUpdateInfo> { appUpdateApi.getGithubUpdate() }
        if (result !is NetworkResult.Success) {
            Log.d(TAG, "GitHub failed, trying Google...")
            result = safeMapDirectResponse<AppUpdateInfo> { appUpdateApi.getGoogleUpdate() }
            if (result !is NetworkResult.Success) {
                Log.d(TAG, "Google failed, trying Yandex...")
                result = safeMapDirectResponse<AppUpdateInfo> { appUpdateApi.getYandexUpdate() }
            }
        }
        if (result is NetworkResult.Success) {
            Log.d(TAG, "Update info fetched: v${result.data.version} (${result.data.versionCode})")
        } else {
            Log.w(TAG, "Failed to fetch update info from all sources")
        }
        return result
    }
}
