package com.gymmanager.app.data


data class MeasurementRecord(
    val id: Long = 0,
    val remoteId: String? = null,
    val memberId: Long,
    val memberRemoteId: String,
    val branchRemoteId: String? = null,
    val recordedAtMillis: Long = System.currentTimeMillis(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val recordedBy: String = ""
)
