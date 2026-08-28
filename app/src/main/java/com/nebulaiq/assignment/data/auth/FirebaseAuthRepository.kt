package com.nebulaiq.assignment.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.nebulaiq.assignment.domain.repository.AuthRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository : AuthRepository {
    override fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    override suspend fun signInAnonymously(): Result<String> =
        suspendCancellableCoroutine { continuation ->
            try {
                val auth = FirebaseAuth.getInstance()
                auth.currentUser?.uid?.let { existingUserId ->
                    continuation.resume(Result.success(existingUserId))
                    return@suspendCancellableCoroutine
                }
                auth
                    .signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = task.result?.user?.uid
                            if (userId.isNullOrBlank()) {
                                continuation.resume(
                                    Result.failure(IllegalStateException("Anonymous user ID is missing")),
                                )
                            } else {
                                continuation.resume(Result.success(userId))
                            }
                        } else {
                            continuation.resume(
                                Result.failure(
                                    task.exception ?: IllegalStateException("Anonymous sign-in failed"),
                                ),
                            )
                        }
                    }
            } catch (error: Throwable) {
                continuation.resume(Result.failure(error))
            }
        }
}
