package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class MatchPhase {
    LOBBY,
    ROLE_REVEAL,
    QUESTIONS,
    VOTING,
    SPY_GUESS,
    ROUND_RESULTS,
    MATCH_OVER;

    companion object {
        fun fromString(value: String): MatchPhase {
            return entries.firstOrNull { it.name == value } ?: LOBBY
        }
    }
}

@IgnoreExtraProperties
data class OnlineRoom(
    @DocumentId val id: String = "",
    val code: String = "",
    val hostId: String = "",
    val creatorId: String = "",
    val status: String = MatchPhase.LOBBY.name,
    val category: String = "RANDOM",
    val spyCount: Long = 1L,
    val winScore: Long = 100L,
    val roundNumber: Long = 0L,
    val questionCount: Long = 0L,
    val currentAskerId: String = "",
    val currentAnswererId: String = "",
    val turnEventId: String = "",
    val memberUids: List<String> = emptyList(),
    val maxPlayers: Long = 10L,
    @get:PropertyName("isClosed") @field:PropertyName("isClosed") val isClosed: Boolean = false,
    val revealedWord: String = "",
    val revealedSpyIds: List<String> = emptyList(),
    val winnerIds: List<String> = emptyList(),
    val roundVotesSummary: Map<String, List<String>> = emptyMap(),
    val roundGuessesSummary: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OnlineRoomPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomCode: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val avatarIndex: Long = 0L,
    val totalScore: Long = 0L,
    val lastRoundScore: Long = 0L,
    val hasConfirmedCard: Boolean = false,
    val hasVoted: Boolean = false,
    val hasGuessedWord: Boolean = false,
    @get:PropertyName("isMicMuted") @field:PropertyName("isMicMuted") val isMicMuted: Boolean = false,
    @get:PropertyName("isConnected") @field:PropertyName("isConnected") val isConnected: Boolean = true,
    val joinedOrder: Long = 0L,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlayerSecretDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val hostId: String = "",
    val roundNumber: Long = 1L,
    @get:PropertyName("isSpy") @field:PropertyName("isSpy") val isSpy: Boolean = false,
    val secretWord: String = "",
    val spyOptions: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoundVaultDoc(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val roundNumber: Long = 1L,
    val secretWord: String = "",
    val spyIds: List<String> = emptyList(),
    val spyOptions: List<String> = emptyList(),
    val scored: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VoteBallotDoc(
    @DocumentId val id: String = "",
    val voterId: String = "",
    val roundNumber: Long = 1L,
    val selectedSuspectIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SpyWordGuessDoc(
    @DocumentId val id: String = "",
    val spyId: String = "",
    val roundNumber: Long = 1L,
    val guessedWord: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VoiceSignalDoc(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val targetId: String = "",
    val roomCode: String = "",
    val type: String = "PING",
    val payload: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserMatchStatDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "OFFLINE",
    val won: Boolean = false,
    val score: Long = 0L,
    val playerCount: Long = 3L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
