package com.gymmanager.app.data


enum class PlanType { DAILY, WEEKLY, HALF_MONTH, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUSTOM }
enum class MembershipCategory { STANDARD, PREMIUM_PT }
enum class Gender { GENTS, LADY }

data class Member(
    val id: Long = 0,
    val remoteId: String? = null,
    val name: String,
    val age: Int? = null,
    val gender: Gender,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val medicalIssues: String = "",
    val place: String = "",
    val goal: String = "",
    val phone: String,
    val photoUri: String? = null,
    val branchId: Long,
    val branchRemoteId: String? = null,
    val membershipCategory: MembershipCategory = MembershipCategory.STANDARD,
    val joinDateMillis: Long,
    val addedAtMillis: Long = System.currentTimeMillis(),
    val planType: PlanType,
    val customDurationDays: Int? = null,
    val feeAmount: Double,
    val membershipStartDateMillis: Long = joinDateMillis,
    val nextDueDateMillis: Long,
    val notes: String = "",
    val isArchived: Boolean = false,
    val archivedAtMillis: Long? = null
)
