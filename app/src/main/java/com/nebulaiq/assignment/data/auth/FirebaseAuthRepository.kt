package com.nebulaiq.assignment.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.nebulaiq.assignment.domain.repository.AuthRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class FirebaseAuthRepository : AuthRepository {
    override fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun currentUserDisplayName(): String? = FirebaseAuth.getInstance().currentUser?.displayName

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

    override suspend fun updateDisplayName(displayName: String): Result<Unit> = runCatching {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Anonymous user is not signed in")
        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build(),
        ).await()
    }
}
