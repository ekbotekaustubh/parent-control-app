package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody,
)
