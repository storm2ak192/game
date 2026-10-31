package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.GoogleSignInButton
import com.example.auth.attemptAutoSignIn
import com.example.game.online.OnlineMatchUiState
import com.example.ui.components.AvatarPickerGrid
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.NoirElevatedCard
import com.example.ui.theme.NoirMidnight
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineAuthGateScreen(
    pendingInviteCode: String?,
    onAuthCompleted: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var authError by remember { mutableStateOf<String?>(null) }
    var checkingAutoSignIn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                checkingAutoSignIn = false
                onAuthCompleted()
            },
            onUnauthenticated = {
                checkingAutoSignIn = false
            },
            scope = scope
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اللعب أونلاين") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("online_auth_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoirMidnight)
            )
        },
        containerColor = NoirMidnight
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                border = BorderStroke(1.5.dp, SpyGold.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = SpyGold,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "غرف التحقيق الصوتية المباشرة",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpyGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "للانضمام إلى الغرف الجماعية عبر الإنترنت وحماية سرية الأدوار والتواصل الصوتي المباشر، سجّل الدخول بحساب Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!pendingInviteCode.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EmeraldConfirm.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, EmeraldConfirm)
                        ) {
                            Text(
                                text = "دعوة جاهزة للانضمام إلى الغرفة: $pendingInviteCode",
                                color = EmeraldConfirm,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = EmeraldConfirm, modifier = Modifier.size(18.dp))
                            Text("غرف خاصة بكود", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = SpyGold, modifier = Modifier.size(18.dp))
                            Text("تحدث صوتي حي", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (!authError.isNullOrBlank()) {
                        Text(
                            text = authError!!,
                            color = SpyCrimson,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("online_auth_error_text")
                        )
                    }

                    if (checkingAutoSignIn) {
                        CircularProgressIndicator(color = SpyGold)
                    } else {
                        GoogleSignInButton(
                            onAuthSuccess = onAuthCompleted,
                            onAuthError = { msg -> authError = msg }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineEntryHubScreen(
    state: OnlineMatchUiState,
    defaultName: String,
    defaultAvatarIndex: Int,
    pendingInviteCode: String?,
    onConsumePendingInviteCode: () -> Unit,
    onUpdateProfileInputs: (name: String, avatarIndex: Int) -> Unit,
    onUpdateJoinCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var isJoinTab by rememberSaveable { mutableStateOf(!pendingInviteCode.isNullOrBlank()) }

    LaunchedEffect(defaultName, defaultAvatarIndex) {
        if (state.playerNameInput.isBlank() && defaultName.isNotBlank()) {
            onUpdateProfileInputs(defaultName, defaultAvatarIndex)
        }
    }

    LaunchedEffect(pendingInviteCode) {
        if (!pendingInviteCode.isNullOrBlank()) {
            isJoinTab = true
            onUpdateJoinCode(pendingInviteCode)
            onConsumePendingInviteCode()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اللعب أونلاين") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("online_hub_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoirMidnight)
            )
        },
        containerColor = NoirMidnight
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Mode Switcher: Create Room vs Join Room (Mandated by Section 3)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = !isJoinTab,
                    onClick = { isJoinTab = false },
                    label = {
                        Text(
                            text = "إنشاء غرفة",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpyGold,
                        selectedLabelColor = NoirMidnight
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("tab_create_room")
                )

                FilterChip(
                    selected = isJoinTab,
                    onClick = { isJoinTab = true },
                    label = {
                        Text(
                            text = "الانضمام إلى غرفة",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpyCrimson,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("tab_join_room")
                )
            }

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
                            .testTag("online_entry_error_banner")
                    )
                }
            }

            // Player Identity Card (Name + Avatar Picker)
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "هويتك داخل الغرفة",
                        style = MaterialTheme.typography.titleMedium,
                        color = SpyGold
                    )
                    PlayerAvatarBadge(
                        avatarIndex = state.selectedAvatarIndex,
                        playerName = state.playerNameInput.ifBlank { "لاعب" },
                        size = 68.dp
                    )
                    OutlinedTextField(
                        value = state.playerNameInput,
                        onValueChange = { onUpdateProfileInputs(it, state.selectedAvatarIndex) },
                        label = { Text("اسمك الظاهر للاعبين") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("online_player_name_input")
                    )
                    Text(
                        text = "اختر صورتك الرمزية:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    AvatarPickerGrid(
                        selectedIndex = state.selectedAvatarIndex,
                        onSelectAvatar = { idx -> onUpdateProfileInputs(state.playerNameInput, idx) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(195.dp)
                    )
                }
            }

            // Action Card: Create vs Join
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (!isJoinTab) {
                        Text(
                            text = "إنشاء غرفة جديدة كمضيف (Host)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SpyGold
                        )
                        Text(
                            text = "ستحصل على كود غرفة فريد ورابط دعوة مباشر لمشاركته مع أصدقائك والتحكم بإعدادات الجولات.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onCreateRoom,
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpyGold,
                                contentColor = NoirMidnight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("create_online_room_button")
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NoirMidnight,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.Filled.AddCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إنشاء الغرفة الآن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "الانضمام عبر كود الغرفة",
                            style = MaterialTheme.typography.titleMedium,
                            color = SpyGold
                        )
                        OutlinedTextField(
                            value = state.joinCodeInput,
                            onValueChange = onUpdateJoinCode,
                            label = { Text("أدخل كود الغرفة (مثال: K7P9X2)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_room_code_input")
                        )
                        Button(
                            onClick = onJoinRoom,
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpyCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("join_online_room_button")
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("انضمام إلى الغرفة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Recent Online History Summary
            if (state.personalHistory.isNotEmpty()) {
                val wins = state.personalHistory.count { it.won }
                Card(
                    colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SpyGold)
                        Text(
                            text = "سجلك في الأونلاين: ${state.personalHistory.size} مباريات — $wins انتصارات",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldConfirm
                        )
                    }
                }
            }
        }
    }
}
