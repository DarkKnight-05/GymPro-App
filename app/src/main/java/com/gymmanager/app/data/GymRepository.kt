package com.gymmanager.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import kotlin.math.absoluteValue

/**
 * Cloud-only data repository.
 *
 * Firestore is the only persistent store for gym business data. There is deliberately
 * no Room/SQLite cache in the application data path. StateFlows below are in-memory
 * snapshots populated from Firestore listeners and disappear when the process ends.
 */
class GymRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var gymId: String? = null
    private var trainerBranchRemoteId: String? = null
    private var listeners = emptyList<ListenerRegistration>()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    private val _measurements = MutableStateFlow<List<MeasurementRecord>>(emptyList())

    val branches: Flow<List<Branch>> = _branches

    fun startCloudSync(gymId: String, trainerBranchRemoteId: String? = null) {
        stopCloudSync()
        this.gymId = gymId
        this.trainerBranchRemoteId = trainerBranchRemoteId
        val root = firestore.collection("gyms").document(gymId)

        val branchQuery = if (trainerBranchRemoteId != null) {
            root.collection("branches").whereEqualTo(FieldPath.documentId(), trainerBranchRemoteId)
        } else root.collection("branches")

        val memberQuery = if (trainerBranchRemoteId != null) {
            root.collection("members").whereEqualTo("branchRemoteId", trainerBranchRemoteId)
        } else root.collection("members")

        val measurementQuery = if (trainerBranchRemoteId != null) {
            root.collection("measurements").whereEqualTo("branchRemoteId", trainerBranchRemoteId)
        } else root.collection("measurements")

        val paymentQuery = if (trainerBranchRemoteId != null) {
            root.collection("payments").whereEqualTo("branchRemoteId", trainerBranchRemoteId)
        } else root.collection("payments")

        listeners = listOf(
            branchQuery.addSnapshotListener { snap, error ->
                if (error == null && snap != null) _branches.value = snap.documents.map(::toBranch).sortedBy { it.name.lowercase() }
            },
            memberQuery.addSnapshotListener { snap, error ->
                if (error == null && snap != null) _members.value = snap.documents.map(::toMember).sortedBy { it.name.lowercase() }
            },
            measurementQuery.addSnapshotListener { snap, error ->
                if (error == null && snap != null) _measurements.value = snap.documents.map(::toMeasurement)
            },
            paymentQuery.addSnapshotListener { snap, error ->
                if (error == null && snap != null) _payments.value = snap.documents.map(::toPayment).sortedByDescending { it.paidOnMillis }
            }
        )
    }

    fun stopCloudSync() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        gymId = null
        trainerBranchRemoteId = null
        _branches.value = emptyList()
        _members.value = emptyList()
        _payments.value = emptyList()
        _measurements.value = emptyList()
    }

    suspend fun ensureDefaultBranches() = Unit

    private fun stableId(remoteId: String): Long {
        val value = remoteId.hashCode().toLong().absoluteValue
        return if (value == 0L) 1L else value
    }

    private fun gymRoot() = firestore.collection("gyms").document(gymId ?: error("Not signed in"))

    private fun branchRef(remoteId: String) = gymRoot().collection("branches").document(remoteId)
    private fun memberRef(remoteId: String) = gymRoot().collection("members").document(remoteId)
    private fun paymentRef(remoteId: String) = gymRoot().collection("payments").document(remoteId)
    private fun measurementRef(remoteId: String) = gymRoot().collection("measurements").document(remoteId)

    // ---- Branches ----
    suspend fun addBranch(branch: Branch): Long {
        val id = branch.remoteId.ifBlank { UUID.randomUUID().toString() }
        branchRef(id).set(mapOf(
            "name" to branch.name.trim(), "place" to branch.place.trim(), "updatedAt" to System.currentTimeMillis()
        )).await()
        val saved = branch.copy(id = stableId(id), remoteId = id)
        _branches.update { current -> (current.filterNot { it.remoteId == id } + saved).sortedBy { it.name.lowercase() } }
        return saved.id
    }

    suspend fun updateBranch(branch: Branch) {
        branchRef(branch.remoteId).set(mapOf(
            "name" to branch.name.trim(), "place" to branch.place.trim(), "updatedAt" to System.currentTimeMillis()
        )).await()
        _branches.update { current -> current.map { if (it.remoteId == branch.remoteId) branch else it } }
    }

    suspend fun deleteBranch(branch: Branch): Boolean {
        val hasMembers = _members.value.any { it.branchRemoteId == branch.remoteId }
        if (hasMembers) return false
        branchRef(branch.remoteId).delete().await()
        _branches.update { it.filterNot { b -> b.remoteId == branch.remoteId } }
        return true
    }

    // ---- Members ----
    private fun filteredMembers(branchId: Long?, archived: Boolean, gender: Gender? = null): List<Member> =
        _members.value.filter { m ->
            m.isArchived == archived &&
                (branchId == null || m.branchId == branchId) &&
                (gender == null || m.gender == gender)
        }.sortedBy { it.name.lowercase() }

    fun activeMembers(branchId: Long?, gender: Gender? = null): Flow<List<Member>> =
        _members.map { filteredMembers(branchId, false, gender) }

    fun membersSnapshotForExport(): List<Member> = _members.value.toList()

    fun archivedMembers(branchId: Long?): Flow<List<Member>> =
        _members.map { filteredMembers(branchId, true) }

    suspend fun addMember(member: Member): Long {
        val remoteId = member.remoteId ?: UUID.randomUUID().toString()
        val branchRemote = member.branchRemoteId ?: _branches.value.firstOrNull { it.id == member.branchId }?.remoteId
            ?: error("Select a branch before saving the member")
        val saved = member.copy(id = stableId(remoteId), remoteId = remoteId, branchRemoteId = branchRemote)
        writeMember(saved)
        _members.update { current -> (current.filterNot { it.remoteId == remoteId } + saved).sortedBy { it.name.lowercase() } }
        return saved.id
    }

    suspend fun updateMember(member: Member): Member {
        val remoteId = member.remoteId ?: error("Member is not synchronized with the cloud")
        val branchRemote = member.branchRemoteId ?: _branches.value.firstOrNull { it.id == member.branchId }?.remoteId
            ?: error("Member branch is missing")
        val saved = member.copy(id = stableId(remoteId), branchRemoteId = branchRemote)
        writeMember(saved)
        _members.update { current -> (current.filterNot { it.remoteId == remoteId } + saved).sortedBy { it.name.lowercase() } }
        return saved
    }

    suspend fun archiveMember(memberId: Long) {
        getMember(memberId)?.let { updateMember(it.copy(isArchived = true, archivedAtMillis = System.currentTimeMillis())) }
    }

    suspend fun restoreMember(memberId: Long) {
        getMember(memberId)?.let { updateMember(it.copy(isArchived = false, archivedAtMillis = null)) }
    }

    suspend fun getMember(memberId: Long): Member? = _members.value.firstOrNull { it.id == memberId }

    fun searchMembers(query: String, branchId: Long?, gender: Gender? = null): Flow<List<Member>> =
        _members.map { list ->
            list.filter { m ->
                !m.isArchived && (branchId == null || m.branchId == branchId) && (gender == null || m.gender == gender) &&
                    (m.name.contains(query, true) || m.phone.contains(query, true) || (m.remoteId?.contains(query, true) == true))
            }.sortedBy { it.name.lowercase() }
        }

    fun searchArchivedMembers(query: String, branchId: Long?): Flow<List<Member>> =
        _members.map { list ->
            list.filter { m ->
                m.isArchived && (branchId == null || m.branchId == branchId) &&
                    (m.name.contains(query, true) || m.phone.contains(query, true) || (m.remoteId?.contains(query, true) == true))
            }.sortedByDescending { it.archivedAtMillis ?: 0L }
        }

    suspend fun dueSoonOrOverdueOnce(branchId: Long?, daysAhead: Int = 3): List<Member> {
        val threshold = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, daysAhead) }.timeInMillis
        return filteredMembers(branchId, false).filter { it.nextDueDateMillis <= threshold }
    }

    fun expiredMembers(branchId: Long?): Flow<List<Member>> = _members.map { list ->
        val now = System.currentTimeMillis()
        list.filter { !it.isArchived && it.nextDueDateMillis < now && (branchId == null || it.branchId == branchId) }
            .sortedBy { it.nextDueDateMillis }
    }

    fun expiringSoonMembers(branchId: Long?, daysAhead: Int = 5): Flow<List<Member>> = _members.map { list ->
        val now = System.currentTimeMillis()
        val threshold = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, daysAhead) }.timeInMillis
        list.filter { !it.isArchived && it.nextDueDateMillis in now..threshold && (branchId == null || it.branchId == branchId) }
            .sortedBy { it.nextDueDateMillis }
    }

    // ---- Measurements ----
    fun measurementsForMember(memberId: Long): Flow<List<MeasurementRecord>> = _measurements.map { list ->
        list.filter { it.memberId == memberId }.sortedByDescending { it.recordedAtMillis }
    }

    suspend fun addMeasurement(item: MeasurementRecord): Long {
        val remoteId = item.remoteId ?: UUID.randomUUID().toString()
        val member = _members.value.firstOrNull { it.id == item.memberId } ?: error("Member not found")
        val saved = item.copy(id = stableId(remoteId), remoteId = remoteId, memberRemoteId = member.remoteId ?: error("Member is not synchronized"), branchRemoteId = member.branchRemoteId)
        measurementRef(remoteId).set(mapOf(
            "memberRemoteId" to saved.memberRemoteId,
            "branchRemoteId" to saved.branchRemoteId,
            "recordedAtMillis" to saved.recordedAtMillis,
            "weightKg" to saved.weightKg, "heightCm" to saved.heightCm,
            "neckCm" to saved.neckCm, "waistCm" to saved.waistCm, "hipCm" to saved.hipCm,
            "recordedBy" to saved.recordedBy, "updatedAt" to System.currentTimeMillis()
        )).await()
        _measurements.update { current -> (current.filterNot { it.remoteId == remoteId } + saved).sortedByDescending { it.recordedAtMillis } }
        return saved.id
    }

    suspend fun updateMeasurement(item: MeasurementRecord) {
        val remoteId = item.remoteId ?: return
        measurementRef(remoteId).set(mapOf(
            "memberRemoteId" to item.memberRemoteId, "branchRemoteId" to item.branchRemoteId,
            "recordedAtMillis" to item.recordedAtMillis, "weightKg" to item.weightKg, "heightCm" to item.heightCm,
            "neckCm" to item.neckCm, "waistCm" to item.waistCm, "hipCm" to item.hipCm,
            "recordedBy" to item.recordedBy, "updatedAt" to System.currentTimeMillis()
        )).await()
        _measurements.update { current -> (current.filterNot { it.remoteId == remoteId } + item).sortedByDescending { it.recordedAtMillis } }
    }

    // ---- Payments ----
    fun paymentsForMember(memberId: Long): Flow<List<Payment>> = _payments.map { list ->
        list.filter { it.memberId == memberId }.sortedByDescending { it.paidOnMillis }
    }

    fun paymentsForDate(dateMillis: Long): Flow<List<Payment>> = _payments.map { list ->
        val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        list.filter { it.paidOnMillis >= start && it.paidOnMillis < end }
            .sortedByDescending { it.paidOnMillis }
    }

    suspend fun recordPayment(member: Member, amount: Double, method: PaymentMethod, note: String = "", recordedBy: String = ""): Payment {
        val memberRemote = member.remoteId ?: error("Member is not synchronized with the cloud")
        val branchRemote = member.branchRemoteId ?: error("Member branch is missing")
        val paymentRemoteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Keep the billing anniversary anchored to the existing cycle. If the member is
        // already overdue, advance that cycle until the next due date is in the future.
        var cycleStart = member.nextDueDateMillis.takeIf { it > 0L } ?: member.membershipStartDateMillis
        if (cycleStart <= now) {
            // Late payment: move forward from the established anniversary until the
            // next cycle is in the future. The receipt date never becomes the anniversary.
            while (cycleStart <= now) {
                cycleStart = calculateDueDate(cycleStart, member.planType, member.customDurationDays)
            }
        } else {
            // Payment made before the current due date: renew one full cycle from that due date.
            cycleStart = calculateDueDate(cycleStart, member.planType, member.customDurationDays)
        }
        val newDueDate = cycleStart

        val payment = Payment(
            id = stableId(paymentRemoteId), remoteId = paymentRemoteId,
            memberId = member.id, branchId = member.branchId, branchRemoteId = branchRemote,
            amount = amount, paidOnMillis = now, periodCoveredMillis = newDueDate,
            method = method, note = note, recordedBy = recordedBy
        )

        // Batch makes the payment and member update a single cloud commit.
        val batch = firestore.batch()
        batch.set(paymentRef(paymentRemoteId), mapOf(
            "memberRemoteId" to memberRemote,
            "branchRemoteId" to branchRemote,
            "amount" to amount,
            "paidOnMillis" to now,
            "periodCoveredMillis" to newDueDate,
            "method" to method.name,
            "note" to note,
            "recordedBy" to recordedBy,
            "updatedAt" to now
        ))
        batch.set(memberRef(memberRemote), mapOf(
            "nextDueDateMillis" to newDueDate,
            "membershipStartDateMillis" to member.membershipStartDateMillis,
            "updatedAt" to now
        ), com.google.firebase.firestore.SetOptions.merge())
        batch.commit().await()
        val updatedMember = member.copy(nextDueDateMillis = newDueDate)
        _members.update { current -> current.map { if (it.remoteId == memberRemote) updatedMember else it } }
        _payments.update { current -> (current.filterNot { it.remoteId == paymentRemoteId } + payment).sortedByDescending { it.paidOnMillis } }
        return payment
    }

    suspend fun updatePayment(payment: Payment) {
        val remoteId = payment.remoteId ?: error("Payment is not synchronized with the cloud")
        paymentRef(remoteId).set(mapOf(
            "memberRemoteId" to _members.value.firstOrNull { it.id == payment.memberId }?.remoteId,
            "branchRemoteId" to payment.branchRemoteId,
            "amount" to payment.amount, "paidOnMillis" to payment.paidOnMillis,
            "periodCoveredMillis" to payment.periodCoveredMillis, "method" to payment.method.name,
            "note" to payment.note, "recordedBy" to payment.recordedBy,
            "updatedAt" to System.currentTimeMillis()
        )).await()
        _payments.update { current -> (current.filterNot { it.remoteId == remoteId } + payment).sortedByDescending { it.paidOnMillis } }
    }

    // ---- Reports ----
    suspend fun reviewFor(startMillis: Long, endMillis: Long, branchId: Long?): PeriodReview {
        val payments = _payments.value.filter { it.paidOnMillis in startMillis..endMillis && (branchId == null || it.branchId == branchId) }
            .distinctBy { it.remoteId ?: "${it.memberId}|${it.amount}|${it.paidOnMillis}|${it.periodCoveredMillis}" }
        val collected = payments.sumOf { it.amount }
        val cash = payments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amount }
        val gpay = payments.filter { it.method == PaymentMethod.GPAY }.sumOf { it.amount }
        val admissions = _members.value.count { it.joinDateMillis in startMillis..endMillis && (branchId == null || it.branchId == branchId) }
        return PeriodReview(collected, admissions, cash, gpay, payments.size)
    }

    suspend fun branchReviewsFor(startMillis: Long, endMillis: Long): Map<Long, PeriodReview> {
        val payments = _payments.value
            .filter { it.paidOnMillis in startMillis..endMillis }
            .distinctBy { it.remoteId ?: "${it.memberId}|${it.amount}|${it.paidOnMillis}|${it.periodCoveredMillis}" }

        val paymentGroups = payments.groupBy { it.branchId }
        val memberGroups = _members.value
            .filter { it.joinDateMillis in startMillis..endMillis }
            .groupingBy { it.branchId }
            .eachCount()

        val branchIds = (paymentGroups.keys + memberGroups.keys).toSet()
        return branchIds.associateWith { branchId ->
            val branchPayments = paymentGroups[branchId].orEmpty()
            PeriodReview(
                feesCollected = branchPayments.sumOf { it.amount },
                newAdmissions = memberGroups[branchId] ?: 0,
                cash = branchPayments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amount },
                gpay = branchPayments.filter { it.method == PaymentMethod.GPAY }.sumOf { it.amount },
                paymentCount = branchPayments.size
            )
        }
    }

    suspend fun todayReview(branchId: Long?): PeriodReview {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return reviewFor(c.timeInMillis, System.currentTimeMillis(), branchId)
    }

    suspend fun weeklyReview(branchId: Long?): PeriodReview {
        val start = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return reviewFor(start, System.currentTimeMillis(), branchId)
    }

    suspend fun monthlyReview(branchId: Long?): PeriodReview {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return reviewFor(start, System.currentTimeMillis(), branchId)
    }

    fun calculateDueDate(startMillis: Long, plan: PlanType, customDays: Int? = null): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
        when (plan) {
            PlanType.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            PlanType.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
            PlanType.HALF_MONTH -> cal.add(Calendar.DAY_OF_MONTH, 15)
            PlanType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            PlanType.QUARTERLY -> cal.add(Calendar.MONTH, 3)
            PlanType.HALF_YEARLY -> cal.add(Calendar.MONTH, 6)
            PlanType.YEARLY -> cal.add(Calendar.YEAR, 1)
            PlanType.CUSTOM -> cal.add(Calendar.DAY_OF_MONTH, (customDays ?: 1).coerceAtLeast(1))
        }
        return cal.timeInMillis
    }

    private suspend fun writeMember(member: Member) {
        val remoteId = member.remoteId ?: error("Member remote ID missing")
        memberRef(remoteId).set(mapOf(
            "name" to member.name, "age" to member.age, "gender" to member.gender.name,
            "weightKg" to member.weightKg, "heightCm" to member.heightCm, "neckCm" to member.neckCm,
            "waistCm" to member.waistCm, "hipCm" to member.hipCm, "medicalIssues" to member.medicalIssues,
            "place" to member.place, "goal" to member.goal, "phone" to member.phone, "photoUri" to member.photoUri,
            "branchRemoteId" to member.branchRemoteId, "membershipCategory" to member.membershipCategory.name,
            "joinDateMillis" to member.joinDateMillis, "addedAtMillis" to member.addedAtMillis,
            "planType" to member.planType.name, "customDurationDays" to member.customDurationDays,
            "feeAmount" to member.feeAmount, "membershipStartDateMillis" to member.membershipStartDateMillis,
            "nextDueDateMillis" to member.nextDueDateMillis, "notes" to member.notes,
            "isArchived" to member.isArchived, "archivedAtMillis" to member.archivedAtMillis,
            "updatedAt" to System.currentTimeMillis()
        )).await()
    }

    private fun toBranch(d: com.google.firebase.firestore.DocumentSnapshot): Branch =
        Branch(id = stableId(d.id), remoteId = d.id, name = d.getString("name") ?: "", place = d.getString("place") ?: "")

    private fun toMember(d: com.google.firebase.firestore.DocumentSnapshot): Member {
        val branchRemote = d.getString("branchRemoteId")
        return Member(
            id = stableId(d.id), remoteId = d.id, name = d.getString("name") ?: "",
            age = d.getLong("age")?.toInt(), gender = runCatching { Gender.valueOf(d.getString("gender") ?: Gender.GENTS.name) }.getOrDefault(Gender.GENTS),
            weightKg = d.getDouble("weightKg"), heightCm = d.getDouble("heightCm"), neckCm = d.getDouble("neckCm"), waistCm = d.getDouble("waistCm"), hipCm = d.getDouble("hipCm"),
            medicalIssues = d.getString("medicalIssues") ?: "", place = d.getString("place") ?: "", goal = d.getString("goal") ?: "", phone = d.getString("phone") ?: "",
            photoUri = d.getString("photoUri"), branchId = stableId(branchRemote ?: "missing"), branchRemoteId = branchRemote,
            membershipCategory = runCatching { MembershipCategory.valueOf(d.getString("membershipCategory") ?: MembershipCategory.STANDARD.name) }.getOrDefault(MembershipCategory.STANDARD),
            joinDateMillis = d.getLong("joinDateMillis") ?: System.currentTimeMillis(), addedAtMillis = d.getLong("addedAtMillis") ?: System.currentTimeMillis(),
            planType = runCatching { PlanType.valueOf(d.getString("planType") ?: PlanType.MONTHLY.name) }.getOrDefault(PlanType.MONTHLY),
            customDurationDays = d.getLong("customDurationDays")?.toInt(), feeAmount = d.getDouble("feeAmount") ?: 0.0,
            membershipStartDateMillis = d.getLong("membershipStartDateMillis") ?: System.currentTimeMillis(), nextDueDateMillis = d.getLong("nextDueDateMillis") ?: System.currentTimeMillis(),
            notes = d.getString("notes") ?: "", isArchived = d.getBoolean("isArchived") ?: false, archivedAtMillis = d.getLong("archivedAtMillis")
        )
    }

    private fun toPayment(d: com.google.firebase.firestore.DocumentSnapshot): Payment {
        val memberRemote = d.getString("memberRemoteId") ?: ""
        val member = _members.value.firstOrNull { it.remoteId == memberRemote }
        return Payment(
            id = stableId(d.id), remoteId = d.id, memberId = member?.id ?: stableId(memberRemote),
            branchId = member?.branchId ?: stableId(d.getString("branchRemoteId") ?: "missing"), branchRemoteId = d.getString("branchRemoteId"),
            amount = d.getDouble("amount") ?: 0.0, paidOnMillis = d.getLong("paidOnMillis") ?: 0L,
            periodCoveredMillis = d.getLong("periodCoveredMillis") ?: 0L,
            method = runCatching { PaymentMethod.valueOf(d.getString("method") ?: PaymentMethod.CASH.name) }.getOrDefault(PaymentMethod.CASH),
            note = d.getString("note") ?: "", recordedBy = d.getString("recordedBy") ?: ""
        )
    }

    private fun toMeasurement(d: com.google.firebase.firestore.DocumentSnapshot): MeasurementRecord {
        val memberRemote = d.getString("memberRemoteId") ?: ""
        val member = _members.value.firstOrNull { it.remoteId == memberRemote }
        return MeasurementRecord(
            id = stableId(d.id), remoteId = d.id, memberId = member?.id ?: stableId(memberRemote), memberRemoteId = memberRemote,
            branchRemoteId = d.getString("branchRemoteId"), recordedAtMillis = d.getLong("recordedAtMillis") ?: 0L,
            weightKg = d.getDouble("weightKg"), heightCm = d.getDouble("heightCm"), neckCm = d.getDouble("neckCm"),
            waistCm = d.getDouble("waistCm"), hipCm = d.getDouble("hipCm"), recordedBy = d.getString("recordedBy") ?: ""
        )
    }

    // Kept for reminder-worker compatibility: it is still cloud-only.
    companion object {
        suspend fun forCurrentUser(): GymRepository? {
            val user = FirebaseAuth.getInstance().currentUser ?: return null
            val doc = FirebaseFirestore.getInstance().collection("users").document(user.uid).get().await()
            val gid = doc.getString("gymId") ?: return null
            val branch = doc.getString("branchRemoteId")
            return GymRepository().also { it.startCloudSync(gid, branch) }
        }
    }
}

data class PeriodReview(val feesCollected: Double, val newAdmissions: Int, val cash: Double = 0.0, val gpay: Double = 0.0, val paymentCount: Int = 0)
