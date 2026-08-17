package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateChildRequest(
    val name: String,
    val birthYear: Int? = null,
)

@Serializable
data class ChildResponse(
    val id: String,
    val parentId: String,
    val name: String,
    val birthYear: Int?,
    val configVersion: Long,
    val status: String,
    val createdAt: String,
)
