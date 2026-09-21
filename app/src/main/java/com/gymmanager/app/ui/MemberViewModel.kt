package com.gymmanager.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.app.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemberViewModel(private val repo: GymRepository, private val cloud: CloudSyncRepository) : ViewModel() {
    init { viewModelScope.launch { repo.ensureDefaultBranches() } }
    val branches: StateFlow<List<Branch>> = repo.branches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val selectedBranchId = MutableStateFlow<Long?>(null)
    val currentBranchId: StateFlow<Long?> = selectedBranchId
    private val selectedGender = MutableStateFlow<Gender?>(null)
    val currentGender: StateFlow<Gender?> = selectedGender
    private val searchQuery = MutableStateFlow("")
    private val archivedSearchQuery = MutableStateFlow("")

    fun startCloudSync(gymId: String, trainerBranchRemoteId: String? = null) { repo.startCloudSync(gymId, trainerBranchRemoteId); cloud.start(gymId, trainerBranchRemoteId) }
    fun stopCloudSync() { repo.stopCloudSync(); cloud.stop() }
    fun setSelectedBranch(branchId: Long?) { selectedBranchId.value = branchId }
    fun setSelectedGender(gender: Gender?) { selectedGender.value = gender }
    fun activeMembersForBranch(branchId: Long) = repo.activeMembers(branchId)
    fun membersSnapshotForExport(): List<Member> = repo.membersSnapshotForExport()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMembers = combine(searchQuery, selectedBranchId, selectedGender) { q,b,g -> Triple(q,b,g) }.flatMapLatest { (q,b,g) -> if(q.isBlank()) repo.activeMembers(b,g) else repo.searchMembers(q,b,g) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val archivedMembers = combine(archivedSearchQuery, selectedBranchId) { q,b -> q to b }.flatMapLatest { (q,b) -> if(q.isBlank()) repo.archivedMembers(b) else repo.searchArchivedMembers(q,b) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val expiredMembers = selectedBranchId.flatMapLatest { repo.expiredMembers(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val expiringSoonMembers = selectedBranchId.flatMapLatest { repo.expiringSoonMembers(it,5) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _todayReview=MutableStateFlow<PeriodReview?>(null); val todayReview:StateFlow<PeriodReview?> = _todayReview
    private val _weeklyReview=MutableStateFlow<PeriodReview?>(null); val weeklyReview:StateFlow<PeriodReview?> = _weeklyReview
    private val _monthlyReview=MutableStateFlow<PeriodReview?>(null); val monthlyReview:StateFlow<PeriodReview?> = _monthlyReview
    fun loadReviews(branchId:Long?)=viewModelScope.launch{_todayReview.value=repo.todayReview(branchId);_weeklyReview.value=repo.weeklyReview(branchId);_monthlyReview.value=repo.monthlyReview(branchId)}
    private val _todayBranchReviews = MutableStateFlow<Map<Long, PeriodReview>>(emptyMap())
    val todayBranchReviews: StateFlow<Map<Long, PeriodReview>> = _todayBranchReviews
    private val _weeklyBranchReviews = MutableStateFlow<Map<Long, PeriodReview>>(emptyMap())
    val weeklyBranchReviews: StateFlow<Map<Long, PeriodReview>> = _weeklyBranchReviews
    private val _monthlyBranchReviews = MutableStateFlow<Map<Long, PeriodReview>>(emptyMap())
    val monthlyBranchReviews: StateFlow<Map<Long, PeriodReview>> = _monthlyBranchReviews

    fun loadBranchReviews() = viewModelScope.launch {
        fun dayStart(): Long = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0);
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val todayStart = dayStart()
        val weekStart = java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0);
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0);
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0);
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        _todayBranchReviews.value = repo.branchReviewsFor(todayStart, now)
        _weeklyBranchReviews.value = repo.branchReviewsFor(weekStart, now)
        _monthlyBranchReviews.value = repo.branchReviewsFor(monthStart, now)
    }

    fun setSearchQuery(q:String){searchQuery.value=q}; fun setArchivedSearchQuery(q:String){archivedSearchQuery.value=q}

    fun addMember(
        member: Member,
        photoUri: Uri? = null,
        context: Context? = null,
        onDone: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            val branchRemote = member.branchRemoteId ?: branches.value.firstOrNull { it.id == member.branchId }?.remoteId
            // Never publish a device-local content:// or file:// URI. Only a Firebase HTTPS URL
            // is stored in the member record so other devices can display the photo.
            var saved = member.copy(
                remoteId = member.remoteId ?: java.util.UUID.randomUUID().toString(),
                branchRemoteId = branchRemote,
                photoUri = member.photoUri?.takeIf { it.startsWith("https://") }
            )
            // Create the Firestore member first so Storage security rules can verify
            // the member's gym/branch before allowing the photo upload.
            val localId = repo.addMember(saved.copy(photoUri = null))
            if (photoUri != null) {
                if (context == null) throw IllegalStateException("Unable to access the selected photo.")
                try {
                    val uploadedPhotoUrl = cloud.uploadMemberPhoto(context, photoUri, saved.remoteId!!)
                    repo.updateMember(saved.copy(photoUri = uploadedPhotoUrl))
                } catch (photoError: Exception) {
                    // The member is already safely created. Do not make the user submit the
                    // form again and risk creating a duplicate. The photo can be added later
                    // from the member profile.
                    onError("Member created, but profile photo upload failed. You can add the photo from the member profile.")
                }
            }
            onDone(localId)
        } catch (e: Exception) {
            onError(e.message ?: "Unable to save member.")
        }
    }

    fun updateMember(
        member: Member,
        newPhotoUri: Uri? = null,
        context: Context? = null,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            var saved = member
            if (newPhotoUri != null) {
                if (context == null) throw IllegalStateException("Unable to access the selected photo.")
                val remoteId = member.remoteId ?: throw IllegalStateException("Member is not synchronized with the cloud yet.")
                saved = member.copy(photoUri = cloud.uploadMemberPhoto(context, newPhotoUri, remoteId, member.photoUri))
            }
            val synced = repo.updateMember(saved)
            onDone()
        } catch (e: Exception) {
            onError(e.message ?: "Unable to save member.")
        }
    }
    fun calculateDueDate(startMillis:Long,plan:PlanType,customDays:Int?=null)=repo.calculateDueDate(startMillis,plan,customDays)
    fun archiveMember(memberId:Long)=viewModelScope.launch{repo.archiveMember(memberId)}
    fun restoreMember(memberId:Long,onDone:()->Unit={})=viewModelScope.launch{repo.restoreMember(memberId);onDone()}
    fun recordPayment(
        member: Member,
        amount: Double,
        method: PaymentMethod,
        note: String = "",
        recordedBy: String = "",
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            repo.recordPayment(member, amount, method, note, recordedBy)
            onDone()
        } catch (e: Exception) {
            onError(e.message ?: "Unable to record payment.")
        }
    }
    suspend fun getMember(id:Long)=repo.getMember(id)
    fun paymentsForMember(id:Long)=repo.paymentsForMember(id)
    fun paymentsForDate(dateMillis:Long)=repo.paymentsForDate(dateMillis)
    fun measurementsForMember(id:Long)=repo.measurementsForMember(id)
    fun addMeasurement(item:MeasurementRecord)=viewModelScope.launch{repo.addMeasurement(item)}

    fun addBranch(name:String,place:String="",onDone:(Long)->Unit={})=viewModelScope.launch{val id=repo.addBranch(Branch(name=name.trim(),place=place.trim()));onDone(id)}
    fun updateBranch(branch:Branch)=viewModelScope.launch{repo.updateBranch(branch)}
    fun deleteBranch(branch:Branch,onResult:(Boolean)->Unit={})=viewModelScope.launch{onResult(repo.deleteBranch(branch))}

    private val trainerCloudRepo=TrainerCloudRepository()
    val trainerAccounts:StateFlow<List<TrainerAccount>> = trainerCloudRepo.observeTrainers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun configureTrainerCloud(gymId:String){trainerCloudRepo.configure(gymId)}
    fun addTrainerAccount(name:String,username:String,password:String,branchId:Long?,onDone:()->Unit={},onError:(String)->Unit={})=viewModelScope.launch{runCatching{val b=branches.value.firstOrNull{it.id==branchId}?:error("Select a branch.");trainerCloudRepo.addTrainer(name,username,password,b)}.onSuccess{onDone()}.onFailure{onError(it.message?:"Unable to add trainer.")}}
    fun updateTrainerAccount(t:TrainerAccount,name:String,branchId:Long?,onDone:()->Unit={},onError:(String)->Unit={})=viewModelScope.launch{runCatching{val b=branches.value.firstOrNull{it.id==branchId}?:error("Select a branch.");trainerCloudRepo.updateTrainer(t,name,b)}.onSuccess{onDone()}.onFailure{onError(it.message?:"Unable to update trainer.")}}
    fun changeTrainerPassword(t:TrainerAccount,password:String,onDone:()->Unit={},onError:(String)->Unit={})=viewModelScope.launch{runCatching{trainerCloudRepo.setTrainerPassword(t.id,password)}.onSuccess{onDone()}.onFailure{onError(it.message?:"Unable to change trainer password.")}}
    fun removeTrainerAccount(t:TrainerAccount,onDone:()->Unit={},onError:(String)->Unit={})=viewModelScope.launch{runCatching{trainerCloudRepo.removeTrainer(t)}.onSuccess{onDone()}.onFailure{onError(it.message?:"Unable to delete trainer.")}}
    fun deactivateTrainerAccount(t:TrainerAccount,onDone:()->Unit={},onError:(String)->Unit={})=viewModelScope.launch{runCatching{trainerCloudRepo.deactivateTrainer(t)}.onSuccess{onDone()}.onFailure{onError(it.message?:"Unable to deactivate trainer.")}}
}
