package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class LendingRecord(
    val id: String,
    val bookId: String,
    val borrowerName: String,
    val checkoutDate: String,
    val returnDate: String? = null
)

@Serializable
data class BorrowRequest(
    val borrowerName: String
)

@Serializable
data class BorrowByBookIdRequest(
    val bookId: String,
    val borrowerName: String
)

@Serializable
data class ReturnRequest(
    val bookId: String
)
