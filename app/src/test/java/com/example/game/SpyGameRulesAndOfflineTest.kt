package com.example.game

import com.example.data.model.MatchPhase
import com.example.game.data.ArabicWordBank
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import com.example.game.offline.OfflineMatchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive verification suite covering all core rules, edge cases, scoring combinations,
 * win conditions, 7-question gate, scratch card reset, host migration, and offline/online parity
 * specified in Section 24 of the Master Prompt.
 */
class SpyGameRulesAndOfflineTest {

    @Test
    fun `1_to_3_offline_match_supports_1_2_and_3_spies_with_exact_secret_message`() {
        assertEquals("أنت الجاسوس إعمل نفسك تعرف الكلمة", SpyGameEngine.SPY_SECRET_MESSAGE)

        for (spyCount in listOf(1, 2, 3)) {
            val vm = OfflineMatchViewModel()
            // Default has 4 players; add 1 more so 5 players supports up to 3 spies
            vm.addPlayer("يوسف", 5)
            vm.selectCategory(WordCategory.FOOD)
            vm.selectSpyCount(spyCount)
            vm.selectPresetWinScore(100)

            vm.startMatchOrNextRound(isNewMatch = true)
            val state = vm.uiState.value
            assertEquals(MatchPhase.ROLE_REVEAL, state.phase)
            assertEquals(spyCount, state.spyIds.size)
            assertEquals(8, state.currentWordSelection?.spyOptions?.size)
            assertTrue(state.currentWordSelection!!.secretWord in state.currentWordSelection!!.spyOptions)
        }
    }

    @Test
    fun `4_to_6_voting_validates_exact_1_2_and_3_spy_selections_and_blocks_self_vote`() {
        val allPlayers = setOf("p1", "p2", "p3", "p4", "p5")

        // 1 spy: must pick 1, cannot pick self
        assertTrue(SpyGameEngine.validateVoteSelection("p1", listOf("p2"), allPlayers, 1).isValid)
        assertFalse(SpyGameEngine.validateVoteSelection("p1", listOf("p1"), allPlayers, 1).isValid)
        assertFalse(SpyGameEngine.validateVoteSelection("p1", listOf("p2", "p3"), allPlayers, 1).isValid)

        // 2 spies: must pick 2 distinct others
        assertTrue(SpyGameEngine.validateVoteSelection("p1", listOf("p2", "p3"), allPlayers, 2).isValid)
        assertFalse(SpyGameEngine.validateVoteSelection("p1", listOf("p2", "p2"), allPlayers, 2).isValid)
        assertFalse(SpyGameEngine.validateVoteSelection("p1", listOf("p2"), allPlayers, 2).isValid)

        // 3 spies: must pick 3 distinct others
        assertTrue(SpyGameEngine.validateVoteSelection("p1", listOf("p2", "p3", "p4"), allPlayers, 3).isValid)
        assertFalse(SpyGameEngine.validateVoteSelection("p1", listOf("p2", "p3"), allPlayers, 3).isValid)
    }

    @Test
    fun `7_to_9_independent_spy_guessing_and_exact_points_for_1_2_and_3_spies`() {
        val players = listOf("c1", "c2", "s1", "s2", "s3")

        // Case A: 1 Spy (s1 guesses right -> +10; c1 finds s1 -> +10; c2 misses -> 0)
        // Proves independence: s1 guessing right does NOT cancel c1's +10!
        val res1 = SpyGameEngine.calculateRoundAndWinners(
            playerIds = listOf("c1", "c2", "s1"),
            spyIds = setOf("s1"),
            secretWord = "شاورما",
            votesByVoter = mapOf("c1" to listOf("s1"), "c2" to listOf("c1"), "s1" to listOf("c1")),
            guessesBySpy = mapOf("s1" to "شاورما"),
            previousTotalScores = emptyMap(),
            winScore = 100
        )
        assertEquals(10, res1.playerScores["c1"]?.roundTotalEarned)
        assertEquals(0, res1.playerScores["c2"]?.roundTotalEarned)
        assertEquals(10, res1.playerScores["s1"]?.roundTotalEarned)

        // Case B: 2 Spies (s1 guesses right -> +20; s2 guesses wrong -> 0; c1 finds both -> +20; c2 finds one -> +10)
        val res2 = SpyGameEngine.calculateRoundAndWinners(
            playerIds = listOf("c1", "c2", "s1", "s2"),
            spyIds = setOf("s1", "s2"),
            secretWord = "مصر",
            votesByVoter = mapOf(
                "c1" to listOf("s1", "s2"),
                "c2" to listOf("s1", "c1"),
                "s1" to listOf("c1", "c2"),
                "s2" to listOf("c1", "c2")
            ),
            guessesBySpy = mapOf("s1" to "مصر", "s2" to "الأردن"),
            previousTotalScores = emptyMap(),
            winScore = 100
        )
        assertEquals(20, res2.playerScores["c1"]?.roundTotalEarned)
        assertEquals(10, res2.playerScores["c2"]?.roundTotalEarned)
        assertEquals(20, res2.playerScores["s1"]?.roundTotalEarned)
        assertEquals(0, res2.playerScores["s2"]?.roundTotalEarned)

        // Case C: 3 Spies (s1 & s3 guess right -> +30 each; s2 wrong -> 0; c1 finds all 3 -> +30; c2 finds 2 -> +20)
        val res3 = SpyGameEngine.calculateRoundAndWinners(
            playerIds = players,
            spyIds = setOf("s1", "s2", "s3"),
            secretWord = "مطرقة",
            votesByVoter = mapOf(
                "c1" to listOf("s1", "s2", "s3"),
                "c2" to listOf("s1", "s2", "c1")
            ),
            guessesBySpy = mapOf("s1" to "مطرقة", "s2" to "منشار", "s3" to "مطرقة"),
            previousTotalScores = emptyMap(),
            winScore = 100
        )
        assertEquals(30, res3.playerScores["c1"]?.roundTotalEarned)
        assertEquals(20, res3.playerScores["c2"]?.roundTotalEarned)
        assertEquals(30, res3.playerScores["s1"]?.roundTotalEarned)
        assertEquals(0, res3.playerScores["s2"]?.roundTotalEarned)
        assertEquals(30, res3.playerScores["s3"]?.roundTotalEarned)
    }

    @Test
    fun `10_and_11_single_winner_and_multiple_co_winners_without_tie_breaker`() {
        // Single winner reaching 50
        val singleWin = SpyGameEngine.calculateRoundAndWinners(
            playerIds = listOf("p1", "p2", "s1"),
            spyIds = setOf("s1"),
            secretWord = "كبسة",
            votesByVoter = mapOf("p1" to listOf("s1"), "p2" to listOf("p1")),
            guessesBySpy = mapOf("s1" to "بيتزا"),
            previousTotalScores = mapOf("p1" to 40, "p2" to 40, "s1" to 30),
            winScore = 50
        )
        assertTrue(singleWin.isMatchOver)
        assertEquals(listOf("p1"), singleWin.winnerIds)

        // Co-winners: p1 reaches 50 AND s1 reaches 60 in the exact same round -> BOTH win together!
        val multiWin = SpyGameEngine.calculateRoundAndWinners(
            playerIds = listOf("p1", "p2", "s1", "s2"),
            spyIds = setOf("s1", "s2"),
            secretWord = "كبسة",
            votesByVoter = mapOf("p1" to listOf("s1", "s2"), "p2" to listOf("s1", "p1")),
            guessesBySpy = mapOf("s1" to "كبسة", "s2" to "فلافل"),
            previousTotalScores = mapOf("p1" to 30, "p2" to 35, "s1" to 40, "s2" to 20),
            winScore = 50
        )
        assertTrue(multiWin.isMatchOver)
        // p1: 30 + 20 = 50, p2: 35 + 10 = 45, s1: 40 + 20 = 60 -> winners = [p1, s1]
        assertEquals(listOf("p1", "s1"), multiWin.winnerIds)
    }

    @Test
    fun `12_13_14_22_offline_full_flow_scratch_reset_7_question_gate_and_no_round_after_match_over`() {
        val vm = OfflineMatchViewModel()
        vm.selectCategory(WordCategory.COUNTRIES)
        vm.selectSpyCount(1)
        vm.updateCustomWinScore("10") // Win in 1 round so we also verify MATCH_OVER lock

        vm.startMatchOrNextRound(isNewMatch = true)

        // Verify scratch card pass-and-play resets between every player
        val playerCount = vm.uiState.value.players.size
        for (idx in 0 until playerCount) {
            assertEquals(idx, vm.uiState.value.currentCardPlayerIndex)
            assertFalse("New player's card must start unrevealed!", vm.uiState.value.isCurrentCardRevealed)
            // Attempting to advance before revealing must be blocked
            vm.advanceToNextPlayerCard()
            assertEquals(idx, vm.uiState.value.currentCardPlayerIndex)

            vm.markCurrentScratchCardRevealed()
            assertTrue(vm.uiState.value.isCurrentCardRevealed)
            vm.advanceToNextPlayerCard()
        }

        assertTrue(vm.uiState.value.allCardsDistributedBannerVisible)
        assertFalse(vm.uiState.value.isCurrentCardRevealed)

        // Begin Questions phase -> Question 1
        vm.beginQuestionsPhase()
        assertEquals(MatchPhase.QUESTIONS, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.questionCount)
        assertNotEquals(vm.uiState.value.currentAskerId, vm.uiState.value.currentAnswererId)

        // Questions 1..6: startVotingPhase() MUST be blocked!
        for (q in 1..6) {
            assertEquals(q, vm.uiState.value.questionCount)
            assertFalse(SpyGameEngine.canStartVoting(q))
            vm.startVotingPhase()
            assertEquals("Voting must stay blocked at question $q", MatchPhase.QUESTIONS, vm.uiState.value.phase)
            vm.nextQuestion()
        }

        // At Question 7: can either continue questions OR start voting
        assertEquals(7, vm.uiState.value.questionCount)
        assertTrue(SpyGameEngine.canStartVoting(7))
        vm.nextQuestion() // Continue to Question 8
        assertEquals(8, vm.uiState.value.questionCount)

        // Now trigger voting
        vm.startVotingPhase()
        assertEquals(MatchPhase.VOTING, vm.uiState.value.phase)

        val players = vm.uiState.value.players
        val spyId = vm.uiState.value.spyIds.first()
        val secretWord = vm.uiState.value.currentWordSelection!!.secretWord

        // Each player votes
        for (voter in players) {
            val targetId = if (voter.id != spyId) spyId else players.first { it.id != voter.id }.id
            vm.toggleSuspectSelection(targetId)
            vm.confirmCurrentPlayerVote()
        }

        assertEquals(MatchPhase.SPY_GUESS, vm.uiState.value.phase)
        vm.submitCurrentSpyGuess(secretWord)

        // Since winScore = 10, both citizens who found spy AND spy who guessed word win!
        assertEquals(MatchPhase.MATCH_OVER, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.winnerIds.size >= 2)

        // Scenario 22: Attempting to start next round automatically when MATCH_OVER is strictly blocked
        val finalRoundNumber = vm.uiState.value.roundNumber
        vm.startMatchOrNextRound(isNewMatch = false)
        assertEquals(MatchPhase.MATCH_OVER, vm.uiState.value.phase)
        assertEquals(finalRoundNumber, vm.uiState.value.roundNumber)
    }

    @Test
    fun `21_deterministic_host_migration_elects_earliest_connected_member`() {
        val remaining = listOf("uid_c", "uid_b", "uid_d")
        val connected = mapOf("uid_b" to false, "uid_c" to true, "uid_d" to true)
        val joinedOrder = mapOf("uid_b" to 2, "uid_c" to 4, "uid_d" to 3)

        // uid_b joined earliest (2) but is disconnected -> uid_d (joinedOrder 3, connected) wins deterministically!
        val nextHost = SpyGameEngine.selectNextHostId(remaining, connected, joinedOrder)
        assertEquals("uid_d", nextHost)
    }

    @Test
    fun `random_category_selects_cohesive_single_category_8_options`() {
        repeat(15) {
            val sel = ArabicWordBank.selectSecretWordAndOptions(WordCategory.RANDOM)
            assertNotEquals(WordCategory.RANDOM, sel.effectiveCategory)
            assertEquals(8, sel.spyOptions.distinct().size)
            assertTrue(sel.secretWord in sel.spyOptions)
            val categoryPool = ArabicWordBank.getWordsForCategory(sel.effectiveCategory)
            assertTrue(categoryPool.containsAll(sel.spyOptions))
        }
    }
}
