package com.gymmanager.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Cloud helper for media. Business data is handled directly by GymRepository/Firestore;
 * there is intentionally no local database or cloud-to-Room synchronization layer.
 */
class CloudSyncRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var gymId: String? = null

    fun start(gymId: String, trainerBranchRemoteId: String? = null, onError: (Exception) -> Unit = {}) {
        this.gymId = gymId
    }

    fun stop() { gymId = null }

    suspend fun uploadMemberPhoto(context: Context, uri: Uri, memberRemoteId: String, oldPhotoUrl: String? = null): String {
        val gid = gymId ?: throw IllegalStateException("Cloud sync is not active. Please sign in again.")
        val imageBytes = context.normalizedJpeg(uri)
            ?: throw IllegalArgumentException("The selected image could not be read.")
        val fileName = "profile_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child("gyms/$gid/members/$memberRemoteId/$fileName")
        return try {
            ref.putBytes(imageBytes, com.google.firebase.storage.StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            val newUrl = ref.downloadUrl.await().toString()
            if (!oldPhotoUrl.isNullOrBlank() && oldPhotoUrl != newUrl) {
                runCatching { storage.getReferenceFromUrl(oldPhotoUrl).delete().await() }
            }
            newUrl
        } catch (e: Exception) {
            throw IllegalStateException("Profile photo upload failed: ${e.message ?: "unknown Firebase Storage error"}", e)
        }
    }

    private fun Context.normalizedJpeg(uri: Uri): ByteArray? {
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: return null
        val source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxSide = 1280
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height).toFloat())
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { preScale(-1f, 1f); postRotate(270f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { preScale(-1f, 1f); postRotate(90f) }
            }
        }
        val normalized = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return ByteArrayOutputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.JPEG, 85, output)
            if (normalized !== source) normalized.recycle()
            source.recycle()
            output.toByteArray()
        }
    }
}
