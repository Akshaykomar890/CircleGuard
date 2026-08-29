package com.nebulaiq.assignment.data.group

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nebulaiq.assignment.domain.model.GeoPoint
import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.repository.GroupRepository
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class FirebaseGroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : GroupRepository {
    override suspend fun findGroupForUser(userId: String): Result<Group?> = runCatching {
        firestore.collection(GROUPS_COLLECTION)
            .whereArrayContains("memberIds", userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.let { document ->
                val memberIds = (document.get("memberIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                document.toGroup(
                    invitationCode = document.getString("invitationCode").orEmpty(),
                    memberIds = memberIds,
                )
            }
    }.onFailure { error ->
        Log.e(TAG, "Find user's group failed", error)
    }

    override suspend fun updateMemberDisplayName(
        groupId: String,
        userId: String,
        displayName: String,
    ): Result<Unit> = runCatching {
        firestore.collection(GROUPS_COLLECTION)
            .document(groupId)
            .update("members.$userId.displayName", displayName.trim())
            .await()
        Unit
    }.onFailure { error ->
        Log.e(TAG, "Update member display name failed", error)
    }

    override suspend fun createGroup(
        name: String,
        radiusMeters: Double,
        center: GeoPoint,
        userId: String,
        displayName: String,
    ): Result<Group> = runCatching {
        // Six characters from a 32-symbol alphabet gives a large collision-resistant space.
        // The document ID is the invitation code, so Firestore stores one group per code.
        val invitationCode = generateInvitationCode()
        val groupReference = firestore.collection(GROUPS_COLLECTION).document(invitationCode)
        val groupData = mapOf(
            "name" to name.trim(),
            "invitationCode" to invitationCode,
            "creatorId" to userId,
            "memberIds" to listOf(userId),
            "members" to mapOf(userId to mapOf("displayName" to displayName)),
            "radiusMeters" to radiusMeters,
            "centerLatitude" to center.latitude,
            "centerLongitude" to center.longitude,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        groupReference.set(groupData).await()
        Group(
            id = invitationCode,
            name = name.trim(),
            invitationCode = invitationCode,
            creatorId = userId,
            memberIds = listOf(userId),
            radiusMeters = radiusMeters,
            centerLatitude = center.latitude,
            centerLongitude = center.longitude,
        )
    }.onFailure { error ->
        Log.e(TAG, "Create group failed", error)
    }

    override suspend fun joinGroup(
        invitationCode: String,
        userId: String,
        displayName: String,
    ): Result<Group> = runCatching {
        val normalizedCode = invitationCode.trim().uppercase()
        val groupReference = firestore.collection(GROUPS_COLLECTION).document(normalizedCode)
        val snapshot = groupReference.get().await()
        check(snapshot.exists()) { "Invalid or expired invitation code" }

        val existingMemberIds = snapshot.get("memberIds") as? List<*> ?: emptyList<Any>()
        if (userId !in existingMemberIds.filterIsInstance<String>()) {
            groupReference.update(
                "memberIds",
                FieldValue.arrayUnion(userId),
                "members.$userId",
                mapOf("displayName" to displayName),
            ).await()
        }

        snapshot.toGroup(normalizedCode, existingMemberIds.filterIsInstance<String>() + userId)
    }.onFailure { error ->
        Log.e(TAG, "Join group failed", error)
    }

    private fun generateInvitationCode(): String = buildString {
        repeat(INVITATION_CODE_LENGTH) {
            append(INVITATION_ALPHABET[Random.nextInt(INVITATION_ALPHABET.length)])
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toGroup(
        invitationCode: String,
        memberIds: List<String>,
    ): Group = Group(
        id = id,
        name = getString("name").orEmpty(),
        invitationCode = invitationCode,
        creatorId = getString("creatorId").orEmpty(),
        memberIds = memberIds.distinct(),
        radiusMeters = getDouble("radiusMeters") ?: 200.0,
        centerLatitude = getDouble("centerLatitude"),
        centerLongitude = getDouble("centerLongitude"),
    )

    private companion object {
        const val TAG = "CircleGuardGroup"
        const val GROUPS_COLLECTION = "groups"
        const val INVITATION_CODE_LENGTH = 6
        const val INVITATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
