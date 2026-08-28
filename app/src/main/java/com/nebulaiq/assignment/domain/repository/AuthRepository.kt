package com.nebulaiq.assignment.domain.repository

interface AuthRepository {
    fun currentUserId(): String?

    suspend fun signInAnonymously(): Result<String>
}
