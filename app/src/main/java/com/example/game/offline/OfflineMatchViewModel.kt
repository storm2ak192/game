package com.example.game.offline

import androidx.lifecycle.ViewModel
import com.example.data.model.MatchPhase
import com.example.game.data.SecretWordSelection
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OfflinePlayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarIndex: Int,
    val customPhotoUri: String? = null,
    val totalScore: Int = 0,
    val lastRoundScore: Int = 0
)

data class OfflineUiState(
    val phase: MatchPhase = MatchPhase.LOBBY,
    val players: List<OfflinePlayer> = listOf(
        OfflinePlayer(name = "أحمد", avatarIndex = 0),
        OfflinePlayer(name = "سارة", avatarIndex = 1),
        OfflinePlayer(name = "خالد", avatarIndex = 2),
        OfflinePlayer(name = "نورة", avatarIndex = 3)
    ),
    val category: WordCategory = WordCategory.RANDOM,
    val spyCount: Int = 1,
    val winScore: Int = 100,
    val customWinScoreInput: String = "",
    val useCustomWinScore: Boolean = false,
    val errorMessage: String? = null,

    // Active Round State
    val roundNumber: Int = 0,
    val usedWords: Set<String> = emptySet(),
    val currentWordSelection: SecretWordSelection? = null,
    val spyIds: Set<String> = emptySet(),

    // Pass-and-Play Scratch Card Distribution State
    val currentCardPlayerIndex: Int = 0,
    val isCurrentCardRevealed: Boolean = false,
    val allCardsDistributedBannerVisible: Boolean = false,

    // Questions State
    val questionCount: Int = 0,
    val currentAskerId: String = "",
    val currentAnswererId: String = "",

    // Pass-and-Play Voting State
    val currentVoterIndex: Int = 0,
    val currentVoterSelections: Set<String> = emptySet(),
    val votesByVoter: Map<String, List<String>> = emptyMap(),

    // Pass-and-Play Independent Spy Word Guess State
    val spyOrderForGuessing: List<String> = emptyList(),
    val currentSpyGuessIndex: Int = 0,
    val guessesBySpy: Map<String, String> = emptyMap(),

    // Round & Final Results
    val lastCalculation: SpyGameEngine.RoundCalculationResult? = null,
    val winnerIds: List<String> = emptyList()
)

class OfflineMatchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState: StateFlow<OfflineUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun addPlayer(name: String, avatarIndex: Int, customPhotoUri: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال اسم اللاعب قبل الإضافة.") }
            return
        }
        val state = _uiState.value
        if (state.players.size >= 12) {
            _uiState.update { it.copy(errorMessage = "الحد الأقصى هو ١٢ لاعباً في المباراة الواحدة.") }
            return
        }
        if (state.players.any { it.name.equals(trimmed, ignoreCase = true) }) {
            _uiState.update { it.copy(errorMessage = "اسم اللاعب «$trimmed» موجود بالفعل في القائمة.") }
            return
        }
        _uiState.update {
            it.copy(
                players = it.players + OfflinePlayer(
                    name = trimmed,
                    avatarIndex = avatarIndex,
                    customPhotoUri = customPhotoUri
                ),
                errorMessage = null
            )
        }
    }

    fun removePlayer(playerId: String) {
        _uiState.update {
            it.copy(
                players = it.players.filterNot { p -> p.id == playerId },
                errorMessage = null
            )
        }
    }

    fun updatePlayerAvatar(playerId: String, avatarIndex: Int, customPhotoUri: String? = null) {
        _uiState.update { state ->
            state.copy(
                players = state.players.map { p ->
                    if (p.id == playerId) {
                        p.copy(avatarIndex = avatarIndex, customPhotoUri = customPhotoUri)
                    } else p
                }
            )
        }
    }

    fun selectCategory(category: WordCategory) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
    }

    fun selectSpyCount(count: Int) {
        _uiState.update { it.copy(spyCount = count.coerceIn(1, 3), errorMessage = null) }
    }

    fun selectPresetWinScore(score: Int) {
        _uiState.update {
            it.copy(
                winScore = score,
                useCustomWinScore = false,
                errorMessage = null
            )
        }
    }

    fun updateCustomWinScore(input: String) {
        val digitsOnly = input.filter { it.isDigit() }.take(4)
        val parsed = digitsOnly.toIntOrNull()
        _uiState.update {
            it.copy(
                customWinScoreInput = digitsOnly,
                useCustomWinScore = true,
                winScore = parsed ?: 0,
                errorMessage = null
            )
        }
    }

    /**
     * Validates all offline settings and begins Round 1 (or next round) with scratch-card distribution.
     */
    fun startMatchOrNextRound(isNewMatch: Boolean = false) {
        val current = _uiState.value
        if (!isNewMatch && current.phase == MatchPhase.MATCH_OVER) {
            return // Never auto-start a new round once match is over
        }

        val resetPlayers = if (isNewMatch) {
            current.players.map { it.copy(totalScore = 0, lastRoundScore = 0) }
        } else {
            current.players
        }

        val validation = SpyGameEngine.validateMatchSettings(
            playerCount = resetPlayers.size,
            spyCount = current.spyCount,
            winScore = current.winScore,
            playerNames = resetPlayers.map { it.name }
        )

        if (!validation.isValid) {
            _uiState.update { it.copy(errorMessage = validation.errorMessageAr) }
            return
        }

        val (spies, wordSelection) = SpyGameEngine.distributeRolesAndWord(
            playerIds = resetPlayers.map { it.id },
            spyCount = current.spyCount,
            category = current.category,
            usedWords = if (isNewMatch) emptySet() else current.usedWords
        )

        _uiState.update {
            it.copy(
                phase = MatchPhase.ROLE_REVEAL,
                players = resetPlayers,
                roundNumber = if (isNewMatch) 1 else it.roundNumber + 1,
                usedWords = (if (isNewMatch) emptySet() else it.usedWords) + wordSelection.secretWord,
                currentWordSelection = wordSelection,
                spyIds = spies,
                currentCardPlayerIndex = 0,
                isCurrentCardRevealed = false,
                allCardsDistributedBannerVisible = false,
                questionCount = 0,
                currentVoterIndex = 0,
                currentVoterSelections = emptySet(),
                votesByVoter = emptyMap(),
                spyOrderForGuessing = resetPlayers.filter { p -> p.id in spies }.map { p -> p.id },
                currentSpyGuessIndex = 0,
                guessesBySpy = emptyMap(),
                lastCalculation = null,
                winnerIds = if (isNewMatch) emptyList() else it.winnerIds,
                errorMessage = null
            )
        }
    }

    fun markCurrentScratchCardRevealed() {
        _uiState.update { it.copy(isCurrentCardRevealed = true) }
    }

    /**
     * Called when current player taps "متابعة" after viewing their secret scratch card.
     * Immediately hides previous player's content, resets card cover, and advances to next player.
     */
    fun advanceToNextPlayerCard() {
        val state = _uiState.value
        if (!state.isCurrentCardRevealed) return

        val nextIdx = state.currentCardPlayerIndex + 1
        if (nextIdx < state.players.size) {
            _uiState.update {
                it.copy(
                    currentCardPlayerIndex = nextIdx,
                    isCurrentCardRevealed = false
                )
            }
        } else {
            // All players have viewed their cards -> show completion screen before Questions phase
            _uiState.update {
                it.copy(
                    isCurrentCardRevealed = false,
                    allCardsDistributedBannerVisible = true
                )
            }
        }
    }

    fun beginQuestionsPhase() {
        val state = _uiState.value
        val pair = SpyGameEngine.pickNextQuestionPair(state.players.map { it.id })
        _uiState.update {
            it.copy(
                phase = MatchPhase.QUESTIONS,
                allCardsDistributedBannerVisible = false,
                questionCount = 1,
                currentAskerId = pair.askerId,
                currentAnswererId = pair.answererId
            )
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.phase != MatchPhase.QUESTIONS) return
        val nextPair = SpyGameEngine.pickNextQuestionPair(
            eligiblePlayerIds = state.players.map { it.id },
            previousPair = SpyGameEngine.QuestionPair(state.currentAskerId, state.currentAnswererId)
        )
        _uiState.update {
            it.copy(
                questionCount = it.questionCount + 1,
                currentAskerId = nextPair.askerId,
                currentAnswererId = nextPair.answererId
            )
        }
    }

    fun startVotingPhase() {
        val state = _uiState.value
        if (!SpyGameEngine.canStartVoting(state.questionCount)) return
        _uiState.update {
            it.copy(
                phase = MatchPhase.VOTING,
                currentVoterIndex = 0,
                currentVoterSelections = emptySet(),
                votesByVoter = emptyMap(),
                errorMessage = null
            )
        }
    }

    fun toggleSuspectSelection(suspectId: String) {
        val state = _uiState.value
        val currentVoter = state.players.getOrNull(state.currentVoterIndex) ?: return
        if (suspectId == currentVoter.id) return

        val currentSet = state.currentVoterSelections.toMutableSet()
        if (suspectId in currentSet) {
            currentSet.remove(suspectId)
        } else {
            if (state.spyCount == 1) {
                currentSet.clear()
                currentSet.add(suspectId)
            } else if (currentSet.size < state.spyCount) {
                currentSet.add(suspectId)
            }
        }
        _uiState.update { it.copy(currentVoterSelections = currentSet, errorMessage = null) }
    }

    fun confirmCurrentPlayerVote() {
        val state = _uiState.value
        val voter = state.players.getOrNull(state.currentVoterIndex) ?: return
        val validation = SpyGameEngine.validateVoteSelection(
            voterId = voter.id,
            selectedSuspectIds = state.currentVoterSelections.toList(),
            allPlayerIds = state.players.map { it.id }.toSet(),
            spyCount = state.spyCount
        )
        if (!validation.isValid) {
            _uiState.update { it.copy(errorMessage = validation.errorMessageAr) }
            return
        }

        val updatedVotes = state.votesByVoter + (voter.id to state.currentVoterSelections.toList())
        val nextVoterIdx = state.currentVoterIndex + 1

        if (nextVoterIdx < state.players.size) {
            _uiState.update {
                it.copy(
                    votesByVoter = updatedVotes,
                    currentVoterIndex = nextVoterIdx,
                    currentVoterSelections = emptySet(),
                    errorMessage = null
                )
            }
        } else {
            // All players voted -> move to Independent Spy Word Guess phase
            _uiState.update {
                it.copy(
                    phase = MatchPhase.SPY_GUESS,
                    votesByVoter = updatedVotes,
                    currentVoterSelections = emptySet(),
                    currentSpyGuessIndex = 0,
                    errorMessage = null
                )
            }
        }
    }

    fun submitCurrentSpyGuess(guessedWord: String) {
        val state = _uiState.value
        val spyId = state.spyOrderForGuessing.getOrNull(state.currentSpyGuessIndex) ?: return
        val updatedGuesses = state.guessesBySpy + (spyId to guessedWord)
        val nextSpyIdx = state.currentSpyGuessIndex + 1

        if (nextSpyIdx < state.spyOrderForGuessing.size) {
            _uiState.update {
                it.copy(
                    guessesBySpy = updatedGuesses,
                    currentSpyGuessIndex = nextSpyIdx
                )
            }
        } else {
            // All spies have independently guessed -> finalize round scores & check match winners
            finalizeOfflineRound(updatedGuesses)
        }
    }

    private fun finalizeOfflineRound(finalGuesses: Map<String, String>) {
        val state = _uiState.value
        val secretWord = state.currentWordSelection?.secretWord ?: ""
        val prevTotals = state.players.associate { it.id to it.totalScore }

        val calc = SpyGameEngine.calculateRoundAndWinners(
            playerIds = state.players.map { it.id },
            spyIds = state.spyIds,
            secretWord = secretWord,
            votesByVoter = state.votesByVoter,
            guessesBySpy = finalGuesses,
            previousTotalScores = prevTotals,
            winScore = state.winScore
        )

        val updatedPlayers = state.players.map { p ->
            p.copy(
                lastRoundScore = calc.playerScores[p.id]?.roundTotalEarned ?: 0,
                totalScore = calc.updatedTotalScores[p.id] ?: p.totalScore
            )
        }

        _uiState.update {
            it.copy(
                phase = if (calc.isMatchOver) MatchPhase.MATCH_OVER else MatchPhase.ROUND_RESULTS,
                players = updatedPlayers,
                guessesBySpy = finalGuesses,
                lastCalculation = calc,
                winnerIds = calc.winnerIds
            )
        }
    }

    fun resetToSetupLobby() {
        _uiState.update {
            it.copy(
                phase = MatchPhase.LOBBY,
                roundNumber = 0,
                usedWords = emptySet(),
                spyIds = emptySet(),
                winnerIds = emptyList(),
                lastCalculation = null,
                players = it.players.map { p -> p.copy(totalScore = 0, lastRoundScore = 0) },
                errorMessage = null
            )
        }
    }
}
