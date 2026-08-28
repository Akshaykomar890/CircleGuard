package com.nebulaiq.assignment.presentation.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Small Redux-style foundation shared by feature ViewModels.
 * Reducers stay pure; asynchronous work returns through dispatching another event.
 */
abstract class BaseViewModel<State : Any, Event : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {
    private val events = Channel<Event>(capacity = Channel.UNLIMITED)
    private val _state = MutableStateFlow(initialState)
    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)

    val state: StateFlow<State> = _state.asStateFlow()
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            for (event in events) {
                _state.value = reduce(_state.value, event)
                viewModelScope.launch { handleSideEffect(event) }
            }
        }
    }

    fun dispatch(event: Event) {
        events.trySend(event)
    }

    protected abstract fun reduce(state: State, event: Event): State

    protected open suspend fun handleSideEffect(event: Event) = Unit

    protected suspend fun emitEffect(effect: Effect) {
        _effects.send(effect)
    }

    override fun onCleared() {
        events.close()
        _effects.close()
        super.onCleared()
    }
}
