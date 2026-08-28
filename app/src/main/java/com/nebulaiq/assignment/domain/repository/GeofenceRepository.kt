package com.nebulaiq.assignment.domain.repository

import com.nebulaiq.assignment.domain.model.Group

interface GeofenceRepository {
    fun isLocationEnabled(): Boolean

    suspend fun register(group: Group): Result<Unit>

    suspend fun unregister(): Result<Unit>
}
