package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.AppNotificationManager
import com.example.core.NetworkMonitor
import com.example.data.local.PreferencesDataStore
import com.example.data.repository.TubePulseRepository
import com.example.ui.auth.AuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.components.AddChannelBottomSheet
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ChannelDetailScreen
import com.example.ui.screens.PulseDashboardScreen
import com.example.ui.screens.StudioSettingsScreen
import com.example.ui.theme.ThemePreference
import com.example.ui.theme.TubePulseTheme
import com.example.ui.viewmodel.OneTimeUiEvent
import com.example.ui.viewmodel.TubePulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable

@Serializable
object PulseRoute

@Serializable
object AnalyticsRoute

@Serializable
object AlertsRoute

@Serializable
object StudioRoute

@Serializable
data class ChannelDetailRoute(val channelId: String)

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
)

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun TubePulseApp(
    widthSizeClass: WindowWidthSizeClass,
    initialDeepLinkChannelId: String?,
) {
    val context = LocalContext.current
    val preferencesDataStore = remember(context) { PreferencesDataStore(context) }
    val themePref by preferencesDataStore.themePreference.collectAsStateWithLifecycle(
        initialValue = ThemePreference.DARK
    )

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    TubePulseTheme(themePreference = themePref) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {
                    // AuthStateListener updates currentUser reactively
                }
            )
        } else {
            key(user.uid) {
                AuthenticatedStudioShell(
                    user = user,
                    widthSizeClass = widthSizeClass,
                    initialDeepLinkChannelId = initialDeepLinkChannelId,
                    preferencesDataStore = preferencesDataStore,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(
    user: FirebaseUser,
    widthSizeClass: WindowWidthSizeClass,
    initialDeepLinkChannelId: String?,
    preferencesDataStore: PreferencesDataStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { TubePulseRepository(context) }

    LaunchedEffect(user.uid) {
        repository.ensureUserProfile(user)
    }

    val viewModel: TubePulseViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                TubePulseViewModel(
                    repository = repository,
                    networkMonitor = NetworkMonitor(app),
                    preferencesDataStore = preferencesDataStore,
                    notificationManager = AppNotificationManager(app),
                    currentUserId = user.uid,
                )
            }
        },
    )

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isLiveStreamActive by viewModel.isLiveStreamActive.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val unreadAlertCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddChannelSheet by rememberSaveable { mutableStateOf(false) }
    var selectedExpandedChannelId by rememberSaveable { mutableStateOf(initialDeepLinkChannelId) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OneTimeUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(initialDeepLinkChannelId) {
        if (!initialDeepLinkChannelId.isNullOrBlank() && !isExpanded) {
            navController.navigate(ChannelDetailRoute(initialDeepLinkChannelId))
        }
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = PulseRoute,
                labelRes = R.string.nav_pulse,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                tag = "nav_pulse",
            ),
            TopLevelDestination(
                route = AnalyticsRoute,
                labelRes = R.string.nav_analytics,
                selectedIcon = Icons.Filled.BarChart,
                unselectedIcon = Icons.Outlined.BarChart,
                tag = "nav_analytics",
            ),
            TopLevelDestination(
                route = AlertsRoute,
                labelRes = R.string.nav_alerts,
                selectedIcon = Icons.Filled.Notifications,
                unselectedIcon = Icons.Outlined.Notifications,
                tag = "nav_alerts",
            ),
            TopLevelDestination(
                route = StudioRoute,
                labelRes = R.string.nav_studio,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                tag = "nav_studio",
            ),
        )
    }

    val isDetailRoute = currentDestination?.hasRoute(ChannelDetailRoute::class) == true

    if (showAddChannelSheet) {
        AddChannelBottomSheet(
            searchState = searchState,
            trackedChannelIds = channels.map { it.channelId }.toSet(),
            onSearch = { viewModel.searchChannels(it) },
            onSelectChannel = { candidate -> viewModel.trackChannel(candidate) },
            onDismiss = {
                showAddChannelSheet = false
                viewModel.clearSearch()
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (!isDetailRoute && (currentDestination?.hasRoute(PulseRoute::class) == true || currentDestination == null)) {
                FloatingActionButton(
                    onClick = { showAddChannelSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_channel"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_channel))
                }
            }
        },
        bottomBar = {
            if (!useRail && !isDetailRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets.navigationBars,
                ) {
                    destinations.forEach { dest ->
                        val selected = when (dest.route) {
                            is PulseRoute -> currentDestination?.hasRoute(PulseRoute::class) == true
                            is AnalyticsRoute -> currentDestination?.hasRoute(AnalyticsRoute::class) == true
                            is AlertsRoute -> currentDestination?.hasRoute(AlertsRoute::class) == true
                            is StudioRoute -> currentDestination?.hasRoute(StudioRoute::class) == true
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                if (dest.route is AlertsRoute && unreadAlertCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$unreadAlertCount") } }) {
                                        Icon(icon, contentDescription = stringResource(dest.labelRes))
                                    }
                                } else {
                                    Icon(icon, contentDescription = stringResource(dest.labelRes))
                                }
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.tag),
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    destinations.forEach { dest ->
                        val selected = when (dest.route) {
                            is PulseRoute -> currentDestination?.hasRoute(PulseRoute::class) == true
                            is AnalyticsRoute -> currentDestination?.hasRoute(AnalyticsRoute::class) == true
                            is AlertsRoute -> currentDestination?.hasRoute(AlertsRoute::class) == true
                            is StudioRoute -> currentDestination?.hasRoute(StudioRoute::class) == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                if (dest.route is AlertsRoute && unreadAlertCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$unreadAlertCount") } }) {
                                        Icon(icon, contentDescription = stringResource(dest.labelRes))
                                    }
                                } else {
                                    Icon(icon, contentDescription = stringResource(dest.labelRes))
                                }
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.tag),
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = PulseRoute,
                ) {
                    composable<PulseRoute> {
                        val activeDetailId = selectedExpandedChannelId
                            ?: channels.firstOrNull { it.pinnedToWidget }?.channelId
                            ?: channels.firstOrNull()?.channelId

                        if (isExpanded && channels.isNotEmpty() && activeDetailId != null) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                PulseDashboardScreen(
                                    channels = channels,
                                    selectedChannelId = activeDetailId,
                                    isOnline = isOnline,
                                    isLiveStreamActive = isLiveStreamActive,
                                    isSyncing = isSyncing,
                                    countdownSeconds = countdownSeconds,
                                    lastSyncError = lastSyncError,
                                    onToggleLiveStream = { viewModel.toggleLiveStream() },
                                    onSyncNow = { viewModel.syncAllNow() },
                                    onOpenAddChannel = { showAddChannelSheet = true },
                                    onSelectChannel = { selectedExpandedChannelId = it.channelId },
                                    onTogglePin = { viewModel.toggleWidgetPin(it) },
                                    onToggleAlerts = { viewModel.toggleChannelAlerts(it) },
                                    modifier = Modifier.weight(0.46f),
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val detailChannel by viewModel.observeChannelDetail(activeDetailId).collectAsStateWithLifecycle()
                                val detailVideos by viewModel.observeChannelVideos(activeDetailId).collectAsStateWithLifecycle()
                                val detailSnapshots by viewModel.observeChannelSnapshots(activeDetailId).collectAsStateWithLifecycle()
                                ChannelDetailScreen(
                                    channel = detailChannel,
                                    videos = detailVideos,
                                    snapshots = detailSnapshots,
                                    isOnline = isOnline,
                                    showBackButton = false,
                                    onBack = {},
                                    onSyncChannel = { viewModel.syncSingleChannel(activeDetailId) },
                                    onTogglePin = { viewModel.toggleWidgetPin(it) },
                                    onToggleAlerts = { viewModel.toggleChannelAlerts(it) },
                                    onUpdateMilestoneStep = { ch, step -> viewModel.updateMilestoneStep(ch, step) },
                                    onRemoveChannel = { viewModel.removeChannel(it) },
                                    modifier = Modifier.weight(0.54f),
                                )
                            }
                        } else {
                            PulseDashboardScreen(
                                channels = channels,
                                selectedChannelId = null,
                                isOnline = isOnline,
                                isLiveStreamActive = isLiveStreamActive,
                                isSyncing = isSyncing,
                                countdownSeconds = countdownSeconds,
                                lastSyncError = lastSyncError,
                                onToggleLiveStream = { viewModel.toggleLiveStream() },
                                onSyncNow = { viewModel.syncAllNow() },
                                onOpenAddChannel = { showAddChannelSheet = true },
                                onSelectChannel = { navController.navigate(ChannelDetailRoute(it.channelId)) },
                                onTogglePin = { viewModel.toggleWidgetPin(it) },
                                onToggleAlerts = { viewModel.toggleChannelAlerts(it) },
                            )
                        }
                    }

                    composable<AnalyticsRoute> {
                        AnalyticsScreen(
                            channels = channels,
                            onSelectChannel = { ch ->
                                if (isExpanded) {
                                    selectedExpandedChannelId = ch.channelId
                                    navController.navigate(PulseRoute)
                                } else {
                                    navController.navigate(ChannelDetailRoute(ch.channelId))
                                }
                            },
                        )
                    }

                    composable<AlertsRoute> {
                        AlertsScreen(
                            alertsState = alertsState,
                            onAcknowledgeAlert = { viewModel.acknowledgeAlert(it) },
                            onAcknowledgeAll = { viewModel.acknowledgeAllAlerts() },
                            onSendTestAlert = { viewModel.sendTestNotification() },
                        )
                    }

                    composable<StudioRoute> {
                        val pinnedTitle = channels.firstOrNull { it.pinnedToWidget }?.title
                        StudioSettingsScreen(
                            userProfileState = userProfileState,
                            themePreference = themePreference,
                            pinnedChannelTitle = pinnedTitle,
                            onUpdateSyncInterval = { viewModel.updateSyncInterval(it) },
                            onToggleMasterNotifications = { viewModel.toggleMasterNotifications(it) },
                            onSetThemePreference = { viewModel.setThemePreference(it) },
                            onSignOut = {
                                AuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = { viewModel.onBeforeSignOut() },
                                )
                            },
                        )
                    }

                    composable<ChannelDetailRoute> { entry ->
                        val route = entry.toRoute<ChannelDetailRoute>()
                        val detailChannel by viewModel.observeChannelDetail(route.channelId).collectAsStateWithLifecycle()
                        val detailVideos by viewModel.observeChannelVideos(route.channelId).collectAsStateWithLifecycle()
                        val detailSnapshots by viewModel.observeChannelSnapshots(route.channelId).collectAsStateWithLifecycle()
                        ChannelDetailScreen(
                            channel = detailChannel,
                            videos = detailVideos,
                            snapshots = detailSnapshots,
                            isOnline = isOnline,
                            showBackButton = true,
                            onBack = { navController.popBackStack() },
                            onSyncChannel = { viewModel.syncSingleChannel(route.channelId) },
                            onTogglePin = { viewModel.toggleWidgetPin(it) },
                            onToggleAlerts = { viewModel.toggleChannelAlerts(it) },
                            onUpdateMilestoneStep = { ch, step -> viewModel.updateMilestoneStep(ch, step) },
                            onRemoveChannel = { viewModel.removeChannel(it) },
                        )
                    }
                }
            }
        }
    }
}
