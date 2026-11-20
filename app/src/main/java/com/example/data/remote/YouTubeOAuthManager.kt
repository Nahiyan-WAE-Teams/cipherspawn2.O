package com.example.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compatibility status model for the existing settings and add-channel surfaces. */
data class ApiAuthStatus(
    val oauthConnected: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val tokenExpiresAtMillis: Long = 0L,
) {
    val isReadyForRequests: Boolean get() = true
}

class YouTubeOAuthManager {
    private val _authStatus = MutableStateFlow(ApiAuthStatus())
    val authStatus: StateFlow<ApiAuthStatus> = _authStatus.asStateFlow()
    fun clearToken() = Unit
}
