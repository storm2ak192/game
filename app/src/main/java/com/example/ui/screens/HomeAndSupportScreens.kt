package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalAppSettings
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.NoirElevatedCard
import com.example.ui.theme.NoirMidnight
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold

@Composable
fun HomeScreen(
    localSettings: LocalAppSettings,
    isSignedIn: Boolean,
    userDisplayName: String?,
    onPlayOffline: () -> Unit,
    onPlayOnline: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHowToPlay: () -> Unit
) {
    Scaffold(
        containerColor = NoirMidnight
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Hero Noir Spy Illustration Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.5.dp, SpyGold.copy(alpha = 0.55f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_noir_spy_1793298756082),
                            contentDescription = "شعار وهوية لعبة الجاسوس",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            NoirMidnight.copy(alpha = 0.72f),
                                            NoirMidnight.copy(alpha = 0.96f)
                                        )
                                    )
                                )
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = SpyGold,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "الجاسوس",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = SpyGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "لعبة الاستنتاج، الخداع الاجتماعي، والتحقيق الجماعي",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Mode Buttons
                Button(
                    onClick = onPlayOffline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpyGold,
                        contentColor = NoirMidnight
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .testTag("home_play_offline_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "اللعب أوفلاين (تمرير الهاتف)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onPlayOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpyCrimson,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .testTag("home_play_online_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "اللعب أونلاين (غرف صوتية مباشرة)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Buttons Row: Settings & How to Play
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onOpenHowToPlay,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("home_how_to_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طريقة اللعب", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.55f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = SpyGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الإعدادات",
                            color = SpyGold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Stats & Last Match Summary Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = SpyGold
                            )
                            Text(
                                text = "الإحصائيات وآخر النتائج",
                                style = MaterialTheme.typography.titleMedium,
                                color = SpyGold
                            )
                        }
                        Text(
                            text = "المباريات المحلية المكتملة: ${localSettings.offlineMatchesPlayed}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (localSettings.lastWinnerSummary.isNotBlank()) {
                            Text(
                                text = localSettings.lastWinnerSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldConfirm,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "ابدأ أول جولة لاكتشاف الجاسوس بين أصدقائك!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSignedIn && !userDisplayName.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Groups,
                                    contentDescription = null,
                                    tint = EmeraldConfirm,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "متصل بحساب الأونلاين: $userDisplayName",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldConfirm
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    localSettings: LocalAppSettings,
    isSignedIn: Boolean,
    userEmail: String?,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 560.dp)
                .padding(20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = SpyGold)
                            Column {
                                Text("المؤثرات الصوتية", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "تشغيل أصوات تفاعلية عند كشف الكروت والتصويت والفوز",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = localSettings.soundEffectsEnabled,
                            onCheckedChange = onToggleSound,
                            modifier = Modifier.testTag("toggle_sound_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.Vibration, contentDescription = null, tint = SpyGold)
                            Column {
                                Text("الاهتزاز اللمسي", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "اهتزاز خفيف عند خربشة الكارت السري والقرارات الحاسمة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = localSettings.vibrationEnabled,
                            onCheckedChange = onToggleVibration,
                            modifier = Modifier.testTag("toggle_vibration_switch")
                        )
                    }
                }
            }

            if (isSignedIn) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("حساب اللعب أونلاين", style = MaterialTheme.typography.titleMedium, color = SpyGold)
                        Text(
                            text = userEmail ?: "مسجّل الدخول عبر Google",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = onSignOut,
                            border = BorderStroke(1.dp, SpyCrimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = SpyCrimson)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الخروج من حساب الأونلاين", color = SpyCrimson)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("طريقة اللعب وقواعد النقاط") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("how_to_play_back_button")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            RuleSectionCard(
                stepNumber = "١",
                title = "توزيع الأدوار والكلمة السرية",
                body = "يعرف جميع اللاعبين العاديين الكلمة السرية المشتركة ضمن التصنيف المختار (الأكل، المنزل، الأدوات، الدول، أو عشوائي)، بينما يحصل الجاسوس (١ أو ٢ أو ٣ جواسيس) على رسالة سرية دون معرفة الكلمة."
            )
            RuleSectionCard(
                stepNumber = "٢",
                title = "مرحلة الأسئلة العشوائية",
                body = "يختار النظام عشوائياً لاعباً ليسأل ولاعباً ليجيب. يحاول اللاعبون كشف الجاسوس بأسئلة ذكية غير مباشرة حتى لا يفضحوا الكلمة للجاسوس!"
            )
            RuleSectionCard(
                stepNumber = "٣",
                title = "فتح التصويت بعد السؤال السابع",
                body = "بعد إتمام ٧ أسئلة على الأقل، يتاح لصاحب السؤال خيار «بدء التصويت» أو «الاستمرار في الأسئلة». عند بدء التصويت يختار كل لاعب عدد المشتبه بهم مساوياً لعدد الجواسيس في الجولة."
            )
            RuleSectionCard(
                stepNumber = "٤",
                title = "تخمين الكلمة السرية للجواسيس",
                body = "بعد اكتمال التصويت، تظهر لكل جاسوس بصورة مستقلة قائمة من ٨ كلمات من نفس التصنيف (الكلمة الصحيحة + ٧ كلمات مموهة) ليحاول تخمين الكلمة السرية."
            )
            RuleSectionCard(
                stepNumber = "٥",
                title = "احتساب النقاط وشروط الفوز",
                body = "• اللاعب العادي: يكسب ١٠ نقاط عن كل جاسوس حقيقي يكتشفه في تصويته.\n" +
                    "• الجاسوس: إذا خمّن الكلمة الصحيحة يكسب ١٠ نقاط (جاسوس واحد)، أو ٢٠ نقطة (جاسوسان)، أو ٣٠ نقطة (ثلاثة جواسيس).\n" +
                    "• النقاط مستقلة تماماً: نجاح الجاسوس في التخمين لا يلغي نقاط من اكتشفوه والعكس صحيح.\n" +
                    "• يفوز كل من يصل إلى حد الفوز المحدد (٥٠ / ١٠٠ / ١٥٠ / ٢٠٠ أو مخصص) وإذا وصل أكثر من لاعب في نفس الجولة يفوزون معاً!"
            )
        }
    }
}

@Composable
private fun RuleSectionCard(stepNumber: String, title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NoirElevatedCard),
        border = BorderStroke(1.dp, SpyGold.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(SpyGold)
                ) {
                    Text(
                        text = stepNumber,
                        color = NoirMidnight,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpyGold
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
