const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_user_1";
const PLAYER2_UID = "player_user_2";
const SPY_UID = "spy_user_3";
const OUTSIDER_UID = "outsider_99";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowTs() {
  return new Date(Date.now() - 1000);
}

test("1. Unauthenticated caller cannot read or create rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("rooms").doc("AB12CD").get());
  await assertFails(unauthDb.collection("rooms").doc("AB12CD").set({ code: "AB12CD" }));
});

test("2. Host can create valid room and player profile", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const ts = nowTs();
  await assertSucceeds(
    hostDb.collection("rooms").doc("AB12CD").set({
      code: "AB12CD",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "LOBBY",
      category: "FOOD",
      spyCount: 1,
      winScore: 100,
      roundNumber: 0,
      questionCount: 0,
      memberUids: [HOST_UID],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    hostDb.collection("rooms").doc("AB12CD").collection("players").doc(HOST_UID).set({
      userId: HOST_UID,
      roomCode: "AB12CD",
      memberUids: [HOST_UID],
      name: "أحمد",
      avatarIndex: 1,
      totalScore: 0,
      lastRoundScore: 0,
      hasConfirmedCard: false,
      hasVoted: false,
      hasGuessedWord: false,
      isMicMuted: false,
      isConnected: true,
      joinedOrder: 1,
      lastSeenAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("3. Secret word isolation: Spy cannot read another player's secret or RoundVault", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("RM9999").set({
      code: "RM9999",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "ROLE_REVEAL",
      category: "FOOD",
      spyCount: 1,
      winScore: 100,
      roundNumber: 1,
      questionCount: 0,
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("rooms").doc("RM9999").collection("secrets").doc(PLAYER2_UID).set({
      userId: PLAYER2_UID,
      hostId: HOST_UID,
      roundNumber: 1,
      isSpy: false,
      secretWord: "شاورما",
      spyOptions: [],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("rooms").doc("RM9999").collection("secrets").doc(SPY_UID).set({
      userId: SPY_UID,
      hostId: HOST_UID,
      roundNumber: 1,
      isSpy: true,
      secretWord: "",
      spyOptions: ["شاورما", "كبسة", "فلافل", "بيتزا", "برجر", "كشري", "منسف", "ملوخية"],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("rooms").doc("RM9999").collection("vault").doc("round_1").set({
      hostId: HOST_UID,
      roundNumber: 1,
      secretWord: "شاورما",
      spyIds: [SPY_UID],
      spyOptions: ["شاورما", "كبسة", "فلافل", "بيتزا", "برجر", "كشري", "منسف", "ملوخية"],
      scored: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const spyDb = testEnv.authenticatedContext(SPY_UID).firestore();
  const p2Db = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();

  // Spy can read ONLY their own secret (which has empty secretWord)
  await assertSucceeds(spyDb.collection("rooms").doc("RM9999").collection("secrets").doc(SPY_UID).get());
  // Spy CANNOT read Player 2's secret
  await assertFails(spyDb.collection("rooms").doc("RM9999").collection("secrets").doc(PLAYER2_UID).get());
  // Spy CANNOT list secrets
  await assertFails(spyDb.collection("rooms").doc("RM9999").collection("secrets").get());
  // Spy CANNOT read RoundVault
  await assertFails(spyDb.collection("rooms").doc("RM9999").collection("vault").doc("round_1").get());
  // Host CAN read RoundVault
  await assertSucceeds(hostDb.collection("rooms").doc("RM9999").collection("vault").doc("round_1").get());
  // Player 2 CAN read their own secret
  await assertSucceeds(p2Db.collection("rooms").doc("RM9999").collection("secrets").doc(PLAYER2_UID).get());
});

test("4. Voting cannot start before 7 questions, succeeds after 7 questions", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc("Q7ROOM").set({
      code: "Q7ROOM",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "QUESTIONS",
      category: "HOME",
      spyCount: 1,
      winScore: 100,
      roundNumber: 1,
      questionCount: 5,
      currentAskerId: PLAYER2_UID,
      currentAnswererId: SPY_UID,
      turnEventId: "evt_5",
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const p2Db = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  // Attempting to jump to VOTING at question 5 fails for non-host asker
  await assertFails(
    p2Db.collection("rooms").doc("Q7ROOM").update({
      status: "VOTING",
      turnEventId: "evt_vote_early",
      updatedAt: nowTs(),
    })
  );

  // Advancing to question 6 and 7 succeeds
  await assertSucceeds(
    p2Db.collection("rooms").doc("Q7ROOM").update({
      questionCount: 6,
      currentAskerId: PLAYER2_UID,
      currentAnswererId: HOST_UID,
      turnEventId: "evt_6",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    p2Db.collection("rooms").doc("Q7ROOM").update({
      questionCount: 7,
      currentAskerId: PLAYER2_UID,
      currentAnswererId: SPY_UID,
      turnEventId: "evt_7",
      updatedAt: nowTs(),
    })
  );

  // Now at questionCount == 7, currentAskerId CAN trigger VOTING!
  await assertSucceeds(
    p2Db.collection("rooms").doc("Q7ROOM").update({
      status: "VOTING",
      turnEventId: "evt_vote_ok",
      updatedAt: nowTs(),
    })
  );
});

test("5. Vote ballot is immutable, matches spyCount, and hides choices from other players", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc("VOTERM").set({
      code: "VOTERM",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "VOTING",
      category: "TOOLS",
      spyCount: 2,
      winScore: 100,
      roundNumber: 1,
      questionCount: 7,
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID, "spy_4"],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const p2Db = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  const spyDb = testEnv.authenticatedContext(SPY_UID).firestore();

  // Fails if selecting 1 suspect when spyCount is 2
  await assertFails(
    p2Db.collection("rooms").doc("VOTERM").collection("votes").doc(`r1_${PLAYER2_UID}`).set({
      voterId: PLAYER2_UID,
      roundNumber: 1,
      selectedSuspectIds: [SPY_UID],
      createdAt: nowTs(),
    })
  );

  // Succeeds when selecting exact spyCount (2 suspects)
  await assertSucceeds(
    p2Db.collection("rooms").doc("VOTERM").collection("votes").doc(`r1_${PLAYER2_UID}`).set({
      voterId: PLAYER2_UID,
      roundNumber: 1,
      selectedSuspectIds: [SPY_UID, "spy_4"],
      createdAt: nowTs(),
    })
  );

  // Cannot update vote after casting
  await assertFails(
    p2Db.collection("rooms").doc("VOTERM").collection("votes").doc(`r1_${PLAYER2_UID}`).update({
      selectedSuspectIds: [HOST_UID, SPY_UID],
    })
  );

  // Another non-host player CANNOT read Player 2's ballot
  await assertFails(
    spyDb.collection("rooms").doc("VOTERM").collection("votes").doc(`r1_${PLAYER2_UID}`).get()
  );
});

test("6. Host cannot control another player's microphone mute state", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("MICRM1").set({
      code: "MICRM1",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "QUESTIONS",
      category: "COUNTRIES",
      spyCount: 1,
      winScore: 100,
      roundNumber: 1,
      questionCount: 1,
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("rooms").doc("MICRM1").collection("players").doc(PLAYER2_UID).set({
      userId: PLAYER2_UID,
      roomCode: "MICRM1",
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      name: "سارة",
      avatarIndex: 2,
      totalScore: 0,
      lastRoundScore: 0,
      hasConfirmedCard: true,
      hasVoted: false,
      hasGuessedWord: false,
      isMicMuted: true,
      isConnected: true,
      joinedOrder: 2,
      lastSeenAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const p2Db = testEnv.authenticatedContext(PLAYER2_UID).firestore();

  // Host CANNOT unmute Player 2's microphone!
  await assertFails(
    hostDb.collection("rooms").doc("MICRM1").collection("players").doc(PLAYER2_UID).update({
      isMicMuted: false,
      updatedAt: nowTs(),
    })
  );

  // Player 2 CAN toggle their own microphone!
  await assertSucceeds(
    p2Db.collection("rooms").doc("MICRM1").collection("players").doc(PLAYER2_UID).update({
      isMicMuted: false,
      updatedAt: nowTs(),
    })
  );
});

test("7. Host Migration claim blocked while host is connected, succeeds when host disconnects", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("MIGRM1").set({
      code: "MIGRM1",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "QUESTIONS",
      category: "FOOD",
      spyCount: 1,
      winScore: 100,
      roundNumber: 1,
      questionCount: 2,
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      maxPlayers: 10,
      isClosed: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("rooms").doc("MIGRM1").collection("players").doc(HOST_UID).set({
      userId: HOST_UID,
      roomCode: "MIGRM1",
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      name: "المضيف",
      avatarIndex: 0,
      totalScore: 10,
      lastRoundScore: 10,
      hasConfirmedCard: true,
      hasVoted: false,
      hasGuessedWord: false,
      isMicMuted: false,
      isConnected: true,
      joinedOrder: 1,
      lastSeenAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const p2Db = testEnv.authenticatedContext(PLAYER2_UID).firestore();

  // Hijack attempt while host isConnected == true MUST FAIL
  await assertFails(
    p2Db.collection("rooms").doc("MIGRM1").update({
      hostId: PLAYER2_UID,
      updatedAt: nowTs(),
    })
  );

  // Mark host disconnected
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  await assertSucceeds(
    hostDb.collection("rooms").doc("MIGRM1").collection("players").doc(HOST_UID).update({
      isConnected: false,
      updatedAt: nowTs(),
    })
  );

  // Now Player 2 CAN claim host migration!
  await assertSucceeds(
    p2Db.collection("rooms").doc("MIGRM1").update({
      hostId: PLAYER2_UID,
      updatedAt: nowTs(),
    })
  );
});

test("8. Terminal MATCH_OVER blocks starting a new round automatically", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc("ENDRM1").set({
      code: "ENDRM1",
      hostId: HOST_UID,
      creatorId: HOST_UID,
      status: "MATCH_OVER",
      category: "FOOD",
      spyCount: 1,
      winScore: 50,
      roundNumber: 5,
      questionCount: 7,
      memberUids: [HOST_UID, PLAYER2_UID, SPY_UID],
      maxPlayers: 10,
      isClosed: false,
      winnerIds: [PLAYER2_UID],
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();

  // Host CANNOT jump from MATCH_OVER directly to ROLE_REVEAL (round 6)
  await assertFails(
    hostDb.collection("rooms").doc("ENDRM1").update({
      status: "ROLE_REVEAL",
      roundNumber: 6,
      updatedAt: nowTs(),
    })
  );

  // Host CAN reset to LOBBY with roundNumber == 0 for "مباراة جديدة"
  await assertSucceeds(
    hostDb.collection("rooms").doc("ENDRM1").update({
      status: "LOBBY",
      roundNumber: 0,
      questionCount: 0,
      winnerIds: [],
      revealedWord: "",
      revealedSpyIds: [],
      updatedAt: nowTs(),
    })
  );
});

test("9. Query alignment: outsider cannot list RoomPlayers or UserMatchStats", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("PRIVRM").collection("players").doc(HOST_UID).set({
      userId: HOST_UID,
      roomCode: "PRIVRM",
      memberUids: [HOST_UID, PLAYER2_UID],
      name: "أحمد",
      avatarIndex: 0,
      totalScore: 0,
      lastRoundScore: 0,
      hasConfirmedCard: false,
      hasVoted: false,
      hasGuessedWord: false,
      isMicMuted: false,
      isConnected: true,
      joinedOrder: 1,
      lastSeenAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(HOST_UID).collection("history").doc("stat1").set({
      userId: HOST_UID,
      mode: "ONLINE",
      won: true,
      score: 100,
      playerCount: 4,
      createdAt: ts,
    });
  });

  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const memberDb = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();

  // Outsider fails unscoped list query on rooms without membership (rules are not filters)
  await assertFails(
    outsiderDb
      .collection("rooms")
      .get()
  );

  // Member succeeds with aligned query on RoomPlayers
  await assertSucceeds(
    memberDb
      .collection("rooms")
      .doc("PRIVRM")
      .collection("players")
      .where("roomCode", "==", "PRIVRM")
      .get()
  );

  // Outsider fails reading Host's history
  await assertFails(
    outsiderDb.collection("users").doc(HOST_UID).collection("history").where("userId", "==", HOST_UID).get()
  );

  // Host succeeds reading own history
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("history").where("userId", "==", HOST_UID).get()
  );
});
