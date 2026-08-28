package com.nebulaiq.assignment.domain.repository

import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.model.GeoPoint

interface GroupRepository {
    suspend fun findGroupForUser(userId: String): Result<Group?>

    suspend fun createGroup(
        name: String,
        radiusMeters: Double,
        center: GeoPoint,
        userId: String,
        displayName: String,
    ): Result<Group>

    suspend fun joinGroup(
        invitationCode: String,
        userId: String,
        displayName: String,
    ): Result<Group>
}
