package com.gymmanager.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.app.data.CloudSyncRepository
import com.gymmanager.app.data.GymRepository

class ViewModelFactory(private val repo: GymRepository, private val cloud: CloudSyncRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
        if(modelClass.isAssignableFrom(MemberViewModel::class.java)) return MemberViewModel(repo,cloud) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
