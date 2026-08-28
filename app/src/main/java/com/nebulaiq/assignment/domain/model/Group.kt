package com.nebulaiq.assignment.domain.model

data class Group(
    val id: String,
    val name: String,
    val invitationCode: String,
    val creatorId: String,
    val memberIds: List<String>,
    val radiusMeters: Double,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
)
