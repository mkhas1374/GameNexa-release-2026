package com.example.data

data class PaymentCard(
    val id: String,
    val ownerName: String,
    val cardNumber: String,
    val isActive: Boolean = true
)

data class PaymentGateway(
    val id: String,
    val name: String,
    val url: String,
    val isActive: Boolean = true
)

data class PaymentCrypto(
    val id: String,
    val name: String,
    val network: String,
    val address: String,
    val isActive: Boolean = true
)
