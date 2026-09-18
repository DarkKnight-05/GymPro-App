package com.gymmanager.app.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Cloud identity for the single-gym app. No passwords are stored in Firestore. */
class CloudAuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun createAdmin(gymName: String, email: String, password: String): AuthSession {
        require(email.trim().isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Firebase did not return a user ID")
        val gymId = UUID.randomUUID().toString()
        db.collection("gyms").document(gymId).set(mapOf("name" to gymName.trim(), "createdAt" to System.currentTimeMillis(), "ownerUid" to uid)).await()
        db.collection("users").document(uid).set(mapOf("gymId" to gymId, "role" to "ADMIN", "email" to email.trim().lowercase(), "active" to true)).await()
        return AuthSession(uid, gymId, "ADMIN", null, email.trim())
    }

    suspend fun loginAdmin(email: String, password: String): AuthSession? {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: return null
        val doc = db.collection("users").document(uid).get().await()
        if (!doc.exists() || doc.getBoolean("active") == false || doc.getString("role") != "ADMIN") { auth.signOut(); return null }
        return AuthSession(uid, doc.getString("gymId") ?: return null, "ADMIN", null, result.user?.email)
    }

    suspend fun currentSession(): AuthSession? {
        val user = auth.currentUser ?: return null
        val doc = db.collection("users").document(user.uid).get().await()
        if (!doc.exists() || doc.getBoolean("active") == false) return null
        return AuthSession(user.uid, doc.getString("gymId") ?: return null, doc.getString("role") ?: return null, doc.getString("branchRemoteId"), user.email)
    }

    suspend fun sendPasswordReset(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }

    suspend fun setAdminEmail(currentPassword: String, newEmail: String) {
        val user = auth.currentUser ?: error("You are not signed in.")
        val oldEmail = user.email ?: error("This account does not have an email address.")
        user.reauthenticate(EmailAuthProvider.getCredential(oldEmail, currentPassword)).await()
        user.updateEmail(newEmail.trim()).await()
        db.collection("users").document(user.uid).update("email", newEmail.trim().lowercase()).await()
    }

    suspend fun changeOwnPassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: error("You are not signed in.")
        val email = user.email ?: error("This account does not have a recovery email.")
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    fun signOut() = auth.signOut()
}

data class AuthSession(val uid: String, val gymId: String, val role: String, val branchRemoteId: String?, val email: String? = null)
