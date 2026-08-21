package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val isAvailable: Boolean = true
)

@Serializable
data class CreateBookRequest(
    val title: String,
    val author: String
)

@Serializable
data class UpdateBookRequest(
    val title: String? = null,
    val author: String? = null,
    val isAvailable: Boolean? = null
)

@Serializable
data class MessageResponse(
    val message: String
)
