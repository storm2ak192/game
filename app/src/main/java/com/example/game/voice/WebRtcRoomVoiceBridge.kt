package com.example.game.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.VoiceSignalDoc
import org.json.JSONArray
import org.json.JSONObject

enum class VoiceConnectionState {
    PERMISSION_REQUIRED,
    INITIALIZING,
    CONNECTED,
    MUTED,
    ERROR,
    DISCONNECTED
}

/**
 * Headless Compose-hosted WebRTC Mesh Voice Chat Engine using Chromium's built-in `RTCPeerConnection`
 * bridged over Firestore Signaling (`/rooms/{roomCode}/signals`).
 *
 * - Requests Android RECORD_AUDIO permission before activating audio tracks.
 * - Physically enables/disables outgoing microphone `MediaStreamTrack` when muted/unmuted.
 * - Automatically disconnects and stops all microphone tracks upon leaving the room (`onDispose`).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebRtcRoomVoiceBridge(
    roomCode: String,
    currentUserId: String,
    hasAudioPermission: Boolean,
    isMuted: Boolean,
    connectedPeerUids: List<String>,
    incomingSignals: List<VoiceSignalDoc>,
    onSendSignal: (targetUserId: String, type: String, payload: String) -> Unit,
    onConsumeSignal: (signalId: String) -> Unit,
    onVoiceStatusChange: (VoiceConnectionState, String?) -> Unit
) {
    val context = LocalContext.current
    val currentOnSendSignal by rememberUpdatedState(onSendSignal)
    val currentOnVoiceStatus by rememberUpdatedState(onVoiceStatusChange)

    var pageLoaded by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val processedSignalIds = remember(roomCode) { mutableSetOf<String>() }

    DisposableEffect(roomCode) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val previousMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = true
        } catch (_: Exception) {
        }

        onDispose {
            webViewRef?.evaluateJavascript("window.VoiceEngine && window.VoiceEngine.shutdown();", null)
            webViewRef?.destroy()
            webViewRef = null
            try {
                audioManager?.mode = previousMode
            } catch (_: Exception) {
            }
        }
    }

    if (!hasAudioPermission) {
        LaunchedEffect(Unit) {
            currentOnVoiceStatus(VoiceConnectionState.PERMISSION_REQUIRED, "بانتظار إذن الميكروفون للتحدث الصوتي")
        }
        return
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.let { permReq ->
                            val audioResources = permReq.resources.filter {
                                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                            }.toTypedArray()
                            if (audioResources.isNotEmpty() && hasAudioPermission) {
                                permReq.grant(audioResources)
                            } else {
                                permReq.deny()
                            }
                        }
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun sendSignal(targetUid: String, type: String, payload: String) {
                            currentOnSendSignal(targetUid, type, payload)
                        }

                        @JavascriptInterface
                        fun onVoiceStateChanged(status: String, detail: String) {
                            val mapped = when {
                                status == "MIC_READY" -> if (isMuted) VoiceConnectionState.MUTED else VoiceConnectionState.CONNECTED
                                status == "MUTED" -> VoiceConnectionState.MUTED
                                status == "UNMUTED" -> VoiceConnectionState.CONNECTED
                                status == "MIC_ERROR" -> VoiceConnectionState.ERROR
                                status.startsWith("PEER_CONNECTED") -> if (isMuted) VoiceConnectionState.MUTED else VoiceConnectionState.CONNECTED
                                status.startsWith("PEER_FAILED") -> VoiceConnectionState.ERROR
                                else -> null
                            }
                            if (mapped != null) {
                                currentOnVoiceStatus(mapped, detail.takeIf { it.isNotBlank() })
                            }
                        }
                    },
                    "VoiceAndroidBridge"
                )

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                    }
                }

                loadUrl("file:///android_asset/webrtc_voice.html")
                webViewRef = this
            }
        }
    )

    // Initialize local WebRTC audio track once HTML loads
    LaunchedEffect(pageLoaded, roomCode, currentUserId, hasAudioPermission) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (pageLoaded && hasAudioPermission) {
            currentOnVoiceStatus(VoiceConnectionState.INITIALIZING, null)
            val safeUid = JSONObject.quote(currentUserId)
            wv.evaluateJavascript(
                "window.VoiceEngine && window.VoiceEngine.initSession($safeUid, $isMuted);",
                null
            )
        }
    }

    // Physically toggle outgoing WebRTC MediaStreamTrack when `isMuted` changes
    LaunchedEffect(pageLoaded, isMuted) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (pageLoaded) {
            wv.evaluateJavascript(
                "window.VoiceEngine && window.VoiceEngine.setMuted($isMuted);",
                null
            )
        }
    }

    // Sync active room peers to establish / close RTCPeerConnections
    LaunchedEffect(pageLoaded, connectedPeerUids) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (pageLoaded) {
            val jsonArr = JSONArray(connectedPeerUids).toString()
            val quotedJson = JSONObject.quote(jsonArr)
            wv.evaluateJavascript(
                "window.VoiceEngine && window.VoiceEngine.syncRoomMembers($quotedJson);",
                null
            )
        }
    }

    // Forward incoming SDP Offers, Answers, and ICE Candidates to WebRTC engine
    LaunchedEffect(pageLoaded, incomingSignals) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageLoaded) return@LaunchedEffect
        for (sig in incomingSignals) {
            if (sig.id.isNotBlank() && processedSignalIds.add(sig.id)) {
                val senderArg = JSONObject.quote(sig.senderId)
                val typeArg = JSONObject.quote(sig.type)
                val payloadArg = JSONObject.quote(sig.payload)
                wv.evaluateJavascript(
                    "window.VoiceEngine && window.VoiceEngine.handleIncomingSignal($senderArg, $typeArg, $payloadArg);",
                    null
                )
                onConsumeSignal(sig.id)
            }
        }
    }
}
