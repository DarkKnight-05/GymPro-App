package com.gymmanager.app.data

import java.util.UUID

data class Branch(
    val id: Long = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val name: String,
    val place: String = ""
)
