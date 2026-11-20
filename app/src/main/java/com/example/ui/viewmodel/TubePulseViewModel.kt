package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppNotificationManager
import com.example.core.NetworkMonitor
import com.example.core.extractFirestoreMessage
import com.example.data.local.CachedChannelEntity
import com.example.data.local.CachedSnapshotEntity
import com.example.data.local.CachedVideoEntity
import com.example.data.local.PreferencesDataStore
import com.example.data.model.AlertEvent
import com.example.data.model.AlertMetricType
import com.example.data.model.UserProfile
import com.example.data.model.YouTubeChannelSearchResult
import com.example.data.remote.ApiAuthStatus
import com.example.data.repository.TubePulseRepository
import com.example.ui.theme.ThemePreference
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val items: List<YouTubeChannelSearchResult>) : SearchState
    data class Error(val message: String) : SearchState
}

sealed interface OneTimeUiEvent {
    data class ShowMessage(val message: String) : OneTimeUiEvent
}

class TubePulseViewModel(
    private val repository: TubePulseRepository,
    private val networkMonitor: NetworkMonitor,
    private val preferencesDataStore: PreferencesDataStore,
    private val notificationManager: AppNotificationManager,
    private val currentUserId: String,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val apiAuthStatus: StateFlow<ApiAuthStatus> = MutableStateFlow(ApiAuthStatus())
    val themePreference: StateFlow<ThemePreference> = preferencesDataStore.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemePreference.DARK)

    val userProfile: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", firestoreError ?: error)
            emit(UiState.Error(extractFirestoreMessage(error, "Failed to load profile")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Combine local Room cache (instant offline resilience) with real-time Firestore sync
    private val cloudSyncState: StateFlow<UiState<Int>> = repository.observeFirestoreChannels(currentUserId)
        .map<List<com.example.data.model.TrackedChannel>, UiState<Int>> { UiState.Success(it.size) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Cloud channels listener error", firestoreError ?: error)
            emit(UiState.Error(extractFirestoreMessage(error, "Cloud sync interrupted")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val channels: StateFlow<List<CachedChannelEntity>> = repository.observeCachedChannels(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val alertsState: StateFlow<UiState<List<AlertEvent>>> = repository.observeAlerts(currentUserId)
        .map<List<AlertEvent>, UiState<List<AlertEvent>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Alerts stream error", firestoreError ?: error)
            emit(UiState.Error(extractFirestoreMessage(error, "Failed to load alerts")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val unreadAlertCount: StateFlow<Int> = alertsState
        .map { state -> (state as? UiState.Success)?.data?.count { !it.acknowledged } ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isLiveStreamActive = MutableStateFlow(true)
    val isLiveStreamActive: StateFlow<Boolean> = _isLiveStreamActive.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(15)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _events = MutableSharedFlow<OneTimeUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OneTimeUiEvent> = _events.asSharedFlow()

    private var liveLoopJob: Job? = null

    init {
        // Keep cloud listener active while VM lives
        cloudSyncState.launchIn(viewModelScope)

        // Start real-time polling loop & auto-sync when connectivity recovers
        startRealtimePollingEngine()
        networkMonitor.isOnline
            .onEach { online ->
                if (online && channels.value.isNotEmpty() && true) {
                    syncAllNow(silent = true)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun startRealtimePollingEngine() {
        liveLoopJob?.cancel()
        liveLoopJob = viewModelScope.launch {
            while (isActive) {
                val interval = ((userProfile.value as? UiState.Success)?.data?.syncIntervalSeconds ?: 15L)
                    .coerceIn(10L, 3600L)
                    .toInt()
                _countdownSeconds.value = interval
                for (tick in interval downTo 1) {
                    if (!isActive) break
                    _countdownSeconds.value = tick
                    delay(1000L)
                }
                if (isActive && _isLiveStreamActive.value && isOnline.value &&
                    channels.value.isNotEmpty() && oauthManager.authStatus.value.isReadyForRequests
                ) {
                    syncAllNow(silent = true)
                }
            }
        }
    }

    fun toggleLiveStream() {
        _isLiveStreamActive.value = !_isLiveStreamActive.value
    }

    fun syncAllNow(silent: Boolean = false) {
        if (_isSyncing.value) return
        if (!isOnline.value) {
            if (!silent) _events.tryEmit(OneTimeUiEvent.ShowMessage("Offline: showing cached telemetry"))
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _lastSyncError.value = null
            val notifEnabled = (userProfile.value as? UiState.Success)?.data?.notificationsEnabled ?: true
            val result = repository.syncAllTrackedChannels(currentUserId, notifEnabled)
            result.onFailure { err ->
                val msg = err.localizedMessage ?: "Sync failed"
                _lastSyncError.value = msg
                if (!silent) {
                    _events.tryEmit(OneTimeUiEvent.ShowMessage(msg))
                }
            }
            result.onSuccess { count ->
                if (!silent && count > 0) {
                    _events.tryEmit(OneTimeUiEvent.ShowMessage("Synced $count channel${if (count == 1) "" else "s"} live"))
                }
            }
            _isSyncing.value = false
        }
    }

    fun syncSingleChannel(channelId: String) {
        if (!isOnline.value) {
            _events.tryEmit(OneTimeUiEvent.ShowMessage("Offline: showing cached data"))
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val notifEnabled = (userProfile.value as? UiState.Success)?.data?.notificationsEnabled ?: true
            repository.syncSingleChannel(currentUserId, channelId, notifEnabled)
                .onFailure { err ->
                    _events.tryEmit(OneTimeUiEvent.ShowMessage(err.localizedMessage ?: "Channel refresh failed"))
                }
            _isSyncing.value = false
        }
    }

    fun searchChannels(query: String) {
        if (query.isBlank()) {
            _searchState.value = SearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = SearchState.Searching
            repository.searchPublicChannels(query)
                .onSuccess { items ->
                    _searchState.value = SearchState.Results(items)
                }
                .onFailure { err ->
                    _searchState.value = SearchState.Error(
                        err.localizedMessage ?: "Failed to search YouTube channels"
                    )
                }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
    }

    fun trackChannel(candidate: YouTubeChannelSearchResult) {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.trackAndSyncChannel(candidate)
                .onSuccess { saved ->
                    _events.tryEmit(OneTimeUiEvent.ShowMessage("Tracking ${saved.title} in real time"))
                }
                .onFailure { err ->
                    _events.tryEmit(
                        OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Failed to track channel"))
                    )
                }
            _isSyncing.value = false
        }
    }

    fun observeChannelDetail(channelId: String): StateFlow<CachedChannelEntity?> =
        repository.observeCachedChannel(currentUserId, channelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun observeChannelVideos(channelId: String): StateFlow<List<CachedVideoEntity>> =
        repository.observeCachedVideos(currentUserId, channelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun observeChannelSnapshots(channelId: String): StateFlow<List<CachedSnapshotEntity>> {
        // Keep Firestore snapshot stream synced while viewing detail
        repository.observeFirestoreSnapshots(currentUserId, channelId)
            .catch { error ->
                val firestoreError = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
            }
            .launchIn(viewModelScope)

        return repository.observeCachedSnapshots(currentUserId, channelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    }

    fun toggleWidgetPin(channel: CachedChannelEntity) {
        viewModelScope.launch {
            repository.updateChannelPreferences(
                channelId = channel.channelId,
                milestoneStep = channel.milestoneStep,
                alertsEnabled = channel.alertsEnabled,
                pinnedToWidget = !channel.pinnedToWidget,
            ).onSuccess {
                val msg = if (!channel.pinnedToWidget) "Pinned to Home Screen Widget" else "Unpinned from Widget"
                _events.tryEmit(OneTimeUiEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.tryEmit(OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Update failed")))
            }
        }
    }

    fun toggleChannelAlerts(channel: CachedChannelEntity) {
        viewModelScope.launch {
            repository.updateChannelPreferences(
                channelId = channel.channelId,
                milestoneStep = channel.milestoneStep,
                alertsEnabled = !channel.alertsEnabled,
                pinnedToWidget = channel.pinnedToWidget,
            ).onFailure { err ->
                _events.tryEmit(OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Update failed")))
            }
        }
    }

    fun updateMilestoneStep(channel: CachedChannelEntity, newStep: Long) {
        viewModelScope.launch {
            repository.updateChannelPreferences(
                channelId = channel.channelId,
                milestoneStep = newStep,
                alertsEnabled = channel.alertsEnabled,
                pinnedToWidget = channel.pinnedToWidget,
            ).onSuccess {
                _events.tryEmit(OneTimeUiEvent.ShowMessage("Milestone threshold updated"))
            }.onFailure { err ->
                _events.tryEmit(OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Update failed")))
            }
        }
    }

    fun removeChannel(channelId: String) {
        viewModelScope.launch {
            repository.deleteTrackedChannel(channelId)
                .onSuccess {
                    _events.tryEmit(OneTimeUiEvent.ShowMessage("Channel removed from dashboard"))
                }
                .onFailure { err ->
                    _events.tryEmit(OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Delete failed")))
                }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId)
        }
    }

    fun acknowledgeAllAlerts() {
        val alerts = (alertsState.value as? UiState.Success)?.data ?: return
        val unreadIds = alerts.filter { !it.acknowledged }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.acknowledgeAllAlerts(unreadIds)
        }
    }

    fun sendTestNotification() {
        val top = channels.value.firstOrNull()
        val channelId = top?.channelId ?: "tubepulse_system"
        val channelTitle = top?.title ?: "TubePulse Live Engine"
        val value = top?.subscriberCount ?: 0L
        viewModelScope.launch {
            repository.createAlertAndNotify(
                uid = currentUserId,
                channelId = channelId,
                channelTitle = channelTitle,
                metricType = AlertMetricType.SUBSCRIBERS,
                alertTitle = "Live Alert Check · $channelTitle",
                alertMessage = "System notifications and cloud milestone alerts are active.",
                milestoneValue = value,
                deltaValue = 0L,
            )
            if (!notificationManager.hasNotificationPermission()) {
                _events.tryEmit(OneTimeUiEvent.ShowMessage("Enable notification permission to see system alerts"))
            } else {
                _events.tryEmit(OneTimeUiEvent.ShowMessage("System notification dispatched"))
            }
        }
    }

    fun onBeforeSignOut() {
        liveLoopJob?.cancel()
    }

    fun updateSyncInterval(seconds: Long) {
        viewModelScope.launch {
            val profile = (userProfile.value as? UiState.Success)?.data
            repository.updateSyncPreferences(
                syncIntervalSeconds = seconds,
                notificationsEnabled = profile?.notificationsEnabled ?: true,
            ).onSuccess {
                startRealtimePollingEngine()
                _events.tryEmit(OneTimeUiEvent.ShowMessage("Polling cadence set to ${seconds}s"))
            }.onFailure { err ->
                _events.tryEmit(OneTimeUiEvent.ShowMessage(extractFirestoreMessage(err, "Update failed")))
            }
        }
    }

    fun toggleMasterNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val profile = (userProfile.value as? UiState.Success)?.data
            repository.updateSyncPreferences(
                syncIntervalSeconds = profile?.syncIntervalSeconds ?: 15L,
                notificationsEnabled = enabled,
                oauthConnected = oauthManager.authStatus.value.oauthConnected,
            )
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            preferencesDataStore.setThemePreference(preference)
        }
    }

    fun onBeforeSignOut() {
        liveLoopJob?.cancel()
    }

    private companion object {
        const val TAG = "TubePulseVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
