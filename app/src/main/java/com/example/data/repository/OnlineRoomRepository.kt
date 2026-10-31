package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MatchPhase
import com.example.data.model.OnlineRoom
import com.example.data.model.OnlineRoomPlayer
import com.example.data.model.PlayerSecretDoc
import com.example.data.model.RoundVaultDoc
import com.example.data.model.SpyWordGuessDoc
import com.example.data.model.UserMatchStatDoc
import com.example.data.model.VoiceSignalDoc
import com.example.data.model.VoteBallotDoc
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.game.data.WordCategory
import com.example.game.engine.SpyGameEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.security.SecureRandom
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OnlineRoomRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private val secureRandom = SecureRandom()
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Unambiguous uppercase alphanumeric

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً لاستخدام الغرف الأونلاين.")
    }

    fun generateRoomCode(length: Int = 6): String {
        return buildString {
            repeat(length) {
                append(codeChars[secureRandom.nextInt(codeChars.length)])
            }
        }
    }

    fun buildInviteDeepLink(roomCode: String): String {
        return "aljasoos://join?code=${roomCode.uppercase()}"
    }

    fun buildShareableMessage(roomCode: String): String {
        val cleanCode = roomCode.uppercase()
        return "انضم إليّ في لعبة «الجاسوس»!\nكود الغرفة: $cleanCode\nرابط الانضمام السريع: aljasoos://join?code=$cleanCode"
    }

    /**
     * Creates a real server-backed room and registers the creator as Host (joinedOrder = 1).
     */
    suspend fun createRoom(
        hostName: String,
        hostAvatarIndex: Int,
        category: WordCategory = WordCategory.RANDOM,
        spyCount: Int = 1,
        winScore: Int = 100
    ): String {
        val uid = requireUserId()
        var chosenCode = ""
        for (attempt in 0 until 5) {
            val candidate = generateRoomCode(6)
            val existingSnap = try {
                db.collection("rooms").document(candidate).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "rooms/$candidate")
                throw e
            }
            if (!existingSnap.exists()) {
                chosenCode = candidate
                break
            }
        }
        if (chosenCode.isEmpty()) {
            chosenCode = generateRoomCode(7)
        }

        val roomRef = db.collection("rooms").document(chosenCode)
        val playerRef = roomRef.collection("players").document(uid)

        val roomPayload = mapOf(
            "code" to chosenCode,
            "hostId" to uid,
            "creatorId" to uid,
            "status" to MatchPhase.LOBBY.name,
            "category" to category.name,
            "spyCount" to spyCount,
            "winScore" to winScore,
            "roundNumber" to 0,
            "questionCount" to 0,
            "memberUids" to listOf(uid),
            "maxPlayers" to 10,
            "isClosed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val playerPayload = mapOf(
            "userId" to uid,
            "roomCode" to chosenCode,
            "memberUids" to listOf(uid),
            "name" to hostName.trim().take(40),
            "avatarIndex" to hostAvatarIndex,
            "totalScore" to 0,
            "lastRoundScore" to 0,
            "hasConfirmedCard" to false,
            "hasVoted" to false,
            "hasGuessedWord" to false,
            "isMicMuted" to false,
            "isConnected" to true,
            "joinedOrder" to 1,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val batch = db.batch()
            batch.set(roomRef, roomPayload)
            batch.set(playerRef, playerPayload)
            batch.commit().await()
            return chosenCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, roomRef.path)
            throw e
        }
    }

    /**
     * Joins an existing room with full validation for:
     * - Invalid code format
     * - Room does not exist
     * - Room closed
     * - Room full
     * - Match already started (allows reconnection if user is ALREADY a member!)
     */
    suspend fun joinRoom(
        rawCode: String,
        playerName: String,
        avatarIndex: Int
    ): String {
        val uid = requireUserId()
        val roomCode = rawCode.trim().uppercase()
        if (!roomCode.matches(Regex("^[A-Z0-9]{4,12}$"))) {
            throw IllegalArgumentException("كود الغرفة غير صحيح. تأكد من كتابة الكود بشكل سليم.")
        }
        if (playerName.trim().isEmpty()) {
            throw IllegalArgumentException("يرجى إدخال اسم اللاعب قبل الانضمام.")
        }

        val roomRef = db.collection("rooms").document(roomCode)
        try {
            val roomSnap = roomRef.get().await()
            if (!roomSnap.exists()) {
                throw IllegalStateException("الغرفة غير موجودة. تحقق من كود الغرفة.")
            }
            val room = roomSnap.toObject(OnlineRoom::class.java)
                ?: throw IllegalStateException("تعذر قراءة بيانات الغرفة.")

            // Reconnection path if player was already in memberUids
            if (uid in room.memberUids) {
                val playerRef = roomRef.collection("players").document(uid)
                val existingPlayer = playerRef.get().await()
                if (existingPlayer.exists()) {
                    playerRef.update(
                        mapOf(
                            "name" to playerName.trim().take(40),
                            "avatarIndex" to avatarIndex,
                            "isConnected" to true,
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return roomCode
            }

            if (room.isClosed) {
                throw IllegalStateException("هذه الغرفة مغلقة حالياً ولا تقبل لاعبين جدد.")
            }
            if (room.status != MatchPhase.LOBBY.name) {
                throw IllegalStateException("المباراة بدأت بالفعل ولا تسمح بانضمام لاعبين جدد.")
            }
            if (room.memberUids.size >= room.maxPlayers.toInt()) {
                throw IllegalStateException("الغرفة ممتلئة بالحد الأقصى من اللاعبين.")
            }

            val updatedMembers = (room.memberUids + uid).distinct()
            val joinedOrder = updatedMembers.size

            val batch = db.batch()
            batch.update(
                roomRef,
                mapOf(
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val newPlayerRef = roomRef.collection("players").document(uid)
            batch.set(
                newPlayerRef,
                mapOf(
                    "userId" to uid,
                    "roomCode" to roomCode,
                    "memberUids" to updatedMembers,
                    "name" to playerName.trim().take(40),
                    "avatarIndex" to avatarIndex,
                    "totalScore" to 0,
                    "lastRoundScore" to 0,
                    "hasConfirmedCard" to false,
                    "hasVoted" to false,
                    "hasGuessedWord" to false,
                    "isMicMuted" to false,
                    "isConnected" to true,
                    "joinedOrder" to joinedOrder,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            return roomCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    fun observeRoom(roomCode: String): Flow<OnlineRoom?> {
        val path = "rooms/$roomCode"
        return db.collection("rooms").document(roomCode)
            .snapshots()
            .map { snap ->
                snap.toObject(OnlineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    fun observeRoomPlayers(roomCode: String, currentUserId: String): Flow<List<OnlineRoomPlayer>> {
        val path = "rooms/$roomCode/players"
        return db.collection("rooms").document(roomCode).collection("players")
            .whereEqualTo("roomCode", roomCode)
            .snapshots()
            .map { querySnap ->
                querySnap.toObjects(
                    OnlineRoomPlayer::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.joinedOrder }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    /**
     * Observes ONLY the authenticated player's own isolated secret card document.
     */
    fun observeMySecret(roomCode: String, currentUserId: String): Flow<PlayerSecretDoc?> {
        val path = "rooms/$roomCode/secrets/$currentUserId"
        return db.collection("rooms").document(roomCode)
            .collection("secrets").document(currentUserId)
            .snapshots()
            .map { snap ->
                snap.toObject(PlayerSecretDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    /**
     * Host updates room settings (Category, Spy Count, Win Score) in LOBBY.
     */
    suspend fun updateRoomSettings(
        roomCode: String,
        category: WordCategory,
        spyCount: Int,
        winScore: Int
    ) {
        requireUserId()
        require(spyCount in 1..3) { "عدد الجواسيس يجب أن يكون ١ أو ٢ أو ٣." }
        require(winScore in SpyGameEngine.MIN_CUSTOM_WIN_SCORE..SpyGameEngine.MAX_CUSTOM_WIN_SCORE) {
            "نقاط الفوز يجب أن تكون بين ${SpyGameEngine.MIN_CUSTOM_WIN_SCORE} و ${SpyGameEngine.MAX_CUSTOM_WIN_SCORE}."
        }
        val roomRef = db.collection("rooms").document(roomCode)
        try {
            roomRef.update(
                mapOf(
                    "category" to category.name,
                    "spyCount" to spyCount,
                    "winScore" to winScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Host starts a new round:
     * - Validates settings & connected player count
     * - Distributes isolated secrets to `/rooms/{code}/secrets/{playerId}` (spies receive `secretWord = ""`!)
     * - Writes authoritative `/rooms/{code}/vault/round_{n}`
     * - Resets player round flags (`hasConfirmedCard = false`, `hasVoted = false`, `hasGuessedWord = false`)
     * - Transitions room status to `ROLE_REVEAL`
     */
    suspend fun startNewRound(
        room: OnlineRoom,
        players: List<OnlineRoomPlayer>,
        usedWords: Set<String> = emptySet()
    ) {
        val hostUid = requireUserId()
        check(room.hostId == hostUid) { "فقط مضيف الغرفة يستطيع بدء الجولة." }
        check(room.status != MatchPhase.MATCH_OVER.name) { "انتهت المباراة ولا يمكن بدء جولة تلقائية." }

        val activePlayers = players.filter { it.isConnected }
        val validation = SpyGameEngine.validateMatchSettings(
            playerCount = activePlayers.size,
            spyCount = room.spyCount.toInt(),
            winScore = room.winScore.toInt()
        )
        if (!validation.isValid) {
            throw IllegalStateException(validation.errorMessageAr ?: "الإعدادات غير صالحة")
        }

        val nextRound = room.roundNumber.toInt() + 1
        val playerIds = activePlayers.map { it.userId }
        val (spyIds, wordSelection) = SpyGameEngine.distributeRolesAndWord(
            playerIds = playerIds,
            spyCount = room.spyCount.toInt(),
            category = WordCategory.fromKey(room.category),
            usedWords = usedWords
        )

        val roomRef = db.collection("rooms").document(room.code)
        val vaultRef = roomRef.collection("vault").document("round_$nextRound")

        try {
            // 1. Write RoundVault for authoritative scoring & host migration
            vaultRef.set(
                mapOf(
                    "hostId" to hostUid,
                    "roundNumber" to nextRound,
                    "secretWord" to wordSelection.secretWord,
                    "spyIds" to spyIds.toList(),
                    "spyOptions" to wordSelection.spyOptions,
                    "scored" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Write isolated PlayerSecret documents & reset player round flags in bounded 2-doc batches
            for (p in activePlayers) {
                val isSpy = p.userId in spyIds
                val secretRef = roomRef.collection("secrets").document(p.userId)
                val pRef = roomRef.collection("players").document(p.userId)
                val playerBatch = db.batch()
                playerBatch.set(
                    secretRef,
                    mapOf(
                        "userId" to p.userId,
                        "hostId" to hostUid,
                        "roundNumber" to nextRound,
                        "isSpy" to isSpy,
                        "secretWord" to if (isSpy) "" else wordSelection.secretWord,
                        "spyOptions" to if (isSpy) wordSelection.spyOptions else emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                playerBatch.update(
                    pRef,
                    mapOf(
                        "hasConfirmedCard" to false,
                        "hasVoted" to false,
                        "hasGuessedWord" to false,
                        "lastRoundScore" to 0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                playerBatch.commit().await()
            }

            // 3. Transition room to ROLE_REVEAL last so all secrets are guaranteed ready
            roomRef.update(
                mapOf(
                    "status" to MatchPhase.ROLE_REVEAL.name,
                    "roundNumber" to nextRound,
                    "questionCount" to 0,
                    "currentAskerId" to "",
                    "currentAnswererId" to "",
                    "turnEventId" to "round_${nextRound}_init",
                    "revealedWord" to "",
                    "revealedSpyIds" to emptyList<String>(),
                    "roundVotesSummary" to emptyMap<String, List<String>>(),
                    "roundGuessesSummary" to emptyMap<String, String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roomRef.path)
            throw e
        }
    }

    /**
     * Player taps "تم" after viewing their secret word/role card.
     * Turns their avatar ring green for everyone in real time.
     */
    suspend fun confirmSecretCardViewed(roomCode: String) {
        val uid = requireUserId()
        val playerRef = db.collection("rooms").document(roomCode)
            .collection("players").document(uid)
        try {
            playerRef.update(
                mapOf(
                    "hasConfirmedCard" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, playerRef.path)
            throw e
        }
    }

    /**
     * Host starts the QUESTIONS phase ONLY after verifying all connected players confirmed viewing their secret card.
     */
    suspend fun startQuestionsPhase(
        room: OnlineRoom,
        players: List<OnlineRoomPlayer>
    ) {
        val hostUid = requireUserId()
        check(room.hostId == hostUid) { "فقط المضيف يمكنه بدء مرحلة الأسئلة." }
        val connectedPlayers = players.filter { it.isConnected }
        if (connectedPlayers.any { !it.hasConfirmedCard }) {
            throw IllegalStateException("لا يمكن بدء الأسئلة قبل تأكيد جميع اللاعبين مشاهدة الكلمة السرية.")
        }

        val firstPair = SpyGameEngine.pickNextQuestionPair(connectedPlayers.map { it.userId })
        val roomRef = db.collection("rooms").document(room.code)
        try {
            roomRef.update(
                mapOf(
                    "status" to MatchPhase.QUESTIONS.name,
                    "questionCount" to 1,
                    "currentAskerId" to firstPair.askerId,
                    "currentAnswererId" to firstPair.answererId,
                    "turnEventId" to UUID.randomUUID().toString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Advances to the next random (Asker, Answerer) question turn using transactional check on `turnEventId`
     * to prevent duplicate/conflicting transitions.
     */
    suspend fun nextQuestionTurn(
        roomCode: String,
        expectedTurnEventId: String,
        eligiblePlayerIds: List<String>
    ) {
        val uid = requireUserId()
        val roomRef = db.collection("rooms").document(roomCode)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                val currentStatus = snap.getString("status") ?: ""
                val currentEventId = snap.getString("turnEventId") ?: ""
                val currentAsker = snap.getString("currentAskerId") ?: ""
                val hostId = snap.getString("hostId") ?: ""
                val currentCount = (snap.getLong("questionCount") ?: 0L).toInt()
                val prevAnswerer = snap.getString("currentAnswererId") ?: ""

                if (currentStatus != MatchPhase.QUESTIONS.name) return@runTransaction
                if (currentEventId != expectedTurnEventId) return@runTransaction
                if (uid != currentAsker && uid != hostId) {
                    throw IllegalStateException("فقط السائل الحالي أو المضيف يمكنه الانتقال للسؤال التالي.")
                }

                val nextPair = SpyGameEngine.pickNextQuestionPair(
                    eligiblePlayerIds = eligiblePlayerIds,
                    previousPair = SpyGameEngine.QuestionPair(currentAsker, prevAnswerer)
                )

                tx.update(
                    roomRef,
                    mapOf(
                        "questionCount" to (currentCount + 1),
                        "currentAskerId" to nextPair.askerId,
                        "currentAnswererId" to nextPair.answererId,
                        "turnEventId" to UUID.randomUUID().toString(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Transitions from QUESTIONS to VOTING once questionCount >= 7.
     */
    suspend fun triggerVotingPhase(roomCode: String, expectedTurnEventId: String) {
        val uid = requireUserId()
        val roomRef = db.collection("rooms").document(roomCode)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                val currentStatus = snap.getString("status") ?: ""
                val currentEventId = snap.getString("turnEventId") ?: ""
                val currentCount = (snap.getLong("questionCount") ?: 0L).toInt()
                val currentAsker = snap.getString("currentAskerId") ?: ""
                val hostId = snap.getString("hostId") ?: ""

                if (currentStatus != MatchPhase.QUESTIONS.name) return@runTransaction
                if (currentEventId != expectedTurnEventId) return@runTransaction
                if (!SpyGameEngine.canStartVoting(currentCount)) {
                    throw IllegalStateException("لا يمكن بدء التصويت قبل إتمام ٧ أسئلة على الأقل.")
                }
                if (uid != currentAsker && uid != hostId) {
                    throw IllegalStateException("فقط السائل الحالي أو المضيف يمكنه بدء التصويت.")
                }

                tx.update(
                    roomRef,
                    mapOf(
                        "status" to MatchPhase.VOTING.name,
                        "turnEventId" to UUID.randomUUID().toString(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Submits an immutable vote ballot and marks `hasVoted = true` atomically.
     */
    suspend fun submitVote(
        roomCode: String,
        roundNumber: Int,
        selectedSuspectIds: List<String>,
        allPlayerIds: Set<String>,
        spyCount: Int
    ) {
        val uid = requireUserId()
        val validation = SpyGameEngine.validateVoteSelection(
            voterId = uid,
            selectedSuspectIds = selectedSuspectIds,
            allPlayerIds = allPlayerIds,
            spyCount = spyCount
        )
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.errorMessageAr ?: "تصويت غير صالح")
        }

        val roomRef = db.collection("rooms").document(roomCode)
        val ballotId = "r${roundNumber}_$uid"
        val ballotRef = roomRef.collection("votes").document(ballotId)
        val playerRef = roomRef.collection("players").document(uid)

        try {
            val batch = db.batch()
            batch.set(
                ballotRef,
                mapOf(
                    "voterId" to uid,
                    "roundNumber" to roundNumber,
                    "selectedSuspectIds" to selectedSuspectIds.distinct(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                playerRef,
                mapOf(
                    "hasVoted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ballotRef.path)
            throw e
        }
    }

    /**
     * Host transitions room from VOTING to SPY_GUESS once all connected players have cast their votes.
     */
    suspend fun advanceToSpyGuessPhase(room: OnlineRoom, players: List<OnlineRoomPlayer>) {
        val hostUid = requireUserId()
        check(room.hostId == hostUid) { "فقط المضيف يدير انتقال المراحل." }
        val connected = players.filter { it.isConnected }
        if (connected.any { !it.hasVoted }) return

        val roomRef = db.collection("rooms").document(room.code)
        try {
            roomRef.update(
                mapOf(
                    "status" to MatchPhase.SPY_GUESS.name,
                    "turnEventId" to UUID.randomUUID().toString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Spy submits their independent secret word guess from the 8 category options.
     */
    suspend fun submitSpyWordGuess(
        roomCode: String,
        roundNumber: Int,
        guessedWord: String
    ) {
        val uid = requireUserId()
        val roomRef = db.collection("rooms").document(roomCode)
        val guessId = "r${roundNumber}_$uid"
        val guessRef = roomRef.collection("guesses").document(guessId)
        val playerRef = roomRef.collection("players").document(uid)

        try {
            val batch = db.batch()
            batch.set(
                guessRef,
                mapOf(
                    "spyId" to uid,
                    "roundNumber" to roundNumber,
                    "guessedWord" to guessedWord.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                playerRef,
                mapOf(
                    "hasGuessedWord" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, guessRef.path)
            throw e
        }
    }

    /**
     * Host reads RoundVault, verifies all connected spies have guessed, computes authoritative scores
     * via `SpyGameEngine.calculateRoundAndWinners`, prevents duplicate scoring via `vault.scored`,
     * and transitions room to `ROUND_RESULTS` or `MATCH_OVER`.
     */
    suspend fun evaluateAndFinalizeRoundIfReady(
        room: OnlineRoom,
        players: List<OnlineRoomPlayer>
    ): Boolean {
        val hostUid = requireUserId()
        if (room.hostId != hostUid || room.status != MatchPhase.SPY_GUESS.name) return false

        val roundNum = room.roundNumber.toInt()
        val roomRef = db.collection("rooms").document(room.code)
        val vaultRef = roomRef.collection("vault").document("round_$roundNum")

        try {
            val vaultSnap = vaultRef.get().await()
            val vault = vaultSnap.toObject(RoundVaultDoc::class.java) ?: return false
            if (vault.scored) return true

            val spyIds = vault.spyIds.toSet()
            val connectedSpies = players.filter { it.userId in spyIds && it.isConnected }
            if (connectedSpies.any { !it.hasGuessedWord }) {
                return false // Still waiting for one or more connected spies to guess independently
            }

            // Fetch each player's deterministic ballot & each spy's deterministic guess
            val votesMap = mutableMapOf<String, List<String>>()
            for (p in players) {
                val ballotSnap = roomRef.collection("votes").document("r${roundNum}_${p.userId}").get().await()
                if (ballotSnap.exists()) {
                    val ballot = ballotSnap.toObject(VoteBallotDoc::class.java)
                    if (ballot != null) {
                        votesMap[p.userId] = ballot.selectedSuspectIds
                    }
                }
            }

            val guessesMap = mutableMapOf<String, String>()
            for (spyId in spyIds) {
                val guessSnap = roomRef.collection("guesses").document("r${roundNum}_$spyId").get().await()
                if (guessSnap.exists()) {
                    val guess = guessSnap.toObject(SpyWordGuessDoc::class.java)
                    if (guess != null) {
                        guessesMap[spyId] = guess.guessedWord
                    }
                }
            }

            val previousTotals = players.associate { it.userId to it.totalScore.toInt() }
            val result = SpyGameEngine.calculateRoundAndWinners(
                playerIds = players.map { it.userId },
                spyIds = spyIds,
                secretWord = vault.secretWord,
                votesByVoter = votesMap,
                guessesBySpy = guessesMap,
                previousTotalScores = previousTotals,
                winScore = room.winScore.toInt()
            )

            // 1. Mark vault scored first so repeated triggers are idempotent
            vaultRef.update(
                mapOf(
                    "scored" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Update player scores in bounded 2-doc batches
            for (chunk in players.chunked(2)) {
                val scoreBatch = db.batch()
                for (p in chunk) {
                    val earned = result.playerScores[p.userId]?.roundTotalEarned ?: 0
                    val newTotal = result.updatedTotalScores[p.userId] ?: p.totalScore.toInt()
                    val pRef = roomRef.collection("players").document(p.userId)
                    scoreBatch.update(
                        pRef,
                        mapOf(
                            "lastRoundScore" to earned,
                            "totalScore" to newTotal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                scoreBatch.commit().await()
            }

            // 3. Publish revealed results & transition room status last
            val nextPhase = if (result.isMatchOver) MatchPhase.MATCH_OVER.name else MatchPhase.ROUND_RESULTS.name
            roomRef.update(
                mapOf(
                    "status" to nextPhase,
                    "revealedWord" to vault.secretWord,
                    "revealedSpyIds" to spyIds.toList(),
                    "winnerIds" to result.winnerIds,
                    "roundVotesSummary" to votesMap,
                    "roundGuessesSummary" to guessesMap,
                    "turnEventId" to "round_${roundNum}_done",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roomRef.path)
            throw e
        }
    }

    /**
     * Resets a completed match (`MATCH_OVER`) back to `LOBBY` with zeroed scores when Host taps "مباراة جديدة".
     */
    suspend fun resetMatchToLobby(roomCode: String, players: List<OnlineRoomPlayer>) {
        requireUserId()
        val roomRef = db.collection("rooms").document(roomCode)
        try {
            for (chunk in players.chunked(2)) {
                val resetBatch = db.batch()
                for (p in chunk) {
                    val pRef = roomRef.collection("players").document(p.userId)
                    resetBatch.update(
                        pRef,
                        mapOf(
                            "totalScore" to 0,
                            "lastRoundScore" to 0,
                            "hasConfirmedCard" to false,
                            "hasVoted" to false,
                            "hasGuessedWord" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                resetBatch.commit().await()
            }
            roomRef.update(
                mapOf(
                    "status" to MatchPhase.LOBBY.name,
                    "roundNumber" to 0,
                    "questionCount" to 0,
                    "currentAskerId" to "",
                    "currentAnswererId" to "",
                    "turnEventId" to "",
                    "revealedWord" to "",
                    "revealedSpyIds" to emptyList<String>(),
                    "winnerIds" to emptyList<String>(),
                    "roundVotesSummary" to emptyMap<String, List<String>>(),
                    "roundGuessesSummary" to emptyMap<String, String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    /**
     * Individual microphone mute toggle: strictly updates ONLY the authenticated caller's own `isMicMuted` flag.
     */
    suspend fun setMyMicrophoneMuted(roomCode: String, isMuted: Boolean) {
        val uid = requireUserId()
        val playerRef = db.collection("rooms").document(roomCode)
            .collection("players").document(uid)
        try {
            playerRef.update(
                mapOf(
                    "isMicMuted" to isMuted,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, playerRef.path)
            throw e
        }
    }

    /**
     * Updates presence heartbeat for connection monitoring & host migration detection.
     */
    suspend fun sendPresenceHeartbeat(roomCode: String, isConnected: Boolean = true) {
        val uid = auth.currentUser?.uid ?: return
        val playerRef = db.collection("rooms").document(roomCode)
            .collection("players").document(uid)
        try {
            playerRef.update(
                mapOf(
                    "isConnected" to isConnected,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, playerRef.path)
        }
    }

    /**
     * Leaves the room cleanly; if the leaving player is the current Host, atomically migrates
     * Host ownership to the deterministic next connected player (`SpyGameEngine.selectNextHostId`)
     * while preserving all match state, round secrets, and scores!
     */
    suspend fun leaveRoom(roomCode: String) {
        val uid = auth.currentUser?.uid ?: return
        val roomRef = db.collection("rooms").document(roomCode)
        try {
            val roomSnap = roomRef.get().await()
            if (!roomSnap.exists()) return
            val room = roomSnap.toObject(OnlineRoom::class.java) ?: return
            if (uid !in room.memberUids) return

            val playersSnap = roomRef.collection("players")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .await()
            val allPlayers = playersSnap.toObjects(OnlineRoomPlayer::class.java)

            val remainingMembers = room.memberUids.filter { it != uid }
            val connectedMap = allPlayers.associate { it.userId to (it.isConnected && it.userId != uid) }
            val joinedOrderMap = allPlayers.associate { it.userId to it.joinedOrder.toInt() }

            val newHostId = if (room.hostId == uid) {
                SpyGameEngine.selectNextHostId(remainingMembers, connectedMap, joinedOrderMap) ?: uid
            } else {
                room.hostId
            }

            val batch = db.batch()
            batch.update(
                roomRef,
                mapOf(
                    "memberUids" to remainingMembers,
                    "hostId" to newHostId,
                    "isClosed" to (remainingMembers.isEmpty() || room.isClosed),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.delete(roomRef.collection("players").document(uid))
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
        }
    }

    /**
     * Claims Host Migration if the current host disconnected (`isConnected == false`) or left `memberUids`,
     * and the deterministic `SpyGameEngine.selectNextHostId` elects `currentUserId`.
     */
    suspend fun attemptHostMigrationIfEligible(
        room: OnlineRoom,
        players: List<OnlineRoomPlayer>
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (room.hostId == uid) return false

        val hostPlayer = players.firstOrNull { it.userId == room.hostId }
        val hostGoneOrDisconnected = (room.hostId !in room.memberUids) || (hostPlayer != null && !hostPlayer.isConnected)
        if (!hostGoneOrDisconnected) return false

        val candidateMembers = room.memberUids.filter { it != room.hostId }
        val connectedMap = players.associate { it.userId to it.isConnected }
        val orderMap = players.associate { it.userId to it.joinedOrder.toInt() }
        val electedUid = SpyGameEngine.selectNextHostId(candidateMembers, connectedMap, orderMap)

        if (electedUid != uid) return false

        val roomRef = db.collection("rooms").document(room.code)
        return try {
            db.runTransaction { tx ->
                val freshRoom = tx.get(roomRef)
                val currentHost = freshRoom.getString("hostId") ?: ""
                if (currentHost != room.hostId) return@runTransaction false
                tx.update(
                    roomRef,
                    mapOf(
                        "hostId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                true
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            false
        }
    }

    // --- WebRTC Voice Signaling ---

    suspend fun sendVoiceSignal(
        roomCode: String,
        targetUserId: String,
        type: String,
        payload: String
    ) {
        val uid = requireUserId()
        val signalId = UUID.randomUUID().toString()
        val signalRef = db.collection("rooms").document(roomCode)
            .collection("signals").document(signalId)
        try {
            signalRef.set(
                mapOf(
                    "senderId" to uid,
                    "targetId" to targetUserId,
                    "roomCode" to roomCode,
                    "type" to type,
                    "payload" to payload,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, signalRef.path)
        }
    }

    fun observeIncomingVoiceSignals(roomCode: String, currentUserId: String): Flow<List<VoiceSignalDoc>> {
        val path = "rooms/$roomCode/signals"
        return db.collection("rooms").document(roomCode).collection("signals")
            .whereEqualTo("roomCode", roomCode)
            .whereEqualTo("targetId", currentUserId)
            .snapshots()
            .map { snap ->
                snap.toObjects(VoiceSignalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun deleteConsumedSignal(roomCode: String, signalId: String) {
        if (signalId.isBlank()) return
        val ref = db.collection("rooms").document(roomCode).collection("signals").document(signalId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
        }
    }

    // --- Personal Match Statistics ---

    suspend fun recordMatchStat(
        mode: String,
        won: Boolean,
        score: Int,
        playerCount: Int
    ) {
        val uid = auth.currentUser?.uid ?: return
        val statId = UUID.randomUUID().toString()
        val ref = db.collection("users").document(uid).collection("history").document(statId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "mode" to mode,
                    "won" to won,
                    "score" to score.coerceIn(0, 10000),
                    "playerCount" to playerCount.coerceIn(3, 20),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    fun observeUserMatchHistory(userId: String): Flow<List<UserMatchStatDoc>> = flow {
        val path = "users/$userId/history"
        emitAll(
            db.collection("users").document(userId).collection("history")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserMatchStatDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { err ->
                    if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                    throw err
                }
        )
    }
}
