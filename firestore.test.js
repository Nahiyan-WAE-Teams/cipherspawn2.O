const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";
const CHANNEL_ID = "UC_x5XG1OV2P6uZZ5FSM9Ttw";

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

function pastDate(offsetMs = 1000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any TubePulse collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("channels").doc(CHANNEL_ID).set({
      userId: ALICE_UID,
      channelId: CHANNEL_ID,
      title: "Google for Developers",
      subscriberCount: 2400000,
      viewCount: 300000000,
      videoCount: 6000,
      latestLikeCount: 15000,
      milestoneStep: 10000,
      alertsEnabled: true,
      pinnedToWidget: true,
      hiddenSubscriberCount: false,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Authenticated owner: valid UserProfile create & partial updates succeed, ghost fields and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Studio",
      email: "alice@example.com",
      oauthConnected: false,
      syncIntervalSeconds: 15,
      notificationsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Action 1 partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      oauthConnected: true,
      syncIntervalSeconds: 30,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user PII read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      syncIntervalSeconds: 60,
      updatedAt: pastDate(500),
    })
  );

  // Ghost field / privilege escalation blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Future timestamp blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      syncIntervalSeconds: 60,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated owner: TrackedChannel CRUD, snapshots, and alerts parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  const channelRef = aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc(CHANNEL_ID);

  // Create channel (mirrors TrackedChannelRepository.upsertChannel without 'id')
  await assertSucceeds(
    channelRef.set({
      userId: ALICE_UID,
      channelId: CHANNEL_ID,
      title: "Google for Developers",
      handle: "@GoogleDevelopers",
      description: "Official channel",
      thumbnailUrl: "https://yt3.ggpht.com/sample",
      subscriberCount: 2410000,
      viewCount: 310000000,
      videoCount: 6150,
      latestLikeCount: 18400,
      milestoneStep: 10000,
      alertsEnabled: true,
      pinnedToWidget: true,
      hiddenSubscriberCount: false,
      uploadsPlaylistId: "UU_x5XG1OV2P6uZZ5FSM9Ttw",
      lastSyncedAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Action 1: Telemetry update
  await assertSucceeds(
    channelRef.update({
      subscriberCount: 2410150,
      viewCount: 310005000,
      videoCount: 6151,
      latestLikeCount: 18520,
      lastSyncedAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Action 2: Widget & alert preference update
  await assertSucceeds(
    channelRef.update({
      milestoneStep: 5000,
      alertsEnabled: false,
      pinnedToWidget: false,
      updatedAt: pastDate(200),
    })
  );

  // List query matching Kotlin repository
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("channels").get());

  // Snapshot creation (Parent Gate checks channel exists)
  await assertSucceeds(
    channelRef.collection("snapshots").doc("snap_001").set({
      userId: ALICE_UID,
      channelId: CHANNEL_ID,
      subscriberCount: 2410150,
      viewCount: 310005000,
      videoCount: 6151,
      likeCount: 18520,
      recordedAt: pastDate(100),
    })
  );

  // Snapshot cannot be mutated
  await assertFails(
    channelRef.collection("snapshots").doc("snap_001").update({
      subscriberCount: 9999999,
    })
  );

  // Alert creation & acknowledgement
  const alertRef = aliceDb.collection("users").doc(ALICE_UID).collection("alerts").doc("alert_001");
  await assertSucceeds(
    alertRef.set({
      userId: ALICE_UID,
      channelId: CHANNEL_ID,
      channelTitle: "Google for Developers",
      metricType: "SUBSCRIBERS",
      title: "Milestone Reached",
      message: "Crossed 2,410,000 subscribers",
      milestoneValue: 2410000,
      deltaValue: 150,
      acknowledged: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    alertRef.update({
      acknowledged: true,
      updatedAt: pastDate(100),
    })
  );

  // Reject @DocumentId 'id' leakage in create payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc("UC_other").set({
      id: "UC_other",
      userId: ALICE_UID,
      channelId: "UC_other",
      title: "Bad Payload",
      subscriberCount: 100,
      viewCount: 1000,
      videoCount: 10,
      latestLikeCount: 50,
      milestoneStep: 1000,
      alertsEnabled: true,
      pinnedToWidget: false,
      hiddenSubscriberCount: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject Bob reading or writing Alice's channels or alerts
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("alerts").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("channels").doc(CHANNEL_ID).delete()
  );
});
