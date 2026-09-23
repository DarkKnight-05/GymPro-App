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


async function deleteQueryDocs(query) {
  let deleted = 0;
  while (true) {
    const snap = await query.limit(450).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += snap.size;
    if (snap.size < 450) break;
  }
  return deleted;
}

/**
 * Permanent member deletion is deliberately privileged.
 * It removes the member and all business records owned by that member,
 * plus the member's Storage photo directory. Archive/restore remains the
 * normal reversible workflow.
 */
exports.adminDeleteMember = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const { memberId } = request.data || {};
  if (typeof memberId !== "string" || !memberId.trim()) {
    throw new HttpsError("invalid-argument", "Member ID is required.");
  }

  const gymId = caller.get("gymId");
  const memberRef = db.doc(`gyms/${gymId}/members/${memberId}`);
  const member = await memberRef.get();
  if (!member.exists) {
    throw new HttpsError("not-found", "Member not found in this gym.");
  }

  if (!member.get("branchRemoteId")) {
    throw new HttpsError("failed-precondition", "Member branch information is missing.");
  }

  await deleteQueryDocs(
    db.collection(`gyms/${gymId}/payments`).whereEqualTo("memberRemoteId", memberId)
  );
  await deleteQueryDocs(
    db.collection(`gyms/${gymId}/measurements`).whereEqualTo("memberRemoteId", memberId)
  );
  await deleteQueryDocs(
    db.collection(`gyms/${gymId}/attendance`).whereEqualTo("memberRemoteId", memberId)
  );

  await memberRef.delete();

  const { getStorage } = require("firebase-admin/storage");
  try {
    const bucket = getStorage().bucket();
    await bucket.deleteFiles({
      prefix: `gyms/${gymId}/members/${memberId}/`
    });
  } catch (error) {
    console.error("Member photo cleanup failed:", error);
    throw new HttpsError(
      "internal",
      "Member records were deleted, but profile-photo cleanup failed."
    );
  }

  return { ok: true };
});
