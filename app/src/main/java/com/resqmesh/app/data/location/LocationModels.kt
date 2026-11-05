package com.resqmesh.app.data.location

import com.resqmesh.app.data.model.GeoCoordinate

/**
 * Tactical hardware and provider state of device geolocation.
 */
enum class LocationStatus {
    /**
     * GPS receiver is actively acquiring satellite lock or awaiting position fix.
     */
    SEARCHING,

    /**
     * Valid, high-precision GPS lock obtained within the valid freshness threshold.
     */
    FIXED,

    /**
     * Last known fix is older than the staleness threshold (> 5 minutes).
     */
    STALE,

    /**
     * Runtime location permission (FINE or COARSE) has not been granted by user.
     */
    PERMISSION_DENIED,

    /**
     * Device location services (GPS hardware switch) are disabled in system settings.
     */
    LOCATION_DISABLED,

    /**
     * Location hardware or Android LocationManager service is unavailable.
     */
    UNAVAILABLE
}

/**
 * Observable device location representation containing current status, coordinate,
 * precision bounds, provider telemetry, and fix age.
 */
data class LocationState(
    val status: LocationStatus = LocationStatus.SEARCHING,
    val coordinate: GeoCoordinate? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long = 0L,
    val provider: String? = null
) {
    val isFixed: Boolean get() = status == LocationStatus.FIXED && coordinate != null
    val hasCoordinate: Boolean get() = coordinate != null

    companion object {
        const val STALE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }
}
