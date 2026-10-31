# Security Specification for الجاسوس (Al-Jasoos)

## 1. Data Invariants
1. **Unauthenticated Rejection**: Zero collections allow reads or writes when `request.auth == null`.
2. **Secret Word Isolation**: `/rooms/{roomCode}/secrets/{playerId}` can ONLY be read (`get`) by `request.auth.uid == playerId`. No `list` queries are permitted on `secrets`.
3. **Round Vault Isolation**: `/rooms/{roomCode}/vault/{roundId}` can ONLY be written and read (`get`) by the current authoritative `hostId` on the parent `/rooms/{roomCode}` document (allowing seamless Host Migration while keeping the word hidden from regular players and spies during active rounds).
4. **Immutable Votes**: `/rooms/{roomCode}/votes/{ballotId}` enforces `ballotId == "r" + roundNumber + "_" + voterId`, `voterId == request.auth.uid`, and `allow update, delete: if false;` so votes can never be changed or cast on behalf of another player. Votes can only be listed when `room.status` is `VOTING`, `SPY_GUESS`, `ROUND_RESULTS`, or `MATCH_OVER` by room members (`request.auth.uid in resource.data.memberUids` or scoped by round). Specifically, to prevent revealing other players' vote selections before voting ends, `votes` can only be read by the voter themselves (`resource.data.voterId == request.auth.uid`), the current host (for tallying), or when `resource.data.isRevealed == true` / during results! Even better: each `VoteBallot` only needs to be read by `voterId == request.auth.uid` during `VOTING`, and read by room members once `room.status in ['SPY_GUESS', 'ROUND_RESULTS', 'MATCH_OVER']` (or we store `hostId` / check parent room status on `get`, and for `list` we check `request.auth.uid in resource.data.memberUids` ONLY after voting or keep votes readable by voter/host and publish tallied vote breakdown in `RoundSummary` or check `room` status). Wait — remember Pillar 8: **Cost-Optimization Clause: strictly forbidden from placing `get()` or `exists()` inside `allow list` blocks!**
   Let's ensure ZERO `get()`/`exists()` inside ANY `allow list` block!
   How do we enforce vote secrecy during `VOTING` without `get()` in `allow list`?
   - During `VOTING`, clients do NOT list `votes`! Instead, when a player casts their vote in a batch/transaction, they write `/rooms/{roomCode}/votes/{ballotId}` AND update their own `/rooms/{roomCode}/players/{uid}` field `hasVoted = true`!
   - Everyone observes `/rooms/{roomCode}/players` (where `request.auth.uid in resource.data.memberUids` or scoped safely) and sees *who* has voted (`hasVoted == true`), WITHOUT seeing `selectedSuspectIds`!
   - When all players have `hasVoted == true`, the Host reads individual deterministic ballot documents via `get()` (`/rooms/{roomCode}/votes/r{round}_{uid}`) — `get` CAN check parent `room`! And at `ROUND_RESULTS`, the Host writes the verified round summary (including revealed word, revealed spies, votes map, guesses map, round scores) onto `/rooms/{roomCode}` (or `/rooms/{roomCode}/summaries/r{round}` where `memberUids` is denormalized), so all players see full results without ever exposing `votes` or `guesses` prematurely!
5. **Immutable Spy Word Guesses**: `/rooms/{roomCode}/guesses/{guessId}` enforces `guessId == "r" + roundNumber + "_" + spyId`, `spyId == request.auth.uid`, and `allow update, delete: if false;`. No `list` allowed on `guesses`! Only `get` by `spyId == request.auth.uid` or the current `room.hostId` after `SPY_GUESS` completes! Thus no spy can ever see another spy's word guess before finishing their own attempt!
6. **Terminal Match State Locking**: Once `/rooms/{roomCode}` reaches `status == 'MATCH_OVER'`, `status` cannot transition back to `QUESTIONS`, `VOTING`, or `ROLE_REVEAL` unless the host explicitly resets a brand new match (`roundNumber == 0` and `status == 'LOBBY'`). Automatic new rounds after `MATCH_OVER` are blocked.
7. **Individual Microphone Control**: On `/rooms/{roomCode}/players/{playerId}`, ONLY `request.auth.uid == playerId` can update `isMicMuted`. Not even the host can unmute or mute another player's microphone!
8. **WebRTC Signaling Privacy**: `/rooms/{roomCode}/signals/{signalId}` only allows creation if `senderId == request.auth.uid`, and `list`/`get` only if `resource.data.targetId == request.auth.uid || resource.data.senderId == request.auth.uid`.

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated read on `/rooms/ABC123` -> Denied (`isSignedIn()`).
2. Regular player attempts `get` on another player's `/rooms/ABC123/secrets/otherUid` -> Denied (`request.auth.uid == playerId`).
3. Spy attempts `get` on `/rooms/ABC123/vault/round_1` -> Denied (`room.hostId == request.auth.uid`).
4. Voter attempts to `update` `/rooms/ABC123/votes/r1_uid1` to change their vote -> Denied (`allow update: if false`).
5. Attacker attempts to `create` vote with `voterId: "victimUid"` -> Denied (`incoming().voterId == request.auth.uid`).
6. Spy 1 attempts to `get` `/rooms/ABC123/guesses/r1_spy2` -> Denied (`spyId == request.auth.uid || isRoomHost(roomCode)`).
7. Host attempts to `update` `isMicMuted: false` on `/rooms/ABC123/players/otherUid` -> Denied (host action `affectedKeys()` excludes `isMicMuted`).
8. Non-host attempts to `update` `spyCount` or `winScore` on `/rooms/ABC123` -> Denied (`existing().hostId == request.auth.uid` required for settings action).
9. Shadow Update with ghost field `isAdmin: true` on `/rooms/ABC123` -> Denied (`keys().hasOnly(...)`).
10. Value Poisoning with 50KB `name` string on `RoomPlayer` -> Denied (`name.size() <= 40`).
11. Path Poisoning with 500-char roomCode -> Denied (`isValidRoomCode(roomCode)`).
12. Unfiltered `list` query on `/users/uid1/history` by `uid2` -> Denied (`request.auth.uid == userId`).
