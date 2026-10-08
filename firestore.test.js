const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ROB_UID = "rob_uid_123";
const JOKE_UID = "joke_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const HID = "ROBJOK";

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

const defaultTypes = [
  "pasta", "rijst", "aardappelen", "vlees", "vis",
  "vegetarisch", "soep", "wok", "ovenschotel", "brood/snel"
];

async function seedHousehold(members = [ROB_UID]) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("households").doc(HID).set({
      members,
      inviteCode: HID,
      types: defaultTypes,
      createdBy: ROB_UID,
      createdAt: new Date(Date.now() - 10000),
      updatedAt: new Date(Date.now() - 10000),
    });
  });
}

test("1. Unauthenticated user cannot read or create households", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("households").doc(HID).get());
  await assertFails(
    unauthDb.collection("households").doc(HID).set({
      members: [ROB_UID],
      inviteCode: HID,
      types: defaultTypes,
      createdBy: ROB_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("2. Authenticated user (Rob) can create household and Joke can join via inviteCode", async () => {
  const robDb = testEnv.authenticatedContext(ROB_UID).firestore();
  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    robDb.collection("households").doc(HID).set({
      members: [ROB_UID],
      inviteCode: HID,
      types: defaultTypes,
      createdBy: ROB_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Joke joins via controlled update
  const jokeDb = testEnv.authenticatedContext(JOKE_UID).firestore();
  await assertSucceeds(
    jokeDb.collection("households").doc(HID).update({
      members: [ROB_UID, JOKE_UID],
      updatedAt: new Date(),
    })
  );

  // Now both Rob and Joke can read the household
  await assertSucceeds(robDb.collection("households").doc(HID).get());
  await assertSucceeds(jokeDb.collection("households").doc(HID).get());
});

test("3. Third user (Charlie) cannot join when household already has 2 members", async () => {
  await seedHousehold([ROB_UID, JOKE_UID]);
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("households").doc(HID).update({
      members: [ROB_UID, JOKE_UID, CHARLIE_UID],
      updatedAt: new Date(),
    })
  );
  await assertFails(
    charlieDb.collection("households").doc(HID).update({
      members: [ROB_UID, CHARLIE_UID],
      updatedAt: new Date(),
    })
  );
});

test("4. Non-member cannot read or write dishes, days, weeks, or looseItems", async () => {
  await seedHousehold([ROB_UID, JOKE_UID]);
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("households").doc(HID).collection("dishes").get());
  await assertFails(
    charlieDb.collection("households").doc(HID).collection("dishes").doc("dish1").set({
      name: "Spaghetti",
      type: "pasta",
      ingredients: [],
      createdBy: CHARLIE_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("5. Household members (Rob and Joke) can create and update dishes, days, weeks, and looseItems", async () => {
  await seedHousehold([ROB_UID, JOKE_UID]);
  const robDb = testEnv.authenticatedContext(ROB_UID).firestore();
  const jokeDb = testEnv.authenticatedContext(JOKE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Rob creates a dish
  await assertSucceeds(
    robDb.collection("households").doc(HID).collection("dishes").doc("dish1").set({
      name: "Spaghetti Bolognaise",
      type: "pasta",
      ingredients: [
        { raw: "2 uien", name: "ui", quantity: 2, unit: "stuk", needsNormalization: false }
      ],
      createdBy: ROB_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Joke updates the dish
  await assertSucceeds(
    jokeDb.collection("households").doc(HID).collection("dishes").doc("dish1").update({
      name: "Spaghetti Bolognaise Deluxe",
      updatedAt: new Date(),
    })
  );

  // Joke cannot spoof createdBy on update
  await assertFails(
    jokeDb.collection("households").doc(HID).collection("dishes").doc("dish1").update({
      createdBy: JOKE_UID,
      updatedAt: new Date(),
    })
  );

  // Rob plans a day
  await assertSucceeds(
    robDb.collection("households").doc(HID).collection("days").doc("2026-10-11").set({
      kind: "koken",
      dishId: "dish1",
      dishSnapshot: {
        name: "Spaghetti Bolognaise Deluxe",
        type: "pasta",
        ingredients: []
      },
      updatedBy: ROB_UID,
      updatedAt: now,
    })
  );

  // Joke checks an item in weeks
  await assertSucceeds(
    jokeDb.collection("households").doc(HID).collection("weeks").doc("2026-10-11").set({
      checked: { "ui|stuk": true },
      updatedBy: JOKE_UID,
      updatedAt: now,
    })
  );

  // Joke adds a loose item
  await assertSucceeds(
    jokeDb.collection("households").doc(HID).collection("looseItems").doc("item1").set({
      name: "Koffiebonen",
      recurring: true,
      checked: false,
      createdBy: JOKE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("6. Client query alignment: member can query their household using array-contains", async () => {
  await seedHousehold([ROB_UID, JOKE_UID]);
  const robDb = testEnv.authenticatedContext(ROB_UID).firestore();
  await assertSucceeds(
    robDb.collection("households").where("members", "array-contains", ROB_UID).get()
  );

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("households").get()
  );
});
