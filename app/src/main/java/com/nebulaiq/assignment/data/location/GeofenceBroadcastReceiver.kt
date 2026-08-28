package com.nebulaiq.assignment.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.nebulaiq.assignment.data.messaging.ExitEventWorker
import java.util.UUID

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence event failed: ${event.errorCode}")
            return
        }

        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "entered"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "exited"
            else -> "unknown transition"
        }
        val groupIds = event.triggeringGeofences.orEmpty().map(Geofence::getRequestId)
        Log.d(TAG, "Device $transition geofence(s): $groupIds")

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            groupIds.forEach { groupId ->
                val eventId = UUID.randomUUID().toString()
                val workRequest = OneTimeWorkRequestBuilder<ExitEventWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setInputData(
                        workDataOf(
                            "groupId" to groupId,
                            "eventId" to eventId,
                        ),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "geofence-exit-$eventId",
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
        }
    }

    private companion object {
        const val TAG = "CircleGuardGeofence"
    }
}
