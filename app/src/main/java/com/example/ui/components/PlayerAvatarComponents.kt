package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import com.example.ui.theme.EmeraldConfirm
import com.example.ui.theme.SpyCrimson
import com.example.ui.theme.SpyGold

data class BuiltInAvatar(
    val index: Int,
    val labelAr: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color
)

object SpyAvatarCatalog {
    val avatars: List<BuiltInAvatar> = listOf(
        BuiltInAvatar(0, "المحقق الذهبي", Icons.Filled.Search, Color(0xFF1E3A8A), Color(0xFF3B82F6)),
        BuiltInAvatar(1, "البصمة السرية", Icons.Filled.Fingerprint, Color(0xFF7C2D12), Color(0xFFEA580C)),
        BuiltInAvatar(2, "عين الصقر", Icons.Filled.Visibility, Color(0xFF064E3B), Color(0xFF10B981)),
        BuiltInAvatar(3, "الدرع الحصين", Icons.Filled.Shield, Color(0xFF4C1D95), Color(0xFF8B5CF6)),
        BuiltInAvatar(4, "العقل المدبر", Icons.Filled.Psychology, Color(0xFF831843), Color(0xFFEC4899)),
        BuiltInAvatar(5, "الرادار الخفي", Icons.Filled.Radar, Color(0xFF134E4A), Color(0xFF14B8A6)),
        BuiltInAvatar(6, "المفتاح المشفر", Icons.Filled.Key, Color(0xFF713F12), Color(0xFFEAB308)),
        BuiltInAvatar(7, "الماس الأسود", Icons.Filled.Diamond, Color(0xFF1E293B), Color(0xFF64748B)),
        BuiltInAvatar(8, "الشعلة الغامضة", Icons.Filled.LocalFireDepartment, Color(0xFF7F1D1D), Color(0xFFEF4444)),
        BuiltInAvatar(9, "الحارس الأمني", Icons.Filled.Security, Color(0xFF14532D), Color(0xFF22C55E)),
        BuiltInAvatar(10, "الأحجية", Icons.Filled.Extension, Color(0xFF312E81), Color(0xFF6366F1)),
        BuiltInAvatar(11, "البرق السريع", Icons.Filled.Bolt, Color(0xFF78350F), Color(0xFFF59E0B)),
        BuiltInAvatar(12, "السفير الدولي", Icons.Filled.Public, Color(0xFF0C4A6E), Color(0xFF0EA5E9)),
        BuiltInAvatar(13, "القاضي", Icons.Filled.AccountBalance, Color(0xFF3B0764), Color(0xFFA855F7)),
        BuiltInAvatar(14, "النجم الخفي", Icons.Filled.AutoAwesome, Color(0xFF881337), Color(0xFFF43F5E))
    )

    fun getByIndex(index: Int): BuiltInAvatar {
        return avatars[index.mod(avatars.size)]
    }
}

@Composable
fun PlayerAvatarBadge(
    avatarIndex: Int,
    customPhotoUri: String? = null,
    playerName: String,
    size: Dp = 56.dp,
    isConfirmedGreen: Boolean = false,
    isHost: Boolean = false,
    isDisconnected: Boolean = false,
    showMicBadge: Boolean = false,
    isMicMuted: Boolean = false,
    canToggleMic: Boolean = false,
    onToggleMic: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val avatar = SpyAvatarCatalog.getByIndex(avatarIndex)
    val context = LocalContext.current

    val customBitmap = remember(customPhotoUri) {
        if (customPhotoUri.isNullOrBlank()) {
            null
        } else {
            try {
                context.contentResolver.openInputStream(Uri.parse(customPhotoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val ringColor = when {
        isConfirmedGreen -> EmeraldConfirm
        isDisconnected -> MaterialTheme.colorScheme.outline
        isHost -> SpyGold
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        brush = if (isConfirmedGreen) {
                            Brush.linearGradient(listOf(Color(0xFF059669), EmeraldConfirm))
                        } else {
                            Brush.linearGradient(listOf(avatar.gradientStart, avatar.gradientEnd))
                        }
                    )
                    .then(
                        if (ringColor != Color.Transparent) {
                            Modifier.border(BorderStroke(3.dp, ringColor), CircleShape)
                        } else {
                            Modifier.border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                        }
                    )
                    .semantics {
                        contentDescription = "صورة اللاعب $playerName ${avatar.labelAr}"
                    }
            ) {
                if (customBitmap != null && !isConfirmedGreen) {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = "صورة اللاعب $playerName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(size).clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = avatar.icon,
                        contentDescription = avatar.labelAr,
                        tint = if (isDisconnected) Color.White.copy(alpha = 0.4f) else Color.White,
                        modifier = Modifier.size(size * 0.52f)
                    )
                }
            }
        }

        if (showMicBadge) {
            val micBg = if (isMicMuted) SpyCrimson.copy(alpha = 0.22f) else EmeraldConfirm.copy(alpha = 0.22f)
            val micTint = if (isMicMuted) SpyCrimson else EmeraldConfirm
            val micBorder = if (isMicMuted) SpyCrimson else EmeraldConfirm

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (canToggleMic) 48.dp else 28.dp)
                    .clip(CircleShape)
                    .background(micBg)
                    .border(1.dp, micBorder.copy(alpha = 0.7f), CircleShape)
                    .then(
                        if (canToggleMic && onToggleMic != null) {
                            Modifier
                                .testTag("my_mic_toggle_button")
                                .clickable { onToggleMic() }
                        } else {
                            Modifier
                        }
                    )
                    .semantics {
                        contentDescription = if (isMicMuted) {
                            "ميكروفون $playerName مغلق"
                        } else {
                            "ميكروفون $playerName مفتوح"
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = micTint,
                    modifier = Modifier.size(if (canToggleMic) 22.dp else 15.dp)
                )
            }
        }
    }
}

@Composable
fun AvatarPickerGrid(
    selectedIndex: Int,
    onSelectAvatar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 60.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        itemsIndexed(SpyAvatarCatalog.avatars) { idx, item ->
            val isSelected = idx == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(item.gradientStart, item.gradientEnd)))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) SpyGold else Color.White.copy(alpha = 0.18f),
                        shape = CircleShape
                    )
                    .testTag("avatar_option_$idx")
                    .clickable { onSelectAvatar(idx) }
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.labelAr,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
