package com.nextservices.nextvision.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks when the first home screen content is ready, so the splash can stay up until then. */
object StartupState {

    private val _homeContentReady = MutableStateFlow(false)
    val homeContentReady = _homeContentReady.asStateFlow()

    fun markHomeContentReady() {
        _homeContentReady.value = true
    }
}
