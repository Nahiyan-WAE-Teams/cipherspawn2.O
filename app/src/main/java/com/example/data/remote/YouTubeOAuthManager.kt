package com.example.data.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicReference

sealed interface OAuthAuthorizeOutcome {
    data class Authorized(val accessToken: String) : OAuthAuthorizeOutcome
    data class NeedsUserConsent(val pendingIntent: PendingIntent) : OAuthAuthorizeOutcome
    data class Failed(val message: String) : OAuthAuthorizeOutcome
}

data class ApiAuthStatus(
    val oauthConnected: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val tokenExpiresAtMillis: Long = 0L,
) {
    val isReadyForRequests: Boolean
        get() = oauthConnected || apiKeyConfigured
}

class YouTubeOAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val accessTokenRef = AtomicReference<String?>(null)
    private var tokenAcquiredAtMillis: Long = 0L

    private val _authStatus = MutableStateFlow(
        ApiAuthStatus(
            oauthConnected = false,
            apiKeyConfigured = isApiKeyConfigured(),
        )
    )
    val authStatus: StateFlow<ApiAuthStatus> = _authStatus.asStateFlow()

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.YOUTUBE_API_KEY
        return key.isNotBlank() && key != YouTubeAuthInterceptor.PLACEHOLDER_API_KEY
    }

    fun currentAccessToken(): String? {
        val token = accessTokenRef.get() ?: return null
        val ageMillis = System.currentTimeMillis() - tokenAcquiredAtMillis
        // Access tokens live ~1 hour; proactively treat tokens > 50 min as stale
        return if (ageMillis < 50 * 60 * 1000L) token else null
    }

    suspend fun requestYouTubeAuthorization(): OAuthAuthorizeOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
                .build()

            val result = Identity.getAuthorizationClient(appContext)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    OAuthAuthorizeOutcome.NeedsUserConsent(pendingIntent)
                } else {
                    OAuthAuthorizeOutcome.Failed("OAuth consent dialog unavailable on this device.")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    setToken(token)
                    OAuthAuthorizeOutcome.Authorized(token)
                } else {
                    OAuthAuthorizeOutcome.Failed("OAuth access token was empty.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OAuth authorization failed: ${e.message}", e)
            OAuthAuthorizeOutcome.Failed(e.localizedMessage ?: "OAuth 2.0 authorization failed.")
        }
    }

    suspend fun refreshTokenSilentlyIfPossible(): String? {
        currentAccessToken()?.let { return it }
        return when (val outcome = requestYouTubeAuthorization()) {
            is OAuthAuthorizeOutcome.Authorized -> outcome.accessToken
            else -> null
        }
    }

    fun handleAuthorizationResultIntent(data: Intent?): OAuthAuthorizeOutcome {
        return try {
            val authResult = Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                setToken(token)
                OAuthAuthorizeOutcome.Authorized(token)
            } else {
                OAuthAuthorizeOutcome.Failed("No OAuth token returned after consent.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OAuth consent result: ${e.message}", e)
            OAuthAuthorizeOutcome.Failed(e.localizedMessage ?: "OAuth consent was cancelled.")
        }
    }

    fun clearToken() {
        accessTokenRef.set(null)
        tokenAcquiredAtMillis = 0L
        _authStatus.value = ApiAuthStatus(
            oauthConnected = false,
            apiKeyConfigured = isApiKeyConfigured(),
            tokenExpiresAtMillis = 0L,
        )
    }

    private fun setToken(token: String) {
        val now = System.currentTimeMillis()
        accessTokenRef.set(token)
        tokenAcquiredAtMillis = now
        _authStatus.value = ApiAuthStatus(
            oauthConnected = true,
            apiKeyConfigured = isApiKeyConfigured(),
            tokenExpiresAtMillis = now + 55 * 60 * 1000L,
        )
    }

    companion object {
        private const val TAG = "YouTubeOAuthManager"
        const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
