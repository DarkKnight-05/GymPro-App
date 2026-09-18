package com.gymmanager.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gymmanager.app.data.AuthSession
import com.gymmanager.app.data.CloudAuthRepository
import com.gymmanager.app.data.TrainerCloudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val cloudAuth: CloudAuthRepository, private val settings: SettingsStore, private val trainerRepo: TrainerCloudRepository) : ViewModel() {
    private val _currentRole = MutableStateFlow<Role?>(null); val currentRole: StateFlow<Role?> = _currentRole
    private val _trainerBranchRemoteId = MutableStateFlow<String?>(null); val trainerBranchRemoteId: StateFlow<String?> = _trainerBranchRemoteId
    private val _gymId = MutableStateFlow<String?>(null); val gymId: StateFlow<String?> = _gymId
    private val _email = MutableStateFlow<String?>(null); val email: StateFlow<String?> = _email
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }
    fun createAdmin(gymName:String,email:String,password:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{cloudAuth.createAdmin(gymName,email,password)}.onSuccess{apply(it);onResult(true)}.onFailure{_error.value=it.message?:"Unable to create the account.";onResult(false)}}
    fun loginAsAdmin(email:String,password:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{cloudAuth.loginAdmin(email,password)}.onSuccess{if(it!=null){apply(it);onResult(true)}else onResult(false)}.onFailure{_error.value=it.message?:"Login failed.";onResult(false)}}
    fun loginAsTrainer(username:String,password:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{trainerRepo.verifyTrainerLogin(username,password)}.onSuccess{trainer->if(trainer!=null){val session=cloudAuth.currentSession();if(session!=null){apply(session.copy(role="TRAINER",branchRemoteId=trainer.branchRemoteId));onResult(true)}else onResult(false)}else onResult(false)}.onFailure{_error.value=it.message?:"Login failed.";onResult(false)}}
    fun sendAdminPasswordReset(email:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{cloudAuth.sendPasswordReset(email)}.onSuccess{onResult(true)}.onFailure{_error.value=it.message?:"Unable to send reset email.";onResult(false)}}
    fun setAdminEmail(current:String,newEmail:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{cloudAuth.setAdminEmail(current,newEmail)}.onSuccess{_email.value=newEmail.trim();onResult(true)}.onFailure{_error.value=it.message?:"Unable to update admin email.";onResult(false)}}
    fun changeAdminPassword(current:String,newPassword:String,onResult:(Boolean)->Unit)=viewModelScope.launch{_error.value=null;runCatching{cloudAuth.changeOwnPassword(current,newPassword)}.onSuccess{onResult(true)}.onFailure{_error.value=it.message?:"Unable to change password.";onResult(false)}}
    private fun apply(session:AuthSession){_gymId.value=session.gymId;_trainerBranchRemoteId.value=session.branchRemoteId;_email.value=session.email;_currentRole.value=if(session.role=="ADMIN")Role.ADMIN else Role.TRAINER;trainerRepo.configure(session.gymId)}
    fun logout(){cloudAuth.signOut();_currentRole.value=null;_gymId.value=null;_trainerBranchRemoteId.value=null;_email.value=null}
}

class AuthViewModelFactory(private val cloudAuth:CloudAuthRepository,private val settings:SettingsStore,private val trainerRepo:TrainerCloudRepository):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T{if(modelClass.isAssignableFrom(AuthViewModel::class.java))return AuthViewModel(cloudAuth,settings,trainerRepo) as T;throw IllegalArgumentException("Unknown ViewModel class")}
}
