package com.example.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.AiInsightDialog
import com.example.ui.components.ManualLyricSearchSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.DownloadScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.RoraxMusicTheme
import com.example.ui.viewmodel.RoraxViewModel

private data class TopTab(
  val route: Any,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@Composable
fun RoraxApp(
  viewModel: RoraxViewModel,
  openPlayerInitially: Boolean,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val splashState by viewModel.splashState.collectAsStateWithLifecycle()
  val hasAudioPermission by viewModel.hasAudioPermission.collectAsStateWithLifecycle()
  val songs by viewModel.songs.collectAsStateWithLifecycle()
  val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val artists by viewModel.artists.collectAsStateWithLifecycle()
  val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
  val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val lyricsMap by viewModel.lyricsMap.collectAsStateWithLifecycle()
  val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
  val inFlightSongIds by viewModel.inFlightLyricSongIds.collectAsStateWithLifecycle()
  val isBulkDownloading by viewModel.isBulkDownloading.collectAsStateWithLifecycle()
  val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
  val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
  val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
  val manualSearchState by viewModel.manualSearchState.collectAsStateWithLifecycle()
  val aiSheetState by viewModel.aiSheetState.collectAsStateWithLifecycle()

  var hasAskedPermissionOnce by remember { mutableStateOf(false) }
  var hasRequestedNotifOnce by remember { mutableStateOf(false) }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasAskedPermissionOnce = true
      viewModel.onAudioPermissionResult(granted)
    }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      hasRequestedNotifOnce = true
    }

  // Refresh permission state when returning from system Settings
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionStatus()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(snackbarMsg) {
    val msg = snackbarMsg ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.consumeMessage()
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val isOnSplash = currentDest?.hasRoute<SplashRoute>() == true || currentDest == null
  val isOnPlayer = currentDest?.hasRoute<PlayerRoute>() == true
  val showMainChrome = !isOnSplash && !isOnPlayer

  // Request audio permission contextually right after leaving Splash on first run
  LaunchedEffect(isOnSplash, hasAudioPermission) {
    if (!isOnSplash && !hasAudioPermission && !hasAskedPermissionOnce) {
      hasAskedPermissionOnce = true
      audioPermissionLauncher.launch(viewModel.requiredAudioPermission())
    }
  }

  // Request POST_NOTIFICATIONS politely on Android 13+ when starting bulk downloads or playback
  val requestNotificationIfNeeded = {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasRequestedNotifOnce &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      hasRequestedNotifOnce = true
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val showSettingsFallback =
    hasAskedPermissionOnce &&
      !hasAudioPermission &&
      (context as? Activity)?.let { act ->
        !ActivityCompat.shouldShowRequestPermissionRationale(act, viewModel.requiredAudioPermission())
      } == true

  val topTabs =
    remember {
      listOf(
        TopTab(
          route = HomeRoute,
          labelRes = R.string.nav_home,
          selectedIcon = Icons.Rounded.Home,
          unselectedIcon = Icons.Outlined.Home,
          testTag = "nav_tab_home",
        ),
        TopTab(
          route = DownloadsRoute,
          labelRes = R.string.nav_lyrics,
          selectedIcon = Icons.Rounded.Lyrics,
          unselectedIcon = Icons.Outlined.Lyrics,
          testTag = "nav_tab_lyrics",
        ),
        TopTab(
          route = SettingsRoute,
          labelRes = R.string.nav_settings,
          selectedIcon = Icons.Rounded.Settings,
          unselectedIcon = Icons.Outlined.Settings,
          testTag = "nav_tab_settings",
        ),
      )
    }

  val navigateToTopTab: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  RoraxMusicTheme(
    themeMode = preferences.themeMode,
    accentPalette = preferences.accentPalette,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 600.dp

      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
          if (showMainChrome && !useNavRail) {
            Column(
              modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
              if (playbackState.currentSong != null) {
                MiniPlayerBar(
                  playbackState = playbackState,
                  onOpenPlayer = { navController.navigate(PlayerRoute) },
                  onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                  onNext = { viewModel.playbackController.next() },
                )
              }
              NavigationBar {
                topTabs.forEach { tab ->
                  val selected = currentDest?.hasRoute(tab.route::class) == true
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateToTopTab(tab.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes),
                      )
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    modifier = Modifier.testTag(tab.testTag),
                  )
                }
              }
            }
          }
        },
      ) { innerPadding ->
        Row(
          modifier =
            Modifier.fillMaxSize()
              .then(if (isOnSplash || isOnPlayer) Modifier else Modifier.padding(innerPadding))
        ) {
          if (showMainChrome && useNavRail) {
            NavigationRail {
              topTabs.forEach { tab ->
                val selected = currentDest?.hasRoute(tab.route::class) == true
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateToTopTab(tab.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = stringResource(tab.labelRes),
                    )
                  },
                  label = { Text(stringResource(tab.labelRes)) },
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }

          Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            Box(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              contentAlignment = Alignment.TopCenter,
            ) {
              val contentModifier =
                if (isOnSplash || isOnPlayer) Modifier.fillMaxSize()
                else Modifier.fillMaxSize().widthIn(max = 840.dp)

              NavHost(
                navController = navController,
                startDestination = SplashRoute,
                modifier = contentModifier,
              ) {
                composable<SplashRoute> {
                  SplashScreen(
                    initState = splashState,
                    reducedMotion = preferences.reducedMotion,
                    onRetry = { viewModel.initializeApp() },
                    onReadyToNavigate = {
                      navController.navigate(HomeRoute) {
                        popUpTo<SplashRoute> { inclusive = true }
                        launchSingleTop = true
                      }
                      if (openPlayerInitially && playbackState.currentSong != null) {
                        navController.navigate(PlayerRoute)
                      }
                    },
                  )
                }

                composable<HomeRoute> {
                  HomeScreen(
                    songs = songs,
                    filteredSongs = filteredSongs,
                    albums = albums,
                    artists = artists,
                    recentSongs = recentSongs,
                    favoriteIds = favoriteIds,
                    playlists = playlists,
                    lyricsMap = lyricsMap,
                    currentPlayingSongId = playbackState.currentSong?.id,
                    searchQuery = searchQuery,
                    hasAudioPermission = hasAudioPermission,
                    showPermissionSettingsFallback = showSettingsFallback,
                    isScanning = isScanning,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onRequestPermission = {
                      audioPermissionLauncher.launch(viewModel.requiredAudioPermission())
                    },
                    onOpenAppSettings = { context.openAppDetailsSettings() },
                    onRescanLibrary = viewModel::rescanLibrary,
                    onPlayQueue = { list, idx ->
                      requestNotificationIfNeeded()
                      viewModel.playSongs(list, idx)
                      navController.navigate(PlayerRoute)
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onFetchLyrics = { song -> viewModel.fetchLyricsForSong(song) },
                    onCreatePlaylist = viewModel::createPlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onAddSongToPlaylist = viewModel::addSongToPlaylist,
                    onRemoveSongFromPlaylist = viewModel::removeSongFromPlaylist,
                    onOpenSettings = { navigateToTopTab(SettingsRoute) },
                  )
                }

                composable<DownloadsRoute> {
                  DownloadScreen(
                    songs = songs,
                    lyricsMap = lyricsMap,
                    downloadTasks = downloadTasks,
                    inFlightSongIds = inFlightSongIds,
                    isBulkDownloading = isBulkDownloading,
                    hasAudioPermission = hasAudioPermission,
                    showPermissionSettingsFallback = showSettingsFallback,
                    aiEnabled = preferences.aiFeaturesEnabled,
                    onRequestPermission = {
                      audioPermissionLauncher.launch(viewModel.requiredAudioPermission())
                    },
                    onOpenAppSettings = { context.openAppDetailsSettings() },
                    onStartBulkDownload = {
                      requestNotificationIfNeeded()
                      viewModel.startBulkDownload()
                    },
                    onCancelBulkDownload = viewModel::cancelBulkDownload,
                    onRetryFailed = {
                      requestNotificationIfNeeded()
                      viewModel.retryFailedDownloads()
                    },
                    onRetrySingleSong = { song -> viewModel.fetchLyricsForSong(song) },
                    onOpenManualSearch = viewModel::openManualSearch,
                    onApplyCandidate = viewModel::applyCandidate,
                    onImportLrc = viewModel::importLrcFile,
                    onRequestAiSuggest = viewModel::requestAiMatchSuggestion,
                  )
                }

                composable<SettingsRoute> {
                  SettingsScreen(
                    preferences = preferences,
                    songCount = songs.size,
                    cacheSizeBytes = cacheSizeBytes,
                    hasAudioPermission = hasAudioPermission,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetAccentPalette = viewModel::setAccentPalette,
                    onSetLyricsFontSize = viewModel::setLyricsFontSize,
                    onSetReducedMotion = viewModel::setReducedMotion,
                    onSetResumeSession = viewModel::setResumeLastSession,
                    onSetDefaultSpeed = viewModel::setDefaultPlaybackSpeed,
                    onSetAutoLookup = viewModel::setAutoLyricLookup,
                    onSetRomanizeLyrics = viewModel::setRomanizeLyrics,
                    onSetWifiOnly = viewModel::setWifiOnlyDownloads,
                    onRescanLibrary = viewModel::rescanLibrary,
                    onRequestOrOpenPermission = {
                      if (hasAudioPermission || showSettingsFallback) {
                        context.openAppDetailsSettings()
                      } else {
                        audioPermissionLauncher.launch(viewModel.requiredAudioPermission())
                      }
                    },
                    onClearLyricCache = viewModel::clearLyricCache,
                    onSetAiEnabled = viewModel::setAiFeaturesEnabled,
                  )
                }

                composable<PlayerRoute> {
                  val activeSongId = playbackState.currentSong?.id
                  val activeLyrics = activeSongId?.let { lyricsMap[it] }
                  PlayerScreen(
                    playbackState = playbackState,
                    lyricRecord = activeLyrics,
                    isFavorite = activeSongId != null && activeSongId in favoriteIds,
                    preferences = preferences,
                    isLookingUpLyrics = activeSongId != null && activeSongId in inFlightSongIds,
                    onNavigateBack = { navController.popBackStack() },
                    onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                    onSeekTo = { pos -> viewModel.playbackController.seekTo(pos) },
                    onSeekToLine = { pos -> viewModel.playbackController.seekToAndPlay(pos) },
                    onNext = { viewModel.playbackController.next() },
                    onPrevious = { viewModel.playbackController.previous() },
                    onToggleShuffle = { viewModel.playbackController.toggleShuffle() },
                    onCycleRepeat = { viewModel.playbackController.cycleRepeatMode() },
                    onChangeSpeed = { spd -> viewModel.playbackController.setPlaybackSpeed(spd) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSelectQueueIndex = { idx -> viewModel.playbackController.seekToQueueIndex(idx) },
                    onRemoveQueueIndex = { idx -> viewModel.playbackController.removeFromQueue(idx) },
                    onFetchLyrics = { song -> viewModel.fetchLyricsForSong(song) },
                    onOpenManualSearch = viewModel::openManualSearch,
                    onApplyCandidate = viewModel::applyCandidate,
                    onImportLrc = viewModel::importLrcFile,
                    onRequestAiExplain = viewModel::requestAiLyricExplanation,
                    onRequestAiSuggest = viewModel::requestAiMatchSuggestion,
                  )
                }
              }
            }

            if (showMainChrome && useNavRail && playbackState.currentSong != null) {
              MiniPlayerBar(
                playbackState = playbackState,
                onOpenPlayer = { navController.navigate(PlayerRoute) },
                onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                onNext = { viewModel.playbackController.next() },
              )
            }
          }
        }
      }

      if (manualSearchState.song != null) {
        ManualLyricSearchSheet(
          state = manualSearchState,
          aiEnabled = preferences.aiFeaturesEnabled,
          onQueryChange = viewModel::updateManualSearchQuery,
          onSearch = { viewModel.executeManualSearch() },
          onSelectCandidate = { cand ->
            manualSearchState.song?.let { viewModel.applyCandidate(it.id, cand) }
          },
          onRequestAiSuggest = viewModel::requestAiMatchSuggestion,
          onDismiss = viewModel::closeManualSearch,
        )
      }

      AiInsightDialog(
        state = aiSheetState,
        onDismiss = viewModel::dismissAiSheet,
      )
    }
  }
}

private fun Context.openAppDetailsSettings() {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
  startActivity(intent)
}
