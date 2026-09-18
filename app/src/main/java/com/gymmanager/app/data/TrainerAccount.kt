package com.gymmanager.app.data

data class TrainerAccount(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val branchId: Long? = null,
    val branchRemoteId: String? = null,
    val active: Boolean = true,
    val phone: String = ""
)
