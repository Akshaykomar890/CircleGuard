package com.nebulaiq.assignment.domain.repository

interface PushTokenRepository {
    suspend fun registerCurrentUserToken(): Result<Unit>

    suspend fun registerToken(token: String): Result<Unit>
}
