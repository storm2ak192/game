package com.example.game.online

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MatchPhase
import com.example.data.model.OnlineRoom
import com.example.data.model.OnlineRoomPlayer
import com.example.data.model.PlayerSecretDoc
import com.example.data.model.UserMatchStatDoc
import com.example.data.model.VoiceSignalDoc
import com.example.data.repository.OnlineRoomRepository
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import com.example.game.voice.VoiceConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OnlineMatchUiState(
    val activeRoomCode: String? = null,
    val room: OnlineRoom? = null,
    val players: List<OnlineRoomPlayer> = emptyList(),
    val mySecret: PlayerSecretDoc? = null,
    val incomingVoiceSignals: List<VoiceSignalDoc> = emptyList(),
    val personalHistory: List<UserMatchStatDoc> = emptyList(),

    // Lobby / Join Form State
    val playerNameInput: String = "",
    val selectedAvatarIndex: Int = 0,
    val joinCodeInput: String = "",
    val customWinScoreInput: String = "",
    val useCustomWinScore: Boolean = false,

    // In-round local selection state before submitting to Firestore
    val localSelectedSuspects: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoToast: String? = null,

    // Real-time WebRTC Voice state
    val voiceState: VoiceConnectionState = VoiceConnectionState.PERMISSION_REQUIRED,
    val voiceStatusMessage: String? = null
)

class OnlineMatchViewModel(
    private val repository: OnlineRoomRepository,
    val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineMatchUiState())
    val uiState: StateFlow<OnlineMatchUiState> = _uiState.asStateFlow()

    private var roomObserverJob: Job? = null
    private var playersObserverJob: Job? = null
    private var secretObserverJob: Job? = null
    private var signalsObserverJob: Job? = null
    private var heartbeatJob: Job? = null

    private val usedWordsInSession = mutableSetOf<String>()
    private var lastRecordedMatchRoundKey: String? = null

    init {
        observeUserStats()
    }

    private fun observeUserStats() {
        viewModelScope.launch {
            repository.observeUserMatchHistory(currentUserId)
                .catch { e -> Log.w("OnlineVM", "History stream error", e) }
                .collect { stats ->
                    _uiState.update { it.copy(personalHistory = stats) }
                }
        }
    }

    fun setProfileInputs(name: String, avatarIndex: Int) {
        _uiState.update {
            it.copy(
                playerNameInput = name,
                selectedAvatarIndex = avatarIndex,
                errorMessage = null
            )
        }
    }

    fun setJoinCodeInput(code: String) {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }.take(10)
        _uiState.update { it.copy(joinCodeInput = clean, errorMessage = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoToast = null) }
    }

    fun updateVoiceStatus(state: VoiceConnectionState, detail: String?) {
        _uiState.update { it.copy(voiceState = state, voiceStatusMessage = detail) }
    }

    fun createOnlineRoom(onCreated: (String) -> Unit = {}) {
        val state = _uiState.value
        val trimmedName = state.playerNameInput.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال اسم اللاعب قبل إنشاء الغرفة.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val code = repository.createRoom(
                    hostName = trimmedName,
                    hostAvatarIndex = state.selectedAvatarIndex
                )
                attachToRoomStreams(code)
                _uiState.update { it.copy(isLoading = false, activeRoomCode = code) }
                onCreated(code)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "تعذر إنشاء الغرفة، تحقق من اتصالك بالإنترنت."
                    )
                }
            }
        }
    }

    fun joinOnlineRoom(overrideCode: String? = null, onJoined: (String) -> Unit = {}) {
        val state = _uiState.value
        val codeToUse = (overrideCode ?: state.joinCodeInput).trim().uppercase()
        val trimmedName = state.playerNameInput.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال اسمك واختيار صورة رمزية قبل الانضمام.") }
            return
        }
        if (codeToUse.length < 4) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال كود غرفة صحيح.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val joinedCode = repository.joinRoom(
                    rawCode = codeToUse,
                    playerName = trimmedName,
                    avatarIndex = state.selectedAvatarIndex
                )
                attachToRoomStreams(joinedCode)
                _uiState.update { it.copy(isLoading = false, activeRoomCode = joinedCode) }
                onJoined(joinedCode)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "تعذر الانضمام إلى الغرفة."
                    )
                }
            }
        }
    }

    private fun attachToRoomStreams(roomCode: String) {
        detachRoomStreams()

        roomObserverJob = viewModelScope.launch {
            repository.observeRoom(roomCode)
                .catch { e ->
                    Log.w("OnlineVM", "Room stream error", e)
                    _uiState.update { it.copy(errorMessage = "انقطع الاتصال بالغرفة: ${e.localizedMessage ?: ""}") }
                }
                .collect { room ->
                    val prevPhase = _uiState.value.room?.status
                    _uiState.update { state ->
                        state.copy(
                            room = room,
                            localSelectedSuspects = if (room?.status != prevPhase) emptySet() else state.localSelectedSuspects
                        )
                    }
                    if (room != null) {
                        if (room.revealedWord.isNotBlank()) {
                            usedWordsInSession.add(room.revealedWord)
                        }
                        orchestrateHostAutomations(room, _uiState.value.players)
                        recordMatchCompletionIfNeeded(room, _uiState.value.players)
                    }
                }
        }

        playersObserverJob = viewModelScope.launch {
            repository.observeRoomPlayers(roomCode, currentUserId)
                .catch { e -> Log.w("OnlineVM", "Players stream error", e) }
                .collect { players ->
                    _uiState.update { it.copy(players = players) }
                    val room = _uiState.value.room
                    if (room != null) {
                        orchestrateHostAutomations(room, players)
                    }
                }
        }

        secretObserverJob = viewModelScope.launch {
            repository.observeMySecret(roomCode, currentUserId)
                .catch { e -> Log.w("OnlineVM", "Secret stream error", e) }
                .collect { secretDoc ->
                    _uiState.update { it.copy(mySecret = secretDoc) }
                }
        }

        signalsObserverJob = viewModelScope.launch {
            repository.observeIncomingVoiceSignals(roomCode, currentUserId)
                .catch { e -> Log.w("OnlineVM", "Voice signal stream error", e) }
                .collect { signals ->
                    _uiState.update { it.copy(incomingVoiceSignals = signals) }
                }
        }

        // Periodic presence heartbeat & automatic Host Migration check
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                repository.sendPresenceHeartbeat(roomCode, isConnected = true)
                val currentRoom = _uiState.value.room
                val currentPlayers = _uiState.value.players
                if (currentRoom != null && currentPlayers.isNotEmpty()) {
                    // Check if host heartbeat expired (> 35 seconds stale) or host disconnected
                    val nowSec = com.google.firebase.Timestamp.now().seconds
                    val hostPlayer = currentPlayers.firstOrNull { it.userId == currentRoom.hostId }
                    val hostLastSeen = hostPlayer?.lastSeenAt?.seconds ?: nowSec
                    val isHostStale = hostPlayer != null && (nowSec - hostLastSeen > 35L)
                    if (isHostStale && currentRoom.hostId == currentUserId) {
                        // Self refresh
                        repository.sendPresenceHeartbeat(roomCode, true)
                    } else if (hostPlayer != null && !hostPlayer.isConnected) {
                        repository.attemptHostMigrationIfEligible(currentRoom, currentPlayers)
                    }
                }
                delay(12_000L)
            }
        }
    }

    /**
     * Central Host coordinator:
     * - Automatically transitions VOTING -> SPY_GUESS once all connected players submit their ballots.
     * - Automatically computes scores & transitions SPY_GUESS -> ROUND_RESULTS / MATCH_OVER once all connected spies guess.
     * - Triggers Host Migration if current host left or disconnected.
     */
    private fun orchestrateHostAutomations(room: OnlineRoom, players: List<OnlineRoomPlayer>) {
        if (players.isEmpty()) return
        viewModelScope.launch {
            if (room.hostId != currentUserId) {
                val migrated = repository.attemptHostMigrationIfEligible(room, players)
                if (migrated) {
                    _uiState.update { it.copy(infoToast = "انتقلت استضافة الغرفة إليك تلقائياً!") }
                }
                return@launch
            }

            when (MatchPhase.fromString(room.status)) {
                MatchPhase.VOTING -> {
                    val connected = players.filter { it.isConnected }
                    if (connected.isNotEmpty() && connected.all { it.hasVoted }) {
                        try {
                            repository.advanceToSpyGuessPhase(room, players)
                        } catch (e: Exception) {
                            Log.w("OnlineVM", "Advance to spy guess failed", e)
                        }
                    }
                }
                MatchPhase.SPY_GUESS -> {
                    try {
                        repository.evaluateAndFinalizeRoundIfReady(room, players)
                    } catch (e: Exception) {
                        Log.w("OnlineVM", "Finalize round failed", e)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun recordMatchCompletionIfNeeded(room: OnlineRoom, players: List<OnlineRoomPlayer>) {
        if (room.status != MatchPhase.MATCH_OVER.name) return
        val matchKey = "${room.code}_${room.roundNumber}"
        if (lastRecordedMatchRoundKey == matchKey) return
        lastRecordedMatchRoundKey = matchKey

        val me = players.firstOrNull { it.userId == currentUserId } ?: return
        val didWin = currentUserId in room.winnerIds
        viewModelScope.launch {
            repository.recordMatchStat(
                mode = "ONLINE",
                won = didWin,
                score = me.totalScore.toInt(),
                playerCount = players.size.coerceAtLeast(3)
            )
        }
    }

    // --- Host Controls ---

    fun updateSettingsAsHost(category: WordCategory, spyCount: Int, winScore: Int) {
        val room = _uiState.value.room ?: return
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            try {
                repository.updateRoomSettings(room.code, category, spyCount, winScore)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "تعذر تحديث الإعدادات.") }
            }
        }
    }

    fun setCustomWinScoreInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(customWinScoreInput = digits, useCustomWinScore = true) }
        val parsed = digits.toIntOrNull()
        val room = _uiState.value.room
        if (parsed != null && parsed in SpyGameEngine.MIN_CUSTOM_WIN_SCORE..SpyGameEngine.MAX_CUSTOM_WIN_SCORE && room != null) {
            updateSettingsAsHost(WordCategory.fromKey(room.category), room.spyCount.toInt(), parsed)
        }
    }

    fun startRoundAsHost() {
        val state = _uiState.value
        val room = state.room ?: return
        viewModelScope.launch {
            try {
                repository.startNewRound(room, state.players, usedWordsInSession)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "تعذر بدء الجولة.") }
            }
        }
    }

    fun confirmSecretViewed() {
        val code = _uiState.value.activeRoomCode ?: return
        viewModelScope.launch {
            try {
                repository.confirmSecretCardViewed(code)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "تعذر تأكيد المشاهدة.") }
            }
        }
    }

    fun startQuestionsAsHost() {
        val state = _uiState.value
        val room = state.room ?: return
        viewModelScope.launch {
            try {
                repository.startQuestionsPhase(room, state.players)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "انتظر تأكيد جميع اللاعبين.") }
            }
        }
    }

    fun advanceQuestionTurn() {
        val state = _uiState.value
        val room = state.room ?: return
        val connectedIds = state.players.filter { it.isConnected }.map { it.userId }
        if (connectedIds.size < 2) {
            _uiState.update { it.copy(errorMessage = "عدد اللاعبين المتصلين غير كافٍ للاستمرار.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.nextQuestionTurn(room.code, room.turnEventId, connectedIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun startVoting() {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            try {
                repository.triggerVotingPhase(room.code, room.turnEventId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun toggleSuspectSelection(suspectUid: String) {
        val room = _uiState.value.room ?: return
        if (suspectUid == currentUserId) return
        val spyCount = room.spyCount.toInt()
        val nextSet = _uiState.value.localSelectedSuspects.toMutableSet()
        if (suspectUid in nextSet) {
            nextSet.remove(suspectUid)
        } else {
            if (spyCount == 1) {
                nextSet.clear()
                nextSet.add(suspectUid)
            } else if (nextSet.size < spyCount) {
                nextSet.add(suspectUid)
            }
        }
        _uiState.update { it.copy(localSelectedSuspects = nextSet, errorMessage = null) }
    }

    fun submitMyVote() {
        val state = _uiState.value
        val room = state.room ?: return
        viewModelScope.launch {
            try {
                repository.submitVote(
                    roomCode = room.code,
                    roundNumber = room.roundNumber.toInt(),
                    selectedSuspectIds = state.localSelectedSuspects.toList(),
                    allPlayerIds = state.players.map { it.userId }.toSet(),
                    spyCount = room.spyCount.toInt()
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "تعذر إرسال التصويت.") }
            }
        }
    }

    fun submitMySpyWordGuess(word: String) {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            try {
                repository.submitSpyWordGuess(
                    roomCode = room.code,
                    roundNumber = room.roundNumber.toInt(),
                    guessedWord = word
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "تعذر إرسال التخمين.") }
            }
        }
    }

    fun startNewMatchAfterGameOver() {
        val state = _uiState.value
        val room = state.room ?: return
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            try {
                usedWordsInSession.clear()
                repository.resetMatchToLobby(room.code, state.players)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    // --- Microphone & Voice Signaling ---

    fun toggleMyMicrophone() {
        val state = _uiState.value
        val code = state.activeRoomCode ?: return
        val me = state.players.firstOrNull { it.userId == currentUserId } ?: return
        val newMuted = !me.isMicMuted
        viewModelScope.launch {
            repository.setMyMicrophoneMuted(code, newMuted)
        }
    }

    fun sendWebRtcSignal(targetUid: String, type: String, payload: String) {
        val code = _uiState.value.activeRoomCode ?: return
        viewModelScope.launch {
            repository.sendVoiceSignal(code, targetUid, type, payload)
        }
    }

    fun consumeWebRtcSignal(signalId: String) {
        val code = _uiState.value.activeRoomCode ?: return
        viewModelScope.launch {
            repository.deleteConsumedSignal(code, signalId)
        }
    }

    fun getInviteLink(roomCode: String): String = repository.buildInviteDeepLink(roomCode)
    fun getShareText(roomCode: String): String = repository.buildShareableMessage(roomCode)

    fun leaveCurrentRoom(onLeft: () -> Unit = {}) {
        val code = _uiState.value.activeRoomCode
        detachRoomStreams()
        _uiState.update {
            it.copy(
                activeRoomCode = null,
                room = null,
                players = emptyList(),
                mySecret = null,
                incomingVoiceSignals = emptyList(),
                localSelectedSuspects = emptySet(),
                errorMessage = null
            )
        }
        if (code != null) {
            viewModelScope.launch {
                repository.leaveRoom(code)
                onLeft()
            }
        } else {
            onLeft()
        }
    }

    private fun detachRoomStreams() {
        roomObserverJob?.cancel()
        playersObserverJob?.cancel()
        secretObserverJob?.cancel()
        signalsObserverJob?.cancel()
        heartbeatJob?.cancel()
        roomObserverJob = null
        playersObserverJob = null
        secretObserverJob = null
        signalsObserverJob = null
        heartbeatJob = null
    }

    override fun onCleared() {
        val code = _uiState.value.activeRoomCode
        detachRoomStreams()
        if (code != null) {
            // Mark disconnected so remaining peers can detect disconnection
            viewModelScope.launch {
                repository.sendPresenceHeartbeat(code, isConnected = false)
            }
        }
        super.onCleared()
    }
}
