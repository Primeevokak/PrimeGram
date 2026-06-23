package vpn.base.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

open class VpnnViewModel<S : VpnnUiState>(
    initState: S,
) : ViewModel() {
    protected val uiState = MutableStateFlow<S>(initState)
    val uiStateFlow: StateFlow<S> = uiState.asStateFlow()

    protected val events = MutableStateFlow<VpnnEvent>(BaseEvent.None)
    val eventFlow: StateFlow<VpnnEvent> = events.asStateFlow()

    private val _effect = Channel<VpnnEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    open fun onAction(action: VpnnAction) {}

    @Deprecated("Use sendEffect instead")
    protected suspend fun sendEvent(event: VpnnEvent) {
        events.emit(event)
        delay(100)
        events.emit(BaseEvent.None)
    }

    protected suspend fun sendEffect(effect: VpnnEffect) {
        _effect.send(effect)
    }
}
