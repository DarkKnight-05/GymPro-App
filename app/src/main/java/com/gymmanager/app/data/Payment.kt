package com.gymmanager.app.data


enum class PaymentMethod { CASH, GPAY }

data class Payment(
    val id: Long = 0,
    val remoteId: String? = null,
    val memberId: Long,
    val branchId: Long,
    val branchRemoteId: String? = null,
    val amount: Double,
    val paidOnMillis: Long,
    val periodCoveredMillis: Long,
    val method: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val recordedBy: String = ""
)
