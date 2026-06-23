package vpn.network

sealed class NetworkResult<out T : Any?> {
    data class Success<out T : Any?>(val data: T) : NetworkResult<T>()
    data class Error(val message: String?) : NetworkResult<Nothing>()
    data class Failure(val code: Int, val body: String) : NetworkResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    fun onSuccess(action: (value: T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (message: String?) -> Unit): NetworkResult<T> {
        if (this is Error) action(message)
        return this
    }

    fun onFailure(action: (code: Int, body: String) -> Unit): NetworkResult<T> {
        if (this is Failure) action(code, body)
        return this
    }
}
