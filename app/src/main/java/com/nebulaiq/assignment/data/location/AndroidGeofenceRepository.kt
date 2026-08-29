package com.nebulaiq.assignment.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.repository.GeofenceRepository
import kotlinx.coroutines.tasks.await

class AndroidGeofenceRepository(
    context: Context,
) : GeofenceRepository {
    private val appContext = context.applicationContext
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(appContext)
    private val trackingPreferences = appContext.getSharedPreferences(
        TRACKING_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }
    }

    override fun isTrackingRegistered(groupId: String): Boolean =
        trackingPreferences.getString(TRACKING_GROUP_ID, null) == groupId

    @SuppressLint("MissingPermission")
    override suspend fun register(group: Group): Result<Unit> = runCatching {
        check(hasLocationPermission()) {
            "Precise location permission is required to enable tracking"
        }
        check(isLocationEnabled()) {
            "Turn on device location services before enabling tracking"
        }
        val latitude = group.centerLatitude
            ?: error("This group does not have a saved boundary center")
        val longitude = group.centerLongitude
            ?: error("This group does not have a saved boundary center")

        val geofence = Geofence.Builder()
            .setRequestId(group.id)
            .setCircularRegion(latitude, longitude, group.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.removeGeofences(geofencePendingIntent).await()
        geofencingClient.addGeofences(request, geofencePendingIntent).await()
        trackingPreferences.edit().putString(TRACKING_GROUP_ID, group.id).apply()
    }

    override suspend fun unregister(): Result<Unit> = runCatching {
        geofencingClient.removeGeofences(geofencePendingIntent).await()
        trackingPreferences.edit().remove(TRACKING_GROUP_ID).apply()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag(),
        )
    }

    private fun mutablePendingIntentFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

    private companion object {
        // New request code prevents an older immutable PendingIntent from being reused.
        const val GEOFENCE_REQUEST_CODE = 7002
        const val TRACKING_PREFERENCES = "circleguard_tracking"
        const val TRACKING_GROUP_ID = "tracking_group_id"
    }
}
