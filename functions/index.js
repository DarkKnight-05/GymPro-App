const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

initializeApp();
const db = getFirestore();

async function requireAdmin(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in as an admin.");
  const caller = await db.doc(`users/${request.auth.uid}`).get();
  if (!caller.exists || caller.get("role") !== "ADMIN" || caller.get("active") === false) {
    throw new HttpsError("permission-denied", "Admin access is required.");
  }
  return caller;
}

exports.adminSetTrainerPassword = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const { trainerId, newPassword } = request.data || {};
  if (typeof trainerId !== "string" || typeof newPassword !== "string" || newPassword.length < 6) {
    throw new HttpsError("invalid-argument", "A trainer ID and password of at least 6 characters are required.");
  }
  const trainer = await db.doc(`users/${trainerId}`).get();
  if (!trainer.exists || trainer.get("role") !== "TRAINER" || trainer.get("gymId") !== caller.get("gymId")) {
    throw new HttpsError("not-found", "Trainer not found in this gym.");
  }
  await getAuth().updateUser(trainerId, { password: newPassword, disabled: trainer.get("active") === false });
  return { ok: true };
});

exports.adminDeleteTrainer = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const { trainerId } = request.data || {};
  if (typeof trainerId !== "string") throw new HttpsError("invalid-argument", "Trainer ID is required.");
  const trainer = await db.doc(`users/${trainerId}`).get();
  if (!trainer.exists || trainer.get("role") !== "TRAINER" || trainer.get("gymId") !== caller.get("gymId")) {
    throw new HttpsError("not-found", "Trainer not found in this gym.");
  }
  await db.doc(`gyms/${caller.get("gymId")}/trainers/${trainerId}`).delete();
  await db.doc(`users/${trainerId}`).delete();
  await getAuth().deleteUser(trainerId);
  return { ok: true };
});
