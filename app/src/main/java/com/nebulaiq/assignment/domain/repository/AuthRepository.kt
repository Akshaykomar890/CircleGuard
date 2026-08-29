package com.nebulaiq.assignment.domain.repository

interface AuthRepository {
    fun currentUserId(): String?

    fun currentUserDisplayName(): String?

    suspend fun signInAnonymously(): Result<String>

    suspend fun updateDisplayName(displayName: String): Result<Unit>
}
