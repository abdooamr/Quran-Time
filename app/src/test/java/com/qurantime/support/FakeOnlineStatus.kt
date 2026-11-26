package com.qurantime.support

import com.qurantime.core.net.OnlineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An [OnlineStatus] tests can flip. */
class FakeOnlineStatus(initial: Boolean = true) : OnlineStatus {
    private val state = MutableStateFlow(initial)
    override val online: StateFlow<Boolean> = state
    fun set(value: Boolean) {
        state.value = value
    }
}
