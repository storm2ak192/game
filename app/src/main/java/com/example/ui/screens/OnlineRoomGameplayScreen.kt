package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LocalAppSettings
import com.example.data.model.MatchPhase
import com.example.data.model.OnlineRoom
import com.example.data.model.OnlineRoomPlayer
import com.example.game.audio.SoundAndHapticsManager
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import com.example.game.online.OnlineMatchUiState
import com.example.game.online.OnlineMatchViewModel
import com.example.game.voice.VoiceConnectionState
import com.example.game.voice.WebRtcRoomVoiceBridge
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.components.SecretScratchCard
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.NoirElevatedCard
import com.example.ui.theme.NoirMidnight
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineActiveRoomScreen(
    viewModel: OnlineMatchViewModel,
    localSettings: LocalAppSettings,
    soundManager: SoundAndHapticsManager,
    onRoomExited: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }

    val roomCode = state.activeRoomCode ?: return
    val room = state.room
    val phase = MatchPhase.fromString(room?.status ?: MatchPhase.LOBBY.name)
    val myPlayer = state.players.firstOrNull { it.userId == viewModel.currentUserId }
    val isHost = room?.hostId == viewModel.currentUserId

    // Runtime RECORD_AUDIO permission state for real WebRTC Voice Chat
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(roomCode) {
        if (!hasMicPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.infoToast) {
        val msg = state.infoToast
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    BackHandler { showLeaveDialog = true }

    // Continuous Headless WebRTC Mesh Voice Engine active across ALL room phases
    val connectedPeerIds = remember(state.players, viewModel.currentUserId) {
        state.players
            .filter { it.isConnected && it.userId != viewModel.currentUserId }
            .map { it.userId }
    }

    WebRtcRoomVoiceBridge(
        roomCode = roomCode,
        currentUserId = viewModel.currentUserId,
        hasAudioPermission = hasMicPermission,
        isMuted = myPlayer?.isMicMuted ?: false,
        connectedPeerUids = connectedPeerIds,
        incomingSignals = state.incomingVoiceSignals,
        onSendSignal = { targetUid, type, payload ->
            viewModel.sendWebRtcSignal(targetUid, type, payload)
        },
        onConsumeSignal = { sigId ->
            viewModel.consumeWebRtcSignal(sigId)
        },
        onVoiceStatusChange = { voiceSt, detail ->
            viewModel.updateVoiceStatus(voiceSt, detail)
        }
    )

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("مغادرة الغرفة", color = SpyGold) },
            text = {
                Text(
                    if (isHost && state.players.size > 1) {
                        "أنت مضيف الغرفة حالياً. عند مغادرتك ستنتقل الاستضافة تلقائياً إلى أحد اللاعبين المتصلين دون توقف المباراة."
                    } else {
                        "هل أنت متأكد من مغادرة الغرفة الصوتية والمباراة الحالية؟"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveCurrentRoom(onLeft = onRoomExited)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson),
                    modifier = Modifier.testTag("confirm_leave_online_room_button")
                ) {
                    Text("مغادرة الغرفة", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveDialog = false },
                    modifier = Modifier.testTag("cancel_leave_online_room_button")
                ) {
                    Text("البقاء", color = SpyGold)
                }
            },
            containerColor = NoirElevatedCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "غرفة: $roomCode",
                            style = MaterialTheme.typography.titleMedium,
                            color = SpyGold
                        )
                        val voiceLabel = when (state.voiceState) {
                            VoiceConnectionState.CONNECTED -> "متصل صوتياً — الميكروفون نشط"
                            VoiceConnectionState.MUTED -> "متصل صوتياً — ميكروفونك مكتوم"
                            VoiceConnectionState.INITIALIZING -> "جارٍ تهيئة الاتصال الصوتي..."
                            VoiceConnectionState.PERMISSION_REQUIRED -> "اضغط لتفعيل إذن الميكروفون"
                            VoiceConnectionState.ERROR -> state.voiceStatusMessage ?: "تعذر تشغيل الميكروفون"
                            VoiceConnectionState.DISCONNECTED -> "غير متصل صوتياً"
                        }
                        Text(
                            text = voiceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.voiceState == VoiceConnectionState.CONNECTED) EmeraldConfirm
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (hasMicPermission && myPlayer != null) {
                        IconButton(
                            onClick = {
                                soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                                viewModel.toggleMyMicrophone()
                            },
                            modifier = Modifier.testTag("topbar_my_mic_toggle")
                        ) {
                            Icon(
                                imageVector = if (myPlayer.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = if (myPlayer.isMicMuted) "إلغاء كتم ميكروفوني" else "كتم ميكروفوني",
                                tint = if (myPlayer.isMicMuted) SpyCrimson else EmeraldConfirm
                            )
                        }
                    }
                    IconButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier.testTag("leave_online_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "مغادرة الغرفة",
                            tint = SpyCrimson
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoirMidnight)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NoirMidnight
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 600.dp)
        ) {
            if (!hasMicPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                    border = BorderStroke(1.dp, SpyGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "فعّل إذن الميكروفون للتحدث مع اللاعبين",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                            modifier = Modifier.testTag("request_mic_permission_button")
                        ) {
                            Text("تفعيل")
                        }
                    }
                }
            }

            // Live Room Players Strip with Green Confirmation Ring & Individual Mic Controls
            RoomPlayersStrip(
                players = state.players,
                hostId = room?.hostId.orEmpty(),
                currentUserId = viewModel.currentUserId,
                phase = phase,
                onToggleMyMic = { viewModel.toggleMyMicrophone() }
            )

            if (!state.errorMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpyCrimson.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, SpyCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = Color(0xFFFFCDD2),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (room == null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = SpyGold)
                }
            } else {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (phase) {
                        MatchPhase.LOBBY -> OnlineLobbyPhaseSection(
                            room = room,
                            state = state,
                            isHost = isHost,
                            viewModel = viewModel,
                            onShowCopiedToast = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                        MatchPhase.ROLE_REVEAL -> OnlineRoleRevealPhaseSection(
                            room = room,
                            state = state,
                            myPlayer = myPlayer,
                            isHost = isHost,
                            onConfirmViewed = {
                                soundManager.playScratchReveal(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                                viewModel.confirmSecretViewed()
                            },
                            onHostProceedToQuestions = {
                                soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                                viewModel.startQuestionsAsHost()
                            }
                        )
                        MatchPhase.QUESTIONS -> OnlineQuestionsPhaseSection(
                            room = room,
                            players = state.players,
                            currentUserId = viewModel.currentUserId,
                            isHost = isHost,
                            onNextQuestion = {
                                soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                                viewModel.advanceQuestionTurn()
                            },
                            onStartVoting = {
                                soundManager.playVoteWarning(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                                viewModel.startVoting()
                            }
                        )
                        MatchPhase.VOTING -> OnlineVotingPhaseSection(
                            room = room,
                            state = state,
                            myPlayer = myPlayer,
                            currentUserId = viewModel.currentUserId,
                            onToggleSuspect = { viewModel.toggleSuspectSelection(it) },
                            onSubmitVote = { viewModel.submitMyVote() }
                        )
                        MatchPhase.SPY_GUESS -> OnlineSpyGuessPhaseSection(
                            room = room,
                            state = state,
                            myPlayer = myPlayer,
                            onSubmitGuess = { viewModel.submitMySpyWordGuess(it) }
                        )
                        MatchPhase.ROUND_RESULTS,
                        MatchPhase.MATCH_OVER -> OnlineResultsPhaseSection(
                            room = room,
                            players = state.players,
                            phase = phase,
                            isHost = isHost,
                            onHostNextRound = { viewModel.startRoundAsHost() },
                            onHostNewMatch = { viewModel.startNewMatchAfterGameOver() },
                            onLeaveToHome = { showLeaveDialog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomPlayersStrip(
    players: List<OnlineRoomPlayer>,
    hostId: String,
    currentUserId: String,
    phase: MatchPhase,
    onToggleMyMic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            players.forEach { p ->
                val isMe = p.userId == currentUserId
                val isRoomHost = p.userId == hostId
                val showGreen = phase == MatchPhase.ROLE_REVEAL && p.hasConfirmedCard

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(74.dp)
                ) {
                    PlayerAvatarBadge(
                        avatarIndex = p.avatarIndex.toInt(),
                        playerName = p.name,
                        size = 48.dp,
                        isConfirmedGreen = showGreen,
                        isHost = isRoomHost,
                        isDisconnected = !p.isConnected,
                        showMicBadge = true,
                        isMicMuted = p.isMicMuted,
                        canToggleMic = isMe,
                        onToggleMic = if (isMe) onToggleMyMic else null
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRoomHost) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "مضيف الغرفة",
                                tint = SpyGold,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (isMe) "${p.name} (أنت)" else p.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (showGreen) EmeraldConfirm else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${p.totalScore} ن",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpyGold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineLobbyPhaseSection(
    room: OnlineRoom,
    state: OnlineMatchUiState,
    isHost: Boolean,
    viewModel: OnlineMatchViewModel,
    onShowCopiedToast: (String) -> Unit
) {
    val context = LocalContext.current
    val currentCategory = WordCategory.fromKey(room.category)
    val currentSpyCount = room.spyCount.toInt()
    val currentWinScore = room.winScore.toInt()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Invite & Share Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("كود دعوة الأصدقاء", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = room.code,
                    style = MaterialTheme.typography.displaySmall,
                    color = SpyGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("online_room_code_text")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("Room Code", room.code))
                            onShowCopiedToast("تم نسخ كود الغرفة: ${room.code}")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_room_code_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ الكود")
                    }

                    OutlinedButton(
                        onClick = {
                            val link = viewModel.getInviteLink(room.code)
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("Invite Link", link))
                            onShowCopiedToast("تم نسخ رابط الدعوة المباشر!")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_invite_link_button")
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ الرابط")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.getShareText(room.code))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة دعوة غرفة الجاسوس"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_invite_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مشاركة الدعوة عبر التطبيقات")
                }
            }
        }

        // Match Settings Card (Editable for Host, Live Read-Only for Guests)
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (isHost) "إعدادات المباراة (أنت المضيف)" else "إعدادات المباراة (يحددها المضيف)",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold
                )

                Text("التصنيف:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WordCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = currentCategory == cat,
                            onClick = {
                                if (isHost) viewModel.updateSettingsAsHost(cat, currentSpyCount, currentWinScore)
                            },
                            enabled = isHost,
                            label = { Text(cat.arabicTitle) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpyGold,
                                selectedLabelColor = NoirMidnight
                            ),
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }

                Text("عدد الجواسيس:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1 to "١ جاسوس", 2 to "٢ جاسوس", 3 to "٣ جواسيس").forEach { (cnt, title) ->
                        FilterChip(
                            selected = currentSpyCount == cnt,
                            onClick = {
                                if (isHost) viewModel.updateSettingsAsHost(currentCategory, cnt, currentWinScore)
                            },
                            enabled = isHost,
                            label = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpyCrimson,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        )
                    }
                }

                Text("حد نقاط الفوز: $currentWinScore نقطة", style = MaterialTheme.typography.labelLarge)
                if (isHost) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 150, 200).forEach { preset ->
                            FilterChip(
                                selected = currentWinScore == preset && !state.useCustomWinScore,
                                onClick = { viewModel.updateSettingsAsHost(currentCategory, currentSpyCount, preset) },
                                label = { Text("$preset") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SpyGold,
                                    selectedLabelColor = NoirMidnight
                                ),
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.customWinScoreInput,
                        onValueChange = { viewModel.setCustomWinScoreInput(it) },
                        label = { Text("نقاط فوز مخصصة (١٠ - ١٠٠٠)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (isHost) {
            Button(
                onClick = { viewModel.startRoundAsHost() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldConfirm, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("online_host_start_round_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "متابعة وتوزيع الأدوار (${state.players.count { it.isConnected }} متصلين)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "بانتظار ضغط مضيف الغرفة على «متابعة» لبدء الجولة...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpyGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OnlineRoleRevealPhaseSection(
    room: OnlineRoom,
    state: OnlineMatchUiState,
    myPlayer: OnlineRoomPlayer?,
    isHost: Boolean,
    onConfirmViewed: () -> Unit,
    onHostProceedToQuestions: () -> Unit
) {
    val secretDoc = state.mySecret
    var localScratched by rememberSaveable(room.roundNumber) { mutableStateOf(false) }
    val alreadyConfirmed = myPlayer?.hasConfirmedCard == true
    val connectedPlayers = state.players.filter { it.isConnected }
    val confirmedCount = connectedPlayers.count { it.hasConfirmedCard }
    val allConfirmed = connectedPlayers.isNotEmpty() && connectedPlayers.all { it.hasConfirmedCard }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "تأكيد مشاهدة الدور السري ($confirmedCount / ${connectedPlayers.size})",
            style = MaterialTheme.typography.titleMedium,
            color = if (allConfirmed) EmeraldConfirm else SpyGold
        )

        if (secretDoc == null || secretDoc.roundNumber != room.roundNumber) {
            CircularProgressIndicator(color = SpyGold)
            Text("جارٍ استلام كارتك السري المشفر...")
        } else {
            SecretScratchCard(
                cardKey = "online_r${room.roundNumber}_${secretDoc.userId}",
                playerName = myPlayer?.name ?: "أنت",
                isSpy = secretDoc.isSpy,
                secretWord = secretDoc.secretWord,
                categoryTitle = WordCategory.fromKey(room.category).arabicTitle,
                isRevealed = localScratched || alreadyConfirmed,
                onAutoRevealed = { localScratched = true }
            )

            if (!alreadyConfirmed) {
                Button(
                    onClick = {
                        localScratched = true
                        onConfirmViewed()
                    },
                    enabled = localScratched,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldConfirm, contentColor = NoirMidnight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("online_confirm_card_tam_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تم (تتحول صورتك للأخضر لدى الجميع)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "تم تأكيد مشاهدتك للكارت السري (صورتك مضاءة بالأخضر الآن).",
                    color = EmeraldConfirm,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isHost) {
            Button(
                onClick = onHostProceedToQuestions,
                enabled = allConfirmed,
                colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("online_host_begin_questions_button")
            ) {
                Text(
                    text = if (allConfirmed) "متابعة لبدء الأسئلة" else "بانتظار ضغط جميع اللاعبين على «تم»...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnlineQuestionsPhaseSection(
    room: OnlineRoom,
    players: List<OnlineRoomPlayer>,
    currentUserId: String,
    isHost: Boolean,
    onNextQuestion: () -> Unit,
    onStartVoting: () -> Unit
) {
    val asker = players.firstOrNull { it.userId == room.currentAskerId }
    val answerer = players.firstOrNull { it.userId == room.currentAnswererId }
    val qCount = room.questionCount.toInt()
    val canControlTurn = (currentUserId == room.currentAskerId) || isHost
    val votingUnlocked = SpyGameEngine.canStartVoting(qCount)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "السؤال العشوائي رقم $qCount",
                    style = MaterialTheme.typography.titleLarge,
                    color = SpyGold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerAvatarBadge(
                            avatarIndex = asker?.avatarIndex?.toInt() ?: 0,
                            playerName = asker?.name ?: "—",
                            size = 64.dp
                        )
                        Text("يسأل", color = SpyGold, style = MaterialTheme.typography.labelMedium)
                        Text(asker?.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("⬅", style = MaterialTheme.typography.headlineMedium, color = SpyCrimson)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerAvatarBadge(
                            avatarIndex = answerer?.avatarIndex?.toInt() ?: 1,
                            playerName = answerer?.name ?: "—",
                            size = 64.dp
                        )
                        Text("يجيب", color = EmeraldConfirm, style = MaterialTheme.typography.labelMedium)
                        Text(answerer?.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "تحدثوا عبر الميكروفون المباشر لطرح السؤال والإجابة!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (canControlTurn) {
            if (!votingUnlocked) {
                Button(
                    onClick = onNextQuestion,
                    colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("online_next_question_button")
                ) {
                    Text(
                        text = "السؤال التالي (${SpyGameEngine.MIN_QUESTIONS_BEFORE_VOTE - qCount} أسئلة لفتح التصويت)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Button(
                    onClick = onStartVoting,
                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("online_start_voting_button")
                ) {
                    Icon(Icons.Filled.HowToVote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بدء التصويت للجميع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = onNextQuestion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("online_continue_questions_button")
                ) {
                    Text("الاستمرار في الأسئلة", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Text(
                text = "بانتظار «${asker?.name ?: "السائل"}» للانتقال إلى السؤال التالي${if (votingUnlocked) " أو بدء التصويت" else ""}...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OnlineVotingPhaseSection(
    room: OnlineRoom,
    state: OnlineMatchUiState,
    myPlayer: OnlineRoomPlayer?,
    currentUserId: String,
    onToggleSuspect: (String) -> Unit,
    onSubmitVote: () -> Unit
) {
    val spyCount = room.spyCount.toInt()
    val candidates = state.players.filter { it.userId != currentUserId }
    val votedCount = state.players.count { it.hasVoted }
    val connectedCount = state.players.count { it.isConnected }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "التصويت السري ($votedCount / $connectedCount صوّتوا)",
            style = MaterialTheme.typography.titleLarge,
            color = SpyGold
        )

        if (myPlayer?.hasVoted == true) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                border = BorderStroke(1.dp, EmeraldConfirm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldConfirm, modifier = Modifier.size(48.dp))
                    Text("تم تسجيل تصويتك السري بنجاح!", style = MaterialTheme.typography.titleMedium, color = EmeraldConfirm)
                    Text("بانتظار انتهاء بقية اللاعبين من التصويت...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text(
                text = "اختر $spyCount ${if (spyCount == 1) "مشتبه به" else "مشتبه بهم"} (${state.localSelectedSuspects.size}/$spyCount):",
                style = MaterialTheme.typography.bodyLarge
            )

            candidates.forEach { suspect ->
                val isSelected = suspect.userId in state.localSelectedSuspects
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) SpyCrimson.copy(alpha = 0.25f) else NoirElevatedCard
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) SpyCrimson else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSuspect(suspect.userId) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlayerAvatarBadge(
                                avatarIndex = suspect.avatarIndex.toInt(),
                                playerName = suspect.name,
                                size = 46.dp
                            )
                            Text(suspect.name, style = MaterialTheme.typography.titleMedium)
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "مختار", tint = SpyCrimson)
                        }
                    }
                }
            }

            Button(
                onClick = onSubmitVote,
                enabled = state.localSelectedSuspects.size == spyCount,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldConfirm, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("online_submit_vote_button")
            ) {
                Text("تأكيد وإرسال التصويت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnlineSpyGuessPhaseSection(
    room: OnlineRoom,
    state: OnlineMatchUiState,
    myPlayer: OnlineRoomPlayer?,
    onSubmitGuess: (String) -> Unit
) {
    val mySecret = state.mySecret
    val amISpy = mySecret?.isSpy == true && mySecret.roundNumber == room.roundNumber
    var chosenWord by rememberSaveable(room.roundNumber) { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = SpyCrimson, modifier = Modifier.size(48.dp))
        if (!amISpy) {
            Text(
                text = "يحاول الجاسوس الآن تخمين الكلمة السرية!",
                style = MaterialTheme.typography.headlineSmall,
                color = SpyGold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "لا تذكروا الكلمة عبر الميكروفون! ستظهر النتيجة فور انتهاء الجاسوس من اختياره.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            CircularProgressIndicator(color = SpyCrimson)
        } else if (myPlayer?.hasGuessedWord == true) {
            Text(
                text = "تم إرسال تخمينك السري!",
                style = MaterialTheme.typography.headlineSmall,
                color = EmeraldConfirm
            )
            Text("بانتظار انتهاء بقية الجواسيس لإعلان النتيجة...")
            CircularProgressIndicator(color = SpyGold)
        } else {
            Text(
                text = "أنت الجاسوس — خمّن الكلمة السرية لكسب ${room.spyCount * 10} نقطة!",
                style = MaterialTheme.typography.titleLarge,
                color = SpyCrimson,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            val options = mySecret?.spyOptions.orEmpty()
            options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { word ->
                        val isChosen = chosenWord == word
                        OutlinedButton(
                            onClick = { chosenWord = word },
                            border = BorderStroke(if (isChosen) 2.dp else 1.dp, if (isChosen) SpyGold else MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isChosen) SpyGold.copy(alpha = 0.2f) else NoirElevatedCard
                            ),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(word, color = if (isChosen) SpyGold else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Button(
                onClick = { chosenWord?.let(onSubmitGuess) },
                enabled = chosenWord != null,
                colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("online_submit_spy_guess_button")
            ) {
                Text("إرسال التخمين النهائي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnlineResultsPhaseSection(
    room: OnlineRoom,
    players: List<OnlineRoomPlayer>,
    phase: MatchPhase,
    isHost: Boolean,
    onHostNextRound: () -> Unit,
    onHostNewMatch: () -> Unit,
    onLeaveToHome: () -> Unit
) {
    val spies = players.filter { it.userId in room.revealedSpyIds }
    val winners = players.filter { it.userId in room.winnerIds }
    val sortedPlayers = players.sortedByDescending { it.totalScore }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (phase == MatchPhase.MATCH_OVER && winners.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(2.dp, SpyGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SpyGold, modifier = Modifier.size(54.dp))
                    Text(
                        text = if (winners.size > 1) "فوز مشترك للأبطال!" else "بطل المباراة!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = winners.joinToString(" و ") { "${it.name} (${it.totalScore} نقطة)" },
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldConfirm,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            border = BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text("الكلمة السرية: ${room.revealedWord}", style = MaterialTheme.typography.titleLarge, color = SpyGold)
                Text(
                    text = "الجواسيس: ${spies.joinToString("، ") { it.name }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyCrimson
                )
                spies.forEach { spy ->
                    val guess = room.roundGuessesSummary[spy.userId] ?: "—"
                    val right = guess == room.revealedWord
                    Text(
                        text = "• تخمين ${spy.name}: «$guess» (${if (right) "صحيح +${room.spyCount * 10}" else "خاطئ +0"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (right) EmeraldConfirm else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                Text("جدول الترتيب والنقاط (الهدف: ${room.winScore})", style = MaterialTheme.typography.titleMedium, color = SpyGold)
                sortedPlayers.forEachIndexed { idx, p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("#${idx + 1}", color = SpyGold, fontWeight = FontWeight.Bold)
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                val suspectNames = room.roundVotesSummary[p.userId].orEmpty().mapNotNull { id ->
                                    players.firstOrNull { it.userId == id }?.name
                                }
                                if (suspectNames.isNotEmpty()) {
                                    Text(
                                        text = "صوّت ضد: ${suspectNames.joinToString("، ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.totalScore} نقطة", color = SpyGold, fontWeight = FontWeight.Bold)
                            Text("+${p.lastRoundScore} هذه الجولة", color = EmeraldConfirm, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (isHost) {
            if (phase == MatchPhase.ROUND_RESULTS) {
                Button(
                    onClick = onHostNextRound,
                    colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("online_host_next_round_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الجولة التالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onHostNewMatch,
                    colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("online_host_new_match_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مباراة جديدة في نفس الغرفة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                text = if (phase == MatchPhase.ROUND_RESULTS) "بانتظار بدء المضيف للجولة التالية..."
                else "بانتظار اختيار المضيف لمباراة جديدة...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedButton(
            onClick = onLeaveToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("online_results_leave_button")
        ) {
            Text("مغادرة الغرفة والعودة للرئيسية")
        }
    }
}
