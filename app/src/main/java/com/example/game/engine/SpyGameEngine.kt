package com.example.game.engine

import com.example.game.data.ArabicWordBank
import com.example.game.data.SecretWordSelection
import com.example.game.data.WordCategory
import kotlin.random.Random

/**
 * Shared authoritative game rules engine used identically by both Offline Mode and Online Mode.
 * Enforces literal compliance with every rule in the specification.
 */
object SpyGameEngine {

    /** Exact literal Arabic phrase shown to spies on their secret card. */
    const val SPY_SECRET_MESSAGE = "أنت الجاسوس إعمل نفسك تعرف الكلمة"

    /** Minimum questions required before "Start Voting" unlocks. */
    const val MIN_QUESTIONS_BEFORE_VOTE = 7

    /** Maximum reasonable custom target score. */
    const val MAX_CUSTOM_WIN_SCORE = 1000
    const val MIN_CUSTOM_WIN_SCORE = 10

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessageAr: String? = null
    )

    data class QuestionPair(
        val askerId: String,
        val answererId: String
    )

    data class PlayerRoundScore(
        val playerId: String,
        val votePointsEarned: Int,
        val spyGuessPointsEarned: Int,
        val roundTotalEarned: Int,
        val correctSpiesFound: Int,
        val spyGuessedCorrectly: Boolean
    )

    data class RoundCalculationResult(
        val playerScores: Map<String, PlayerRoundScore>,
        val updatedTotalScores: Map<String, Int>,
        val winnerIds: List<String>,
        val isMatchOver: Boolean
    )

    /**
     * Validates player count, spy count, player names, and win score before starting a match.
     * Rules:
     * - Spy count must be 1, 2, or 3.
     * - Must have at least 3 players total, and strictly more regular players than spies (or at least spyCount + 2 players so social deduction works fairly: 1 spy -> min 3 players, 2 spies -> min 4 players, 3 spies -> min 5 players).
     * - Win score must be positive and within [10, 1000].
     */
    fun validateMatchSettings(
        playerCount: Int,
        spyCount: Int,
        winScore: Int,
        playerNames: List<String> = emptyList()
    ): ValidationResult {
        if (spyCount !in 1..3) {
            return ValidationResult(false, "عدد الجواسيس يجب أن يكون ١ أو ٢ أو ٣.")
        }
        val minRequiredPlayers = spyCount + 2
        if (playerCount < minRequiredPlayers) {
            return ValidationResult(
                false,
                "عدد اللاعبين غير كافٍ! يتطلب وجود $spyCount جاسوس على الأقل $minRequiredPlayers لاعبين لبدء المباراة."
            )
        }
        if (winScore < MIN_CUSTOM_WIN_SCORE || winScore > MAX_CUSTOM_WIN_SCORE) {
            return ValidationResult(
                false,
                "حد نقاط الفوز يجب أن يكون رقماً موجباً بين $MIN_CUSTOM_WIN_SCORE و $MAX_CUSTOM_WIN_SCORE نقطة."
            )
        }
        if (playerNames.isNotEmpty()) {
            if (playerNames.any { it.trim().isEmpty() }) {
                return ValidationResult(false, "يرجى إدخال أسماء جميع اللاعبين دون ترك حقول فارغة.")
            }
            val normalized = playerNames.map { it.trim() }
            if (normalized.distinct().size != normalized.size) {
                return ValidationResult(false, "يرجى عدم تكرار اسم اللاعب نفسه أكثر من مرة.")
            }
        }
        return ValidationResult(true)
    }

    /**
     * Assigns spies randomly and selects a secret word + 8 spy guess options from the category.
     */
    fun distributeRolesAndWord(
        playerIds: List<String>,
        spyCount: Int,
        category: WordCategory,
        usedWords: Set<String> = emptySet(),
        random: Random = Random.Default
    ): Pair<Set<String>, SecretWordSelection> {
        require(playerIds.size >= spyCount + 2) { "Insufficient players for spyCount=$spyCount" }
        val spyIds = playerIds.shuffled(random).take(spyCount).toSet()
        val wordSelection = ArabicWordBank.selectSecretWordAndOptions(
            requestedCategory = category,
            usedWords = usedWords,
            random = random
        )
        return spyIds to wordSelection
    }

    /**
     * Centrally selects random (Asker, Answerer) pair ensuring Asker != Answerer and avoiding
     * immediate exact repetition when possible.
     */
    fun pickNextQuestionPair(
        eligiblePlayerIds: List<String>,
        previousPair: QuestionPair? = null,
        random: Random = Random.Default
    ): QuestionPair {
        require(eligiblePlayerIds.size >= 2) { "At least 2 players required for questions" }

        val allPairs = mutableListOf<QuestionPair>()
        for (asker in eligiblePlayerIds) {
            for (answerer in eligiblePlayerIds) {
                if (asker != answerer) {
                    allPairs.add(QuestionPair(asker, answerer))
                }
            }
        }

        val filtered = if (previousPair != null && allPairs.size > 1) {
            allPairs.filter { it != previousPair }
        } else {
            allPairs
        }
        return filtered[random.nextInt(filtered.size)]
    }

    /**
     * Voting unlocks ONLY after completing at least 7 questions (questionCount >= 7).
     */
    fun canStartVoting(completedQuestionsCount: Int): Boolean {
        return completedQuestionsCount >= MIN_QUESTIONS_BEFORE_VOTE
    }

    /**
     * Validates a single voter's ballot:
     * - Must select exact `spyCount` distinct suspects.
     * - Cannot vote for oneself (unless allowed, wait: let's check prompt:
     *   "تظهر واجهة تسمح لكل لاعب باختيار الأشخاص الذين يعتقد أنهم الجواسيس. إذا كان هناك جاسوس واحد: يختار كل لاعب شخصًا واحدًا...").
     *   Preventing self-selection for regular players is standard, and all selected IDs must be valid room players and distinct.
     */
    fun validateVoteSelection(
        voterId: String,
        selectedSuspectIds: List<String>,
        allPlayerIds: Set<String>,
        spyCount: Int
    ): ValidationResult {
        val distinctSelection = selectedSuspectIds.distinct()
        if (distinctSelection.size != spyCount) {
            return ValidationResult(
                false,
                "يجب اختيار $spyCount ${if (spyCount == 1) "شخص" else "أشخاص"} بالضبط للتصويت."
            )
        }
        if (voterId in distinctSelection) {
            return ValidationResult(false, "لا يمكنك التصويت على نفسك.")
        }
        if (!allPlayerIds.containsAll(distinctSelection)) {
            return ValidationResult(false, "اختيار غير صالح ضمن قائمة اللاعبين.")
        }
        return ValidationResult(true)
    }

    /**
     * Calculates round points and match winners strictly per Section 16 & Section 17:
     *
     * 1. Normal Players (non-spies):
     *    +10 points for EACH real spy they correctly selected in their vote ballot.
     *    - 1 spy: 10 or 0
     *    - 2 spies: 20, 10, or 0
     *    - 3 spies: 30, 20, 10, or 0
     *    (Spies do NOT earn normal-player voting points for "discovering" fellow spies).
     *
     * 2. Spies (evaluated independently):
     *    If spy guesses `secretWord` == `guessedWord`:
     *    - 1 spy in round: +10 points
     *    - 2 spies in round: +20 points for each spy who guesses right
     *    - 3 spies in round: +30 points for each spy who guesses right
     *    Wrong guess: 0 points.
     *
     * 3. Independence:
     *    Spy guessing the word does NOT cancel normal players' discovery points, and normal players
     *    discovering spies does NOT cancel the spy's word-guess points.
     *
     * 4. Win Threshold (Section 17):
     *    Any player whose `updatedTotalScore >= winScore` wins.
     *    If 2 or more reach/exceed `winScore` in the same round, ALL of them win together (no tie-breaker).
     */
    fun calculateRoundAndWinners(
        playerIds: List<String>,
        spyIds: Set<String>,
        secretWord: String,
        votesByVoter: Map<String, List<String>>,
        guessesBySpy: Map<String, String>,
        previousTotalScores: Map<String, Int>,
        winScore: Int
    ): RoundCalculationResult {
        val spyCount = spyIds.size.coerceIn(1, 3)
        val spyCorrectReward = spyCount * 10

        val roundScores = mutableMapOf<String, PlayerRoundScore>()
        val updatedTotals = mutableMapOf<String, Int>()

        for (playerId in playerIds) {
            val isSpy = playerId in spyIds
            val votePoints: Int
            val spyPoints: Int
            val correctFoundCount: Int
            val guessedRight: Boolean

            if (!isSpy) {
                val chosen = votesByVoter[playerId].orEmpty().distinct()
                correctFoundCount = chosen.count { it in spyIds }
                votePoints = correctFoundCount * 10
                spyPoints = 0
                guessedRight = false
            } else {
                correctFoundCount = 0
                votePoints = 0
                val spyGuess = guessesBySpy[playerId]?.trim()
                guessedRight = (spyGuess != null && spyGuess == secretWord)
                spyPoints = if (guessedRight) spyCorrectReward else 0
            }

            val earned = votePoints + spyPoints
            val prevTotal = previousTotalScores[playerId] ?: 0
            val newTotal = prevTotal + earned

            roundScores[playerId] = PlayerRoundScore(
                playerId = playerId,
                votePointsEarned = votePoints,
                spyGuessPointsEarned = spyPoints,
                roundTotalEarned = earned,
                correctSpiesFound = correctFoundCount,
                spyGuessedCorrectly = guessedRight
            )
            updatedTotals[playerId] = newTotal
        }

        val winners = playerIds.filter { (updatedTotals[it] ?: 0) >= winScore }

        return RoundCalculationResult(
            playerScores = roundScores,
            updatedTotalScores = updatedTotals,
            winnerIds = winners,
            isMatchOver = winners.isNotEmpty()
        )
    }

    /**
     * Deterministic Host Migration selector:
     * Chooses the connected remaining player with the lowest `joinedOrder` (or lexicographical UID tiebreaker),
     * guaranteeing every device agrees on the exact single new Host without split-brain.
     */
    fun selectNextHostId(
        remainingMemberUids: List<String>,
        connectedStatusByUid: Map<String, Boolean>,
        joinedOrderByUid: Map<String, Int>
    ): String? {
        if (remainingMemberUids.isEmpty()) return null
        val connectedCandidates = remainingMemberUids.filter { connectedStatusByUid[it] != false }
        val pool = if (connectedCandidates.isNotEmpty()) connectedCandidates else remainingMemberUids
        return pool.minWithOrNull(
            compareBy<String> { joinedOrderByUid[it] ?: Int.MAX_VALUE }
                .thenBy { it }
        )
    }
}
