package com.gymmanager.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/** Trainer directory. Passwords are handled by Firebase Authentication / secure Functions. */
class TrainerCloudRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private var gymId: String? = null

    fun configure(gymId: String) { this.gymId = gymId }
    private fun trainers() = db.collection("gyms").document(gymId ?: error("Not signed in")).collection("trainers")
    private fun authEmail(username: String) = "${username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")}@gymmanager.local"

    fun observeTrainers(): Flow<List<TrainerAccount>> = callbackFlow {
        val listener = trainers().addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.documents?.map { d ->
                TrainerAccount(id=d.id,name=d.getString("name") ?: "",username=d.getString("username") ?: "",branchRemoteId=d.getString("branchRemoteId"),active=d.getBoolean("active") ?: true,phone=d.getString("phone") ?: "")
            } ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun addTrainer(name:String, username:String, password:String, branch:Branch) {
        val cleanUsername = username.trim().lowercase()
        require(cleanUsername.matches(Regex("[a-z0-9._-]{3,30}"))) { "Username must be 3–30 letters, numbers, dots, underscores or hyphens." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        val secondaryName = "trainer_${System.currentTimeMillis()}"
        val baseApp = com.google.firebase.FirebaseApp.getInstance()
        val app = com.google.firebase.FirebaseApp.initializeApp(baseApp.applicationContext, baseApp.options, secondaryName) ?: com.google.firebase.FirebaseApp.getInstance(secondaryName)
        val secondaryAuth = FirebaseAuth.getInstance(app)
        try {
            val result = secondaryAuth.createUserWithEmailAndPassword(authEmail(cleanUsername),password).await()
            val uid=result.user?.uid ?: error("Firebase did not return a trainer UID")
            trainers().document(uid).set(mapOf("name" to name.trim(),"username" to cleanUsername,"branchRemoteId" to branch.remoteId,"active" to true,"phone" to "")).await()
            db.collection("users").document(uid).set(mapOf("gymId" to gymId,"role" to "TRAINER","branchRemoteId" to branch.remoteId,"active" to true,"username" to cleanUsername)).await()
        } finally { secondaryAuth.signOut(); app.delete() }
    }

    suspend fun updateTrainer(trainer:TrainerAccount,name:String,branch:Branch) {
        trainers().document(trainer.id).update(mapOf("name" to name.trim(),"branchRemoteId" to branch.remoteId,"active" to true)).await()
        db.collection("users").document(trainer.id).update(mapOf("branchRemoteId" to branch.remoteId,"active" to true)).await()
    }

    suspend fun setTrainerPassword(trainerId:String,newPassword:String) {
        require(newPassword.length >= 6) { "Password must be at least 6 characters." }
        functions.getHttpsCallable("adminSetTrainerPassword").call(mapOf("trainerId" to trainerId,"newPassword" to newPassword)).await()
    }

    suspend fun removeTrainer(trainer:TrainerAccount) {
        functions.getHttpsCallable("adminDeleteTrainer").call(mapOf("trainerId" to trainer.id)).await()
    }

    suspend fun deactivateTrainer(trainer:TrainerAccount) {
        trainers().document(trainer.id).update("active",false).await()
        db.collection("users").document(trainer.id).update("active",false).await()
    }

    suspend fun verifyTrainerLogin(username:String,password:String):TrainerAccount? {
        val result=auth.signInWithEmailAndPassword(authEmail(username),password).await()
        val uid=result.user?.uid ?: return null
        val profile=db.collection("users").document(uid).get().await()
        if(!profile.exists()||profile.getString("role")!="TRAINER"||profile.getBoolean("active")==false){auth.signOut();return null}
        val resolvedGymId=profile.getString("gymId")?:run{auth.signOut();return null}
        gymId=resolvedGymId
        val doc=trainers().document(uid).get().await()
        if(!doc.exists()||doc.getBoolean("active")==false){auth.signOut();return null}
        return TrainerAccount(id=uid,name=doc.getString("name") ?: "",username=doc.getString("username") ?: username,branchRemoteId=doc.getString("branchRemoteId"),active=true,phone=doc.getString("phone") ?: "")
    }

}
