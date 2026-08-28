package com.nebulaiq.assignment.domain.repository

import com.nebulaiq.assignment.domain.model.GeoPoint

interface LocationRepository {
    suspend fun getCurrentLocation(): Result<GeoPoint>
}
