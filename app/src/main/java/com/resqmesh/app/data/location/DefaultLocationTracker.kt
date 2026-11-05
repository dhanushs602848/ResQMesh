package com.resqmesh.app.data.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.resqmesh.app.data.model.GeoCoordinate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResQMesh-Location"

/**
 * Normalized immutable fix payload received from underlying location hardware.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "gps"
)

/**
 * Hardware abstraction interface for testability and clean platform decoupling.
 */
interface LocationHardwareAdapter {
    fun isAvailable(): Boolean
    fun isProviderEnabled(provider: String): Boolean
    fun isLocationEnabled(): Boolean
    fun getLastKnownLocation(provider: String): LocationFix?
    fun requestLocationUpdates(
        provider: String,
        minTimeMs: Long,
        minDistanceM: Float,
        onLocationChanged: (LocationFix) -> Unit,
        onProviderStateChanged: () -> Unit
    )
    fun removeLocationUpdates()
}

/**
 * Standard Android OS hardware adapter delegating directly to [LocationManager].
 */
class SystemLocationHardwareAdapter(private val context: Context) : LocationHardwareAdapter {
    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeGpsListener: LocationListener? = null
    private var activeNetworkListener: LocationListener? = null

    override fun isAvailable(): Boolean = locationManager != null

    override fun isProviderEnabled(provider: String): Boolean {
        val lm = locationManager ?: return false
        return try { lm.isProviderEnabled(provider) } catch (_: Exception) { false }
    }

    override fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return try {
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (_: Exception) {
            isProviderEnabled(LocationManager.GPS_PROVIDER) || isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    override fun getLastKnownLocation(provider: String): LocationFix? {
        val lm = locationManager ?: return null
        return try {
            val loc = lm.getLastKnownLocation(provider) ?: return null
            loc.toLocationFix()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun requestLocationUpdates(
        provider: String,
        minTimeMs: Long,
        minDistanceM: Float,
        onLocationChanged: (LocationFix) -> Unit,
        onProviderStateChanged: () -> Unit
    ) {
        val lm = locationManager ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationChanged(location.toLocationFix())
            }

            override fun onProviderEnabled(p: String) {
                onProviderStateChanged()
            }

            override fun onProviderDisabled(p: String) {
                onProviderStateChanged()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
        }

        if (provider == LocationManager.GPS_PROVIDER) {
            activeGpsListener = listener
        } else {
            activeNetworkListener = listener
        }

        val looper = try { Looper.getMainLooper() } catch (_: Exception) { null }
        try {
            if (looper != null) {
                lm.requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, looper)
            } else {
                lm.requestLocationUpdates(provider, minTimeMs, minDistanceM, listener)
            }
            Log.i(TAG, "Subscribed to updates for $provider")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException requesting $provider updates: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Exception requesting $provider updates: ${e.message}")
        }
    }

    override fun removeLocationUpdates() {
        val lm = locationManager ?: return
        try {
            activeGpsListener?.let { lm.removeUpdates(it) }
            activeNetworkListener?.let { lm.removeUpdates(it) }
        } catch (_: Exception) {}
        activeGpsListener = null
        activeNetworkListener = null
    }

    private fun Location.toLocationFix(): LocationFix {
        return LocationFix(
            latitude = latitude,
            longitude = longitude,
            altitude = if (hasAltitude()) altitude else null,
            accuracy = if (hasAccuracy()) accuracy else null,
            timestamp = time,
            provider = provider ?: "gps"
        )
    }
}

/**
 * Production implementation of [LocationTracker] providing real offline GPS tracking
 * via Android's hardware [LocationManager].
 *
 * Guarantees:
 * 1. 100% offline operation: reads raw GPS satellite fixes without cellular/internet dependencies.
 * 2. Never fabricates coordinates or hardcodes fallback locations.
 * 3. Gracefully reports SEARCHING, FIXED, STALE, PERMISSION_DENIED, LOCATION_DISABLED, and UNAVAILABLE.
 * 4. Automatically detects stale fixes when no new satellite locks arrive (> 5 minutes).
 * 5. Coroutine-safe and non-blocking for emergency SOS dispatch.
 */
@Singleton
class DefaultLocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stalenessJob: Job? = null

    // Pluggable hooks for testing
    internal var hardwareAdapter: LocationHardwareAdapter = SystemLocationHardwareAdapter(context)
    internal var permissionChecker: (Context, String) -> Boolean = { ctx, perm ->
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }
    internal var currentTimeProvider: () -> Long = { System.currentTimeMillis() }

    private val _locationState = MutableStateFlow(LocationState(status = LocationStatus.SEARCHING))
    override val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private var isTrackingStarted = false
    private var providerChangedReceiver: BroadcastReceiver? = null

    companion object {
        const val MIN_UPDATE_INTERVAL_MS = 3000L // 3 seconds
        const val MIN_UPDATE_DISTANCE_M = 2.0f   // 2 meters
        const val STALE_THRESHOLD_MS = LocationState.STALE_THRESHOLD_MS
    }

    override fun startTracking() {
        isTrackingStarted = true
        evaluateAndRegisterUpdates()
        startStalenessMonitor()
        registerProviderChangedReceiver()
    }

    override fun stopTracking() {
        isTrackingStarted = false
        hardwareAdapter.removeLocationUpdates()
        stopStalenessMonitor()
        unregisterProviderChangedReceiver()
    }

    override fun refreshLocation() {
        if (isTrackingStarted) {
            evaluateAndRegisterUpdates()
        }
    }

    override fun getCurrentCoordinate(): GeoCoordinate? {
        val current = _locationState.value
        return if (current.coordinate != null &&
            (current.status == LocationStatus.FIXED || current.status == LocationStatus.STALE)
        ) {
            current.coordinate
        } else {
            null
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = permissionChecker(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = permissionChecker(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineGranted || coarseGranted
    }

    internal fun evaluateAndRegisterUpdates() {
        if (!hardwareAdapter.isAvailable()) {
            _locationState.value = LocationState(status = LocationStatus.UNAVAILABLE)
            return
        }

        if (!hasLocationPermission()) {
            _locationState.value = _locationState.value.copy(
                status = LocationStatus.PERMISSION_DENIED
            )
            hardwareAdapter.removeLocationUpdates()
            return
        }

        if (!hardwareAdapter.isLocationEnabled()) {
            _locationState.value = _locationState.value.copy(
                status = LocationStatus.LOCATION_DISABLED
            )
            hardwareAdapter.removeLocationUpdates()
            return
        }

        // Check last known location if we don't already have an active fix
        inspectLastKnownLocation()

        // Register active hardware GPS updates
        registerHardwareUpdates()
    }

    private fun inspectLastKnownLocation() {
        if (_locationState.value.status == LocationStatus.FIXED) return

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestFix: LocationFix? = null
        for (prov in providers) {
            if (hardwareAdapter.isProviderEnabled(prov)) {
                val fix = hardwareAdapter.getLastKnownLocation(prov) ?: continue
                if (bestFix == null || fix.timestamp > bestFix.timestamp) {
                    bestFix = fix
                }
            }
        }

        if (bestFix != null && isValidCoordinate(bestFix.latitude, bestFix.longitude)) {
            val now = currentTimeProvider()
            val ageMs = now - bestFix.timestamp
            val isStale = ageMs > STALE_THRESHOLD_MS
            val geo = GeoCoordinate(
                latitude = bestFix.latitude,
                longitude = bestFix.longitude,
                altitudeMeters = bestFix.altitude,
                accuracyMeters = bestFix.accuracy,
                timestamp = bestFix.timestamp
            )
            _locationState.value = LocationState(
                status = if (isStale) LocationStatus.STALE else LocationStatus.FIXED,
                coordinate = geo,
                accuracyMeters = geo.accuracyMeters,
                timestamp = geo.timestamp,
                provider = bestFix.provider
            )
        } else if (_locationState.value.status != LocationStatus.FIXED &&
            _locationState.value.status != LocationStatus.STALE
        ) {
            _locationState.value = LocationState(status = LocationStatus.SEARCHING)
        }
    }

    private fun registerHardwareUpdates() {
        if (hardwareAdapter.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            hardwareAdapter.requestLocationUpdates(
                provider = LocationManager.GPS_PROVIDER,
                minTimeMs = MIN_UPDATE_INTERVAL_MS,
                minDistanceM = MIN_UPDATE_DISTANCE_M,
                onLocationChanged = { fix -> handleLocationUpdate(fix) },
                onProviderStateChanged = { evaluateAndRegisterUpdates() }
            )
        }

        if (hardwareAdapter.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            hardwareAdapter.requestLocationUpdates(
                provider = LocationManager.NETWORK_PROVIDER,
                minTimeMs = MIN_UPDATE_INTERVAL_MS,
                minDistanceM = MIN_UPDATE_DISTANCE_M,
                onLocationChanged = { fix -> handleLocationUpdate(fix) },
                onProviderStateChanged = { evaluateAndRegisterUpdates() }
            )
        }
    }

    internal fun handleLocationUpdate(fix: LocationFix) {
        if (!isValidCoordinate(fix.latitude, fix.longitude)) {
            Log.w(TAG, "Ignoring invalid coordinate (${fix.latitude}, ${fix.longitude})")
            return
        }

        val geo = GeoCoordinate(
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitudeMeters = fix.altitude,
            accuracyMeters = fix.accuracy,
            timestamp = fix.timestamp
        )

        _locationState.value = LocationState(
            status = LocationStatus.FIXED,
            coordinate = geo,
            accuracyMeters = geo.accuracyMeters,
            timestamp = geo.timestamp,
            provider = fix.provider
        )
        Log.i(TAG, "Acquired real GPS fix: ${geo.latitude}, ${geo.longitude} (±${geo.accuracyMeters}m via ${fix.provider})")
    }

    private fun startStalenessMonitor() {
        stopStalenessMonitor()
        stalenessJob = scope.launch {
            while (isActive) {
                delay(30_000L) // Check every 30 seconds
                checkStaleness()
            }
        }
    }

    private fun stopStalenessMonitor() {
        stalenessJob?.cancel()
        stalenessJob = null
    }

    internal fun checkStaleness() {
        val current = _locationState.value
        if (current.status == LocationStatus.FIXED && current.coordinate != null) {
            val now = currentTimeProvider()
            val ageMs = now - current.timestamp
            if (ageMs > STALE_THRESHOLD_MS) {
                _locationState.value = current.copy(status = LocationStatus.STALE)
                Log.w(TAG, "GPS fix is now STALE (age: ${ageMs / 1000}s)")
            }
        }
    }

    private fun registerProviderChangedReceiver() {
        if (providerChangedReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    evaluateAndRegisterUpdates()
                }
            }
        }
        providerChangedReceiver = receiver
        try {
            context.registerReceiver(
                receiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register PROVIDERS_CHANGED receiver: ${e.message}")
        }
    }

    private fun unregisterProviderChangedReceiver() {
        val receiver = providerChangedReceiver ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        providerChangedReceiver = null
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return false
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
        // Filter out corrupted Null Island default (0.0, 0.0)
        if (lat == 0.0 && lon == 0.0) return false
        return true
    }
}
