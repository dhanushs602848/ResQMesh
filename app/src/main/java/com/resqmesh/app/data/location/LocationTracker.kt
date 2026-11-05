package com.resqmesh.app.data.location

import com.resqmesh.app.data.model.GeoCoordinate
import kotlinx.coroutines.flow.StateFlow

/**
 * Architectural contract for managing device GPS hardware, observing real-time location fixes,
 * and handling permission/provider lifecycle state transitions.
 */
interface LocationTracker {
    /**
     * Observable stream of current location state and hardware status.
     */
    val locationState: StateFlow<LocationState>

    /**
     * Initiates active GPS and network location tracking.
     */
    fun startTracking()

    /**
     * Stops active location listener updates to preserve battery.
     */
    fun stopTracking()

    /**
     * Re-evaluates permissions, provider states, and requests fresh fix.
     */
    fun refreshLocation()

    /**
     * Returns the current usable coordinate fix, or null if acquiring/unavailable.
     */
    fun getCurrentCoordinate(): GeoCoordinate?
}
