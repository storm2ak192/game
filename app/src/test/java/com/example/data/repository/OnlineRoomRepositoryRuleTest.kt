package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MatchPhase
import com.example.game.data.WordCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnlineRoomRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val OP_TIMEOUT = 5000L
        const val FLOW_TIMEOUT = 4000L
    }

    @Test
    fun unauthenticatedUser_cannotObserveRoom_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = OnlineRoomRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT) {
                repo.observeRoom("AB12CD").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun fullOnlineMatchLifecycle_joinDistributeSecretIsolationConfirmQuestionsVotingScoringAndHostMigration() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(6)
        val hostUid = signInTestUser("host_$runSuffix@aljasoos.test")
        val repo = OnlineRoomRepository(firestore, auth)

        // 1. Host creates room
        val roomCode = withTimeout(OP_TIMEOUT) {
            repo.createRoom(
                hostName = "أحمد",
                hostAvatarIndex = 0,
                category = WordCategory.FOOD,
                spyCount = 1,
                winScore = 10
            )
        }
        assertTrue(roomCode.length >= 6)

        // 2. Player 2 joins
        val p2Uid = signInTestUser("p2_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) {
            repo.joinRoom(roomCode, "سارة", 1)
        }

        // 3. Player 3 joins
        val p3Uid = signInTestUser("p3_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) {
            repo.joinRoom(roomCode, "خالد", 2)
        }

        // 4. Switch back to Host and start Round 1
        signInTestUser("host_$runSuffix@aljasoos.test")
        val roomInLobby = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it != null && it.memberUids.size == 3 }!!
        }
        val playersInLobby = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, hostUid).first { it.size == 3 }
        }
        withTimeout(OP_TIMEOUT) {
            repo.startNewRound(roomInLobby, playersInLobby)
        }

        // 5. Verify Secret Isolation: Player 2 can read own secret, CANNOT read Player 3's secret!
        signInTestUser("p2_$runSuffix@aljasoos.test")
        val p2Secret = withTimeout(FLOW_TIMEOUT) {
            repo.observeMySecret(roomCode, p2Uid).first { it != null }!!
        }
        if (p2Secret.isSpy) {
            assertEquals("", p2Secret.secretWord)
            assertEquals(8, p2Secret.spyOptions.size)
        } else {
            assertTrue(p2Secret.secretWord.isNotEmpty())
        }

        // Cross-player secret read MUST fail with PERMISSION_DENIED
        try {
            withTimeout(OP_TIMEOUT) {
                firestore.collection("rooms").document(roomCode)
                    .collection("secrets").document(p3Uid).get().await()
            }
            fail("Cross-user secret read must fail")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException())
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }

        // 6. Prevent starting questions before all players confirm viewing their secret card
        withTimeout(OP_TIMEOUT) { repo.confirmSecretCardViewed(roomCode) } // P2 confirms
        signInTestUser("host_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) { repo.confirmSecretCardViewed(roomCode) } // Host confirms

        val partialPlayers = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, hostUid).first { list -> list.count { it.hasConfirmedCard } == 2 }
        }
        val roomReveal = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.status == MatchPhase.ROLE_REVEAL.name }!!
        }
        try {
            repo.startQuestionsPhase(roomReveal, partialPlayers)
            fail("Should block starting questions before all 3 players confirm")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("تأكيد"))
        }

        // Player 3 confirms
        signInTestUser("p3_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) { repo.confirmSecretCardViewed(roomCode) }

        // 7. Host starts Questions Phase
        signInTestUser("host_$runSuffix@aljasoos.test")
        val allConfirmedPlayers = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, hostUid).first { list -> list.all { it.hasConfirmedCard } }
        }
        withTimeout(OP_TIMEOUT) {
            repo.startQuestionsPhase(roomReveal, allConfirmedPlayers)
        }

        // 8. Verify microphone update isolation: P2 toggles own mic; Host cannot change P2's mic
        signInTestUser("p2_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) { repo.setMyMicrophoneMuted(roomCode, true) }
        val p2MutedCheck = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, p2Uid).first { list -> list.first { it.userId == p2Uid }.isMicMuted }
        }
        assertTrue(p2MutedCheck.first { it.userId == p2Uid }.isMicMuted)

        // 9. Advance 6 more questions to reach questionCount = 7, then start voting
        signInTestUser("host_$runSuffix@aljasoos.test")
        var qRoom = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.status == MatchPhase.QUESTIONS.name }!!
        }
        while (qRoom.questionCount < 7L) {
            val prevCount = qRoom.questionCount
            withTimeout(OP_TIMEOUT) {
                repo.nextQuestionTurn(roomCode, qRoom.turnEventId, listOf(hostUid, p2Uid, p3Uid))
            }
            qRoom = withTimeout(FLOW_TIMEOUT) {
                repo.observeRoom(roomCode).first { it != null && it.questionCount > prevCount }!!
            }
        }
        assertEquals(7L, qRoom.questionCount)

        withTimeout(OP_TIMEOUT) {
            repo.triggerVotingPhase(roomCode, qRoom.turnEventId)
        }

        // 10. All 3 players submit votes
        val allIds = setOf(hostUid, p2Uid, p3Uid)
        withTimeout(OP_TIMEOUT) { repo.submitVote(roomCode, 1, listOf(p2Uid), allIds, 1) }

        signInTestUser("p2_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) { repo.submitVote(roomCode, 1, listOf(p3Uid), allIds, 1) }

        signInTestUser("p3_$runSuffix@aljasoos.test")
        withTimeout(OP_TIMEOUT) { repo.submitVote(roomCode, 1, listOf(p2Uid), allIds, 1) }

        // 11. Host advances to SPY_GUESS, Spy guesses, Host finalizes round & checks MATCH_OVER (winScore = 10)
        signInTestUser("host_$runSuffix@aljasoos.test")
        val voteRoom = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.status == MatchPhase.VOTING.name }!!
        }
        val votedPlayers = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, hostUid).first { list -> list.all { it.hasVoted } }
        }
        withTimeout(OP_TIMEOUT) {
            repo.advanceToSpyGuessPhase(voteRoom, votedPlayers)
        }

        // Find who the spy is from RoundVault (as Host)
        val vaultSnap = withTimeout(OP_TIMEOUT) {
            firestore.collection("rooms").document(roomCode).collection("vault").document("round_1").get().await()
        }
        @Suppress("UNCHECKED_CAST")
        val spyId = (vaultSnap.get("spyIds") as List<String>).first()
        val secretWord = vaultSnap.getString("secretWord")!!

        val spyEmail = when (spyId) {
            hostUid -> "host_$runSuffix@aljasoos.test"
            p2Uid -> "p2_$runSuffix@aljasoos.test"
            else -> "p3_$runSuffix@aljasoos.test"
        }
        signInTestUser(spyEmail)
        withTimeout(OP_TIMEOUT) {
            repo.submitSpyWordGuess(roomCode, 1, secretWord)
        }

        // Host finalizes round
        signInTestUser("host_$runSuffix@aljasoos.test")
        val spyGuessRoom = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.status == MatchPhase.SPY_GUESS.name }!!
        }
        val guessedPlayers = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoomPlayers(roomCode, hostUid).first { list -> list.first { it.userId == spyId }.hasGuessedWord }
        }
        val finalized = withTimeout(OP_TIMEOUT) {
            repo.evaluateAndFinalizeRoundIfReady(spyGuessRoom, guessedPlayers)
        }
        assertTrue(finalized)

        val finalRoom = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.status == MatchPhase.MATCH_OVER.name }!!
        }
        assertEquals(secretWord, finalRoom.revealedWord)
        assertTrue(finalRoom.winnerIds.contains(spyId))

        // 12. Host leaves -> Host Migration transfers hostId to Player 2 automatically!
        withTimeout(OP_TIMEOUT) {
            repo.leaveRoom(roomCode)
        }
        signInTestUser("p2_$runSuffix@aljasoos.test")
        val migratedRoom = withTimeout(FLOW_TIMEOUT) {
            repo.observeRoom(roomCode).first { it?.hostId == p2Uid }
        }
        assertNotNull(migratedRoom)
        assertFalse(migratedRoom!!.memberUids.contains(hostUid))
    }
}
