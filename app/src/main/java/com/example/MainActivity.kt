package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogle
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalPreferencesRepository
import com.example.data.repository.OnlineRoomRepository
import com.example.game.audio.SoundAndHapticsManager
import com.example.game.offline.OfflineMatchViewModel
import com.example.game.online.OnlineMatchViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HowToPlayScreen
import com.example.ui.screens.OfflineMatchHostScreen
import com.example.ui.screens.OnlineActiveRoomScreen
import com.example.ui.screens.OnlineAuthGateScreen
import com.example.ui.screens.OnlineEntryHubScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AlJasoosTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object AppRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_OFFLINE = "offline"
    const val ROUTE_ONLINE = "online"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_HOW_TO_PLAY = "how_to_play"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialInviteCode = extractRoomCodeFromIntent(intent)

        setContent {
            AlJasoosTheme {
                var pendingInviteCode by rememberSaveable { mutableStateOf(initialInviteCode) }

                DisposableEffect(this) {
                    val listener = Consumer<Intent> { newIntent ->
                        val extracted = extractRoomCodeFromIntent(newIntent)
                        if (!extracted.isNullOrBlank()) {
                            pendingInviteCode = extracted
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                AlJasoosAppRoot(
                    pendingInviteCode = pendingInviteCode,
                    onClearPendingInviteCode = { pendingInviteCode = null }
                )
            }
        }
    }

    private fun extractRoomCodeFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val raw = data.getQueryParameter("code") ?: data.lastPathSegment
        val cleaned = raw?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
        return cleaned?.takeIf { it.length in 4..12 && it != "JOIN" }
    }
}

@Composable
fun AlJasoosAppRoot(
    pendingInviteCode: String?,
    onClearPendingInviteCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val prefsRepository = remember(context) { LocalPreferencesRepository(context) }
    val soundManager = remember(context) { SoundAndHapticsManager(context) }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val localSettings by prefsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = LocalAppSettings()
    )

    val currentUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )

    // Automatically route to Online Hub if launched from an invite deep link
    LaunchedEffect(pendingInviteCode) {
        if (!pendingInviteCode.isNullOrBlank()) {
            navController.navigate(AppRoutes.ROUTE_ONLINE) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.ROUTE_HOME
    ) {
        composable(AppRoutes.ROUTE_HOME) {
            HomeScreen(
                localSettings = localSettings,
                isSignedIn = currentUser != null,
                userDisplayName = currentUser?.displayName ?: currentUser?.email,
                onPlayOffline = {
                    soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                    navController.navigate(AppRoutes.ROUTE_OFFLINE) { launchSingleTop = true }
                },
                onPlayOnline = {
                    soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                    navController.navigate(AppRoutes.ROUTE_ONLINE) { launchSingleTop = true }
                },
                onOpenSettings = {
                    soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                    navController.navigate(AppRoutes.ROUTE_SETTINGS) { launchSingleTop = true }
                },
                onOpenHowToPlay = {
                    soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                    navController.navigate(AppRoutes.ROUTE_HOW_TO_PLAY) { launchSingleTop = true }
                }
            )
        }

        composable(AppRoutes.ROUTE_OFFLINE) {
            val offlineVm: OfflineMatchViewModel = viewModel()
            OfflineMatchHostScreen(
                viewModel = offlineVm,
                localSettings = localSettings,
                soundManager = soundManager,
                onRecordMatchWinnerSummary = { winners, topScore ->
                    scope.launch {
                        prefsRepository.recordOfflineMatchSummary(winners, topScore)
                    }
                },
                onExitToHome = {
                    navController.popBackStack(AppRoutes.ROUTE_HOME, inclusive = false)
                }
            )
        }

        composable(AppRoutes.ROUTE_ONLINE) {
            val authenticatedUser = currentUser
            // Auth-Gated Firestore Lifecycle: NEVER instantiate OnlineMatchViewModel when currentUser == null
            if (authenticatedUser == null) {
                OnlineAuthGateScreen(
                    pendingInviteCode = pendingInviteCode,
                    onAuthCompleted = {
                        // StateFlow automatically recomposes into authenticated branch
                    },
                    onBack = {
                        onClearPendingInviteCode()
                        navController.popBackStack(AppRoutes.ROUTE_HOME, inclusive = false)
                    }
                )
            } else {
                val onlineVm: OnlineMatchViewModel = viewModel(
                    key = "online_vm_${authenticatedUser.uid}",
                    factory = viewModelFactory {
                        initializer {
                            OnlineMatchViewModel(
                                repository = OnlineRoomRepository(context),
                                currentUserId = authenticatedUser.uid
                            )
                        }
                    }
                )
                val onlineState by onlineVm.uiState.collectAsStateWithLifecycle()
                val fallbackDefaultName = localSettings.defaultPlayerName.ifBlank {
                    authenticatedUser.displayName?.take(24) ?: ""
                }

                if (onlineState.activeRoomCode == null) {
                    OnlineEntryHubScreen(
                        state = onlineState,
                        defaultName = fallbackDefaultName,
                        defaultAvatarIndex = localSettings.defaultAvatarIndex,
                        pendingInviteCode = pendingInviteCode,
                        onConsumePendingInviteCode = onClearPendingInviteCode,
                        onUpdateProfileInputs = { name, avatarIdx ->
                            onlineVm.setProfileInputs(name, avatarIdx)
                        },
                        onUpdateJoinCode = { code ->
                            onlineVm.setJoinCodeInput(code)
                        },
                        onCreateRoom = {
                            scope.launch {
                                prefsRepository.saveDefaultProfile(
                                    onlineState.playerNameInput,
                                    onlineState.selectedAvatarIndex
                                )
                            }
                            onlineVm.createOnlineRoom()
                        },
                        onJoinRoom = {
                            scope.launch {
                                prefsRepository.saveDefaultProfile(
                                    onlineState.playerNameInput,
                                    onlineState.selectedAvatarIndex
                                )
                            }
                            onlineVm.joinOnlineRoom()
                        },
                        onBack = {
                            navController.popBackStack(AppRoutes.ROUTE_HOME, inclusive = false)
                        }
                    )
                } else {
                    OnlineActiveRoomScreen(
                        viewModel = onlineVm,
                        localSettings = localSettings,
                        soundManager = soundManager,
                        onRoomExited = {
                            // Remains on OnlineEntryHubScreen once activeRoomCode clears
                        }
                    )
                }
            }
        }

        composable(AppRoutes.ROUTE_SETTINGS) {
            SettingsScreen(
                localSettings = localSettings,
                isSignedIn = currentUser != null,
                userEmail = currentUser?.email ?: currentUser?.displayName,
                onToggleSound = { enabled ->
                    scope.launch { prefsRepository.setSoundEffectsEnabled(enabled) }
                },
                onToggleVibration = { enabled ->
                    scope.launch { prefsRepository.setVibrationEnabled(enabled) }
                },
                onSignOut = {
                    signOutGoogle(
                        context = context,
                        credentialManager = credentialManager,
                        onSignOutComplete = {},
                        scope = scope
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.ROUTE_HOW_TO_PLAY) {
            HowToPlayScreen(onBack = { navController.popBackStack() })
        }
    }
}
