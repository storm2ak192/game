package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LocalAppSettings
import com.example.data.model.MatchPhase
import com.example.game.audio.SoundAndHapticsManager
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import com.example.game.offline.OfflineMatchViewModel
import com.example.game.offline.OfflinePlayer
import com.example.game.offline.OfflineUiState
import com.example.ui.components.AvatarPickerGrid
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.components.SecretScratchCard
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.NoirElevatedCard
import com.example.ui.theme.NoirMidnight
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMatchHostScreen(
    viewModel: OfflineMatchViewModel,
    localSettings: LocalAppSettings,
    soundManager: SoundAndHapticsManager,
    onRecordMatchWinnerSummary: (winnerNames: List<String>, topScore: Int) -> Unit,
    onExitToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeaveConfirmation by rememberSaveable { mutableStateOf(false) }
    var lastRecordedWinnerRound by rememberSaveable { mutableIntStateOf(-1) }

    val isMatchActive = state.phase != MatchPhase.LOBBY

    val handleBackRequest = {
        if (isMatchActive) {
            showLeaveConfirmation = true
        } else {
            onExitToHome()
        }
    }

    BackHandler(onBack = handleBackRequest)

    LaunchedEffect(state.phase, state.roundNumber) {
        if (state.phase == MatchPhase.MATCH_OVER && state.roundNumber != lastRecordedWinnerRound) {
            lastRecordedWinnerRound = state.roundNumber
            soundManager.playVictory(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
            val winners = state.players.filter { it.id in state.winnerIds }
            if (winners.isNotEmpty()) {
                val topScore = winners.maxOf { it.totalScore }
                onRecordMatchWinnerSummary(winners.map { it.name }, topScore)
            }
        }
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("تأكيد إنهاء المباراة", color = SpyGold) },
            text = {
                Text("هل أنت متأكد من مغادرة المباراة الحالية؟ سيتم فقدان تقدم الجولة والنقاط الحالية.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmation = false
                        viewModel.resetToSetupLobby()
                        onExitToHome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson),
                    modifier = Modifier.testTag("confirm_leave_offline_button")
                ) {
                    Text("مغادرة وإنهاء", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveConfirmation = false },
                    modifier = Modifier.testTag("cancel_leave_offline_button")
                ) {
                    Text("البقاء في اللعبة", color = SpyGold)
                }
            },
            containerColor = NoirElevatedCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (state.phase) {
                        MatchPhase.LOBBY -> "إعداد مباراة أوفلاين"
                        MatchPhase.ROLE_REVEAL -> "توزيع الكروت السرية — جولة ${state.roundNumber}"
                        MatchPhase.QUESTIONS -> "مرحلة الأسئلة — جولة ${state.roundNumber}"
                        MatchPhase.VOTING -> "مرحلة التصويت السري"
                        MatchPhase.SPY_GUESS -> "تخمين الجاسوس للكلمة"
                        MatchPhase.ROUND_RESULTS -> "نتائج الجولة ${state.roundNumber}"
                        MatchPhase.MATCH_OVER -> "النتيجة النهائية والفائزون"
                    }
                    Text(titleText, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBackRequest,
                        modifier = Modifier.testTag("offline_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoirMidnight)
            )
        },
        containerColor = NoirMidnight
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.phase) {
                MatchPhase.LOBBY -> OfflineSetupContent(
                    state = state,
                    viewModel = viewModel,
                    onStartMatch = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.startMatchOrNextRound(isNewMatch = true)
                    }
                )
                MatchPhase.ROLE_REVEAL -> OfflineRoleRevealContent(
                    state = state,
                    onCardRevealed = {
                        soundManager.playScratchReveal(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.markCurrentScratchCardRevealed()
                    },
                    onNextPlayer = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.advanceToNextPlayerCard()
                    },
                    onStartQuestions = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.beginQuestionsPhase()
                    }
                )
                MatchPhase.QUESTIONS -> OfflineQuestionsContent(
                    state = state,
                    onNextQuestion = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.nextQuestion()
                    },
                    onStartVoting = {
                        soundManager.playVoteWarning(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.startVotingPhase()
                    }
                )
                MatchPhase.VOTING -> OfflineVotingContent(
                    state = state,
                    onToggleSuspect = { suspectId ->
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.toggleSuspectSelection(suspectId)
                    },
                    onConfirmVote = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.confirmCurrentPlayerVote()
                    }
                )
                MatchPhase.SPY_GUESS -> OfflineSpyGuessContent(
                    state = state,
                    onSubmitGuess = { word ->
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.submitCurrentSpyGuess(word)
                    }
                )
                MatchPhase.ROUND_RESULTS,
                MatchPhase.MATCH_OVER -> OfflineResultsContent(
                    state = state,
                    onNextRound = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.startMatchOrNextRound(isNewMatch = false)
                    },
                    onNewMatch = {
                        soundManager.playTap(localSettings.soundEffectsEnabled, localSettings.vibrationEnabled)
                        viewModel.resetToSetupLobby()
                    },
                    onReturnHome = {
                        viewModel.resetToSetupLobby()
                        onExitToHome()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OfflineSetupContent(
    state: OfflineUiState,
    viewModel: OfflineMatchViewModel,
    onStartMatch: () -> Unit
) {
    val context = LocalContext.current
    var newPlayerName by rememberSaveable { mutableStateOf("") }
    var newPlayerAvatarIndex by rememberSaveable { mutableIntStateOf(4) }
    var newPlayerCustomPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAvatarPlayer by remember { mutableStateOf<OfflinePlayer?>(null) }

    // Zero-permission Android Photo Picker for custom avatar selection (Play Policy compliant)
    val newPlayerPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            newPlayerCustomPhotoUri = uri.toString()
        }
    }

    val existingPlayerPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = editingAvatarPlayer
        if (uri != null && target != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.updatePlayerAvatar(target.id, target.avatarIndex, uri.toString())
            editingAvatarPlayer = null
        }
    }

    if (editingAvatarPlayer != null) {
        val target = editingAvatarPlayer!!
        ModalBottomSheet(
            onDismissRequest = { editingAvatarPlayer = null },
            containerColor = NoirElevatedCard
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "تغيير صورة «${target.name}»",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold
                )
                OutlinedButton(
                    onClick = {
                        existingPlayerPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, SpyGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_custom_photo_existing_button")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = SpyGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختيار صورة شخصية من المعرض", color = SpyGold)
                }
                AvatarPickerGrid(
                    selectedIndex = target.avatarIndex,
                    onSelectAvatar = { idx ->
                        viewModel.updatePlayerAvatar(target.id, idx, customPhotoUri = null)
                        editingAvatarPlayer = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 580.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (!state.errorMessage.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpyCrimson.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, SpyCrimson),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.errorMessage,
                    color = Color(0xFFFFCDD2),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(14.dp)
                        .testTag("offline_error_banner")
                )
            }
        }

        // 1. Players Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "اللاعبون (${state.players.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold
                )
                Text(
                    text = "اضغط على الصورة الرمزية لأي لاعب لتغييرها أو رفع صورة مخصصة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Add Player Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlayerAvatarBadge(
                        avatarIndex = newPlayerAvatarIndex,
                        customPhotoUri = newPlayerCustomPhotoUri,
                        playerName = newPlayerName.ifBlank { "لاعب جديد" },
                        size = 48.dp,
                        modifier = Modifier
                            .testTag("new_player_avatar_cycle")
                            .clickable {
                                newPlayerCustomPhotoUri = null
                                newPlayerAvatarIndex = (newPlayerAvatarIndex + 1) % 15
                            }
                    )
                    IconButton(
                        onClick = {
                            newPlayerPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("new_player_pick_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "رفع صورة للاعب الجديد",
                            tint = SpyGold
                        )
                    }
                    OutlinedTextField(
                        value = newPlayerName,
                        onValueChange = { newPlayerName = it },
                        placeholder = { Text("اسم لاعب جديد...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("offline_new_player_input")
                    )
                    Button(
                        onClick = {
                            val beforeSize = state.players.size
                            viewModel.addPlayer(
                                name = newPlayerName,
                                avatarIndex = newPlayerAvatarIndex,
                                customPhotoUri = newPlayerCustomPhotoUri
                            )
                            if (newPlayerName.trim().isNotEmpty() && beforeSize < 12) {
                                newPlayerName = ""
                                newPlayerCustomPhotoUri = null
                                newPlayerAvatarIndex = (newPlayerAvatarIndex + 1) % 15
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpyGold,
                            contentColor = NoirMidnight
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("offline_add_player_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "إضافة لاعب")
                    }
                }

                // Current Players List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.players.forEach { player ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NoirMidnight.copy(alpha = 0.65f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PlayerAvatarBadge(
                                        avatarIndex = player.avatarIndex,
                                        customPhotoUri = player.customPhotoUri,
                                        playerName = player.name,
                                        size = 48.dp,
                                        modifier = Modifier.clickable { editingAvatarPlayer = player }
                                    )
                                    Text(
                                        text = player.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removePlayer(player.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("remove_player_${player.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "حذف ${player.name}",
                                        tint = SpyCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Category Selection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("تصنيف الكلمة السرية", style = MaterialTheme.typography.titleMedium, color = SpyGold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WordCategory.entries.forEach { cat ->
                        val selected = state.category == cat
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.arabicTitle) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpyGold,
                                selectedLabelColor = NoirMidnight
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("offline_category_${cat.name}")
                        )
                    }
                }
            }
        }

        // 3. Spy Count Card (1, 2, or 3)
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("عدد الجواسيس في الجولة", style = MaterialTheme.typography.titleMedium, color = SpyGold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1 to "جاسوس ١", 2 to "جاسوسان ٢", 3 to "٣ جواسيس").forEach { (count, label) ->
                        val selected = state.spyCount == count
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectSpyCount(count) },
                            label = {
                                Text(
                                    text = label,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpyCrimson,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("offline_spy_count_$count")
                        )
                    }
                }
                Text(
                    text = "الحد الأدنى المطلوب: ${state.spyCount + 2} لاعبين لوجود ${state.spyCount} جاسوس.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Win Score Threshold Card (50, 100, 150, 200, or Custom)
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("نقاط الفوز بالمباراة", style = MaterialTheme.typography.titleMedium, color = SpyGold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(50, 100, 150, 200).forEach { scorePreset ->
                        val selected = !state.useCustomWinScore && state.winScore == scorePreset
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectPresetWinScore(scorePreset) },
                            label = { Text("$scorePreset نقطة") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpyGold,
                                selectedLabelColor = NoirMidnight
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("offline_win_score_$scorePreset")
                        )
                    }
                }
                OutlinedTextField(
                    value = state.customWinScoreInput,
                    onValueChange = { viewModel.updateCustomWinScore(it) },
                    label = { Text("أو أدخل نقاط فوز مخصصة (١٠ - ١٠٠٠)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_custom_win_score_input")
                )
            }
        }

        // Start Match CTA
        Button(
            onClick = onStartMatch,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldConfirm,
                contentColor = NoirMidnight
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("offline_start_match_button")
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "بدء المباراة وتوزيع الكروت السرية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OfflineRoleRevealContent(
    state: OfflineUiState,
    onCardRevealed: () -> Unit,
    onNextPlayer: () -> Unit,
    onStartQuestions: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (state.allCardsDistributedBannerVisible) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                border = BorderStroke(1.5.dp, EmeraldConfirm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldConfirm,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "اكتمل توزيع الأدوار السرية بنجاح!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = EmeraldConfirm,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "أصبح كل لاعب الآن يعرف دوره السري. استعدوا لمرحلة التحقيق والأسئلة العشوائية!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onStartQuestions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpyGold,
                            contentColor = NoirMidnight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("offline_begin_questions_button")
                    ) {
                        Icon(Icons.Filled.QuestionAnswer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بدء مرحلة الأسئلة", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            return@Column
        }

        val currentPlayer = state.players.getOrNull(state.currentCardPlayerIndex) ?: return@Column
        val isSpy = currentPlayer.id in state.spyIds
        val secretWord = state.currentWordSelection?.secretWord.orEmpty()
        val effectiveCategoryTitle = state.currentWordSelection?.effectiveCategory?.arabicTitle
            ?: state.category.arabicTitle

        Text(
            text = "اللاعب ${state.currentCardPlayerIndex + 1} من ${state.players.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PlayerAvatarBadge(
            avatarIndex = currentPlayer.avatarIndex,
            customPhotoUri = currentPlayer.customPhotoUri,
            playerName = currentPlayer.name,
            size = 76.dp
        )

        Text(
            text = "سلّم الهاتف إلى: ${currentPlayer.name}",
            style = MaterialTheme.typography.headlineSmall,
            color = SpyGold,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("current_scratch_player_name")
        )

        SecretScratchCard(
            cardKey = "r${state.roundNumber}_p${state.currentCardPlayerIndex}_${currentPlayer.id}",
            playerName = currentPlayer.name,
            isSpy = isSpy,
            secretWord = secretWord,
            categoryTitle = effectiveCategoryTitle,
            isRevealed = state.isCurrentCardRevealed,
            onAutoRevealed = onCardRevealed
        )

        if (state.isCurrentCardRevealed) {
            Button(
                onClick = onNextPlayer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldConfirm,
                    contentColor = NoirMidnight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("offline_next_card_button")
            ) {
                Text(
                    text = if (state.currentCardPlayerIndex + 1 < state.players.size) {
                        "متابعة وتمرير الهاتف للاعب التالي"
                    } else {
                        "متابعة وإنهاء توزيع الكروت"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OfflineQuestionsContent(
    state: OfflineUiState,
    onNextQuestion: () -> Unit,
    onStartVoting: () -> Unit
) {
    val asker = state.players.firstOrNull { it.id == state.currentAskerId }
    val answerer = state.players.firstOrNull { it.id == state.currentAnswererId }
    val canVote = SpyGameEngine.canStartVoting(state.questionCount)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
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
                    text = "السؤال رقم ${state.questionCount} (الحد الأدنى للتصويت: ${SpyGameEngine.MIN_QUESTIONS_BEFORE_VOTE})",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold,
                    modifier = Modifier.testTag("offline_question_counter")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (asker != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PlayerAvatarBadge(
                                avatarIndex = asker.avatarIndex,
                                customPhotoUri = asker.customPhotoUri,
                                playerName = asker.name,
                                size = 68.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("السائل", style = MaterialTheme.typography.labelMedium, color = SpyGold)
                            Text(asker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("يسأل ⬅", style = MaterialTheme.typography.titleLarge, color = SpyCrimson)

                    if (answerer != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PlayerAvatarBadge(
                                avatarIndex = answerer.avatarIndex,
                                customPhotoUri = answerer.customPhotoUri,
                                playerName = answerer.name,
                                size = 68.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("المجيب", style = MaterialTheme.typography.labelMedium, color = EmeraldConfirm)
                            Text(answerer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "وجّه سؤالاً ذكياً حول الكلمة دون كشفها للجاسوس!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!canVote) {
            Button(
                onClick = onNextQuestion,
                colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("offline_next_question_button")
            ) {
                Text(
                    text = "السؤال التالي (${SpyGameEngine.MIN_QUESTIONS_BEFORE_VOTE - state.questionCount} متبقية لفتح التصويت)",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            // Question 7+ reached: show BOTH Start Voting and Continue Questions
            Button(
                onClick = onStartVoting,
                colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("offline_start_voting_button")
            ) {
                Icon(Icons.Filled.HowToVote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("بدء التصويت الآن", style = MaterialTheme.typography.titleMedium)
            }

            FilledTonalButton(
                onClick = onNextQuestion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("offline_continue_questions_button")
            ) {
                Text("الاستمرار في الأسئلة", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OfflineVotingContent(
    state: OfflineUiState,
    onToggleSuspect: (String) -> Unit,
    onConfirmVote: () -> Unit
) {
    val voter = state.players.getOrNull(state.currentVoterIndex) ?: return
    val candidates = state.players.filter { it.id != voter.id }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "تصويت سري — اللاعب ${state.currentVoterIndex + 1} من ${state.players.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PlayerAvatarBadge(
            avatarIndex = voter.avatarIndex,
            customPhotoUri = voter.customPhotoUri,
            playerName = voter.name,
            size = 64.dp
        )
        Text(
            text = "دور التصويت لـ: ${voter.name}",
            style = MaterialTheme.typography.headlineSmall,
            color = SpyGold,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "اختر ${state.spyCount} ${if (state.spyCount == 1) "مشتبه به" else "مشتبه بهم"} (${state.currentVoterSelections.size}/${state.spyCount})",
            style = MaterialTheme.typography.bodyLarge
        )

        if (!state.errorMessage.isNullOrBlank()) {
            Text(state.errorMessage, color = SpyCrimson, style = MaterialTheme.typography.bodyMedium)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            candidates.forEach { suspect ->
                val isSelected = suspect.id in state.currentVoterSelections
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
                        .testTag("vote_suspect_${suspect.name}")
                        .clickable { onToggleSuspect(suspect.id) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PlayerAvatarBadge(
                                avatarIndex = suspect.avatarIndex,
                                customPhotoUri = suspect.customPhotoUri,
                                playerName = suspect.name,
                                size = 48.dp
                            )
                            Text(suspect.name, style = MaterialTheme.typography.titleMedium)
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "مختار", tint = SpyCrimson)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onConfirmVote,
            enabled = state.currentVoterSelections.size == state.spyCount,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldConfirm, contentColor = NoirMidnight),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("offline_confirm_vote_button")
        ) {
            Text("تأكيد التصويت السري", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfflineSpyGuessContent(
    state: OfflineUiState,
    onSubmitGuess: (String) -> Unit
) {
    val spyId = state.spyOrderForGuessing.getOrNull(state.currentSpyGuessIndex) ?: return
    val spyPlayer = state.players.firstOrNull { it.id == spyId } ?: return
    val options = state.currentWordSelection?.spyOptions.orEmpty()
    var selectedOption by remember(spyId, state.roundNumber) { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = SpyCrimson, modifier = Modifier.size(48.dp))
        Text(
            text = "فرصة الجاسوس لتخمين الكلمة السرية!",
            style = MaterialTheme.typography.headlineSmall,
            color = SpyCrimson,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "سلّم الهاتف إلى الجاسوس: ${spyPlayer.name}",
            style = MaterialTheme.typography.titleLarge,
            color = SpyGold
        )
        Text(
            text = "اختر الكلمة السرية الصحيحة من بين الخيارات الـ ٨ التالية لكسب ${state.spyCount * 10} نقطة:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.chunked(2).forEach { rowWords ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowWords.forEach { word ->
                        val chosen = selectedOption == word
                        OutlinedButton(
                            onClick = { selectedOption = word },
                            border = BorderStroke(
                                width = if (chosen) 2.dp else 1.dp,
                                color = if (chosen) SpyGold else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (chosen) SpyGold.copy(alpha = 0.2f) else NoirElevatedCard
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("spy_option_$word")
                        ) {
                            Text(
                                text = word,
                                color = if (chosen) SpyGold else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { selectedOption?.let(onSubmitGuess) },
            enabled = selectedOption != null,
            colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("offline_submit_spy_guess_button")
        ) {
            Text("تأكيد تخمين الكلمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfflineResultsContent(
    state: OfflineUiState,
    onNextRound: () -> Unit,
    onNewMatch: () -> Unit,
    onReturnHome: () -> Unit
) {
    val secretWord = state.currentWordSelection?.secretWord.orEmpty()
    val spies = state.players.filter { it.id in state.spyIds }
    val winners = state.players.filter { it.id in state.winnerIds }
    val sortedLeaderboard = state.players.sortedByDescending { it.totalScore }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (state.phase == MatchPhase.MATCH_OVER && winners.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(2.dp, SpyGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SpyGold, modifier = Modifier.size(56.dp))
                    Text(
                        text = if (winners.size > 1) "فوز مشترك مستحق!" else "لدينا بطل المباراة!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = winners.joinToString(" و ") { "${it.name} (${it.totalScore} نقطة)" },
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldConfirm,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("offline_winners_banner")
                    )
                }
            }
        }

        // Secret Word & Spies Reveal Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            border = BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("الكلمة السرية في هذه الجولة: $secretWord", style = MaterialTheme.typography.titleLarge, color = SpyGold)
                Text(
                    text = "الجواسيس: ${spies.joinToString("، ") { it.name }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyCrimson
                )
                spies.forEach { spy ->
                    val guess = state.guessesBySpy[spy.id] ?: "—"
                    val right = guess == secretWord
                    Text(
                        text = "• تخمين ${spy.name}: «$guess» (${if (right) "صحيح +${state.spyCount * 10}" else "خاطئ +0"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (right) EmeraldConfirm else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Leaderboard & Round Points Breakdown
        Card(
            colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "ترتيب النقاط (حد الفوز: ${state.winScore})",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold
                )
                sortedLeaderboard.forEachIndexed { idx, player ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("#${idx + 1}", color = SpyGold, fontWeight = FontWeight.Bold)
                            PlayerAvatarBadge(
                                avatarIndex = player.avatarIndex,
                                customPhotoUri = player.customPhotoUri,
                                playerName = player.name,
                                size = 42.dp
                            )
                            Column {
                                Text(player.name, style = MaterialTheme.typography.titleMedium)
                                val votedNames = state.votesByVoter[player.id].orEmpty().mapNotNull { id ->
                                    state.players.firstOrNull { it.id == id }?.name
                                }
                                if (votedNames.isNotEmpty()) {
                                    Text(
                                        text = "صوّت ضد: ${votedNames.joinToString("، ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${player.totalScore} نقطة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (player.id in state.winnerIds) EmeraldConfirm else SpyGold
                            )
                            Text(
                                text = "+${player.lastRoundScore} هذه الجولة",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldConfirm
                            )
                        }
                    }
                }
            }
        }

        if (state.phase == MatchPhase.ROUND_RESULTS) {
            Button(
                onClick = onNextRound,
                colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("offline_next_round_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("بدء الجولة التالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onNewMatch,
                colors = ButtonDefaults.buttonColors(containerColor = SpyGold, contentColor = NoirMidnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("offline_new_match_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مباراة جديدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onReturnHome,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("offline_return_home_button")
        ) {
            Text("العودة إلى الشاشة الرئيسية")
        }
    }
}
