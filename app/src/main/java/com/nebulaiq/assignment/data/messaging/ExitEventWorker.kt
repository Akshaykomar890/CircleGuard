package com.nebulaiq.assignment.data.messaging

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nebulaiq.assignment.BuildConfig
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ExitEventWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val workerUrl = BuildConfig.CIRCLEGUARD_WORKER_URL.trimEnd('/')
        val groupId = inputData.getString(KEY_GROUP_ID)
        val eventId = inputData.getString(KEY_EVENT_ID)
        if (workerUrl.isBlank() || groupId.isNullOrBlank() || eventId.isNullOrBlank()) {
            Log.w(TAG, "Exit event is waiting for Worker configuration")
            return Result.failure()
        }

        val idToken = FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return Result.retry()
        val payload = """{"eventId":"$eventId","groupId":"$groupId","occurredAt":"${System.currentTimeMillis()}"}"""
        val request = Request.Builder()
            .url("$workerUrl/v1/geofence-exit")
            .header("Authorization", "Bearer $idToken")
            .header("Accept", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "Worker response status=${response.code} body=$responseBody")
                when {
                    response.isSuccessful -> Result.success()
                    response.code == 408 || response.code == 429 || response.code >= 500 -> Result.retry()
                    else -> Result.failure()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Exit event upload failed", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "CircleGuardExitWorker"
        const val KEY_GROUP_ID = "groupId"
        const val KEY_EVENT_ID = "eventId"
    }
}
