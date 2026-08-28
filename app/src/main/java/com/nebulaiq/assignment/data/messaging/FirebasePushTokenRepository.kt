package com.nebulaiq.assignment.data.messaging

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.nebulaiq.assignment.domain.repository.PushTokenRepository
import kotlinx.coroutines.tasks.await

class FirebasePushTokenRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) : PushTokenRepository {
    override suspend fun registerCurrentUserToken(): Result<Unit> = runCatching {
        registerToken(messaging.token.await()).getOrThrow()
    }.onFailure { error ->
        Log.e(TAG, "Could not register current FCM token", error)
    }

    override suspend fun registerToken(token: String): Result<Unit> = runCatching {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("No signed-in user is available")
        check(token.isNotBlank()) { "FCM token is empty" }

        val group = firestore.collection(GROUPS_COLLECTION)
            .whereArrayContains("memberIds", userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: error("No active group is available for this user")

        firestore.collection(GROUPS_COLLECTION)
            .document(group.id)
            .collection(DEVICE_TOKENS_COLLECTION)
            .document(userId)
            .set(
                mapOf(
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
        Unit
    }.onFailure { error ->
        Log.e(TAG, "Could not register FCM token", error)
    }

    private companion object {
        const val TAG = "CircleGuardMessaging"
        const val GROUPS_COLLECTION = "groups"
        const val DEVICE_TOKENS_COLLECTION = "deviceTokens"
    }
}
