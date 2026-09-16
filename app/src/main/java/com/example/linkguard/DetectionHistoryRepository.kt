package com.example.linkguard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DetectionHistoryRepository {

    private val _isBubbleRunning = MutableStateFlow(false)
    val isBubbleRunning: StateFlow<Boolean> = _isBubbleRunning.asStateFlow()

    fun setBubbleRunning(running: Boolean) {
        _isBubbleRunning.value = running
    }
}
