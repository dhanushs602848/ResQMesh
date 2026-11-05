package com.resqmesh.app.data.location

import android.content.Context
import android.content.ContextWrapper
import android.location.LocationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationTrackerTest {

    private lateinit var tracker: DefaultLocationTracker
    private lateinit var fakeHardwareAdapter: FakeLocationHardwareAdapter
    private var mockFinePermission = true
    private var mockCoarsePermission = true
    private var currentTime = 1700000000000L

    class FakeLocationHardwareAdapter : LocationHardwareAdapter {
        var available = true
        var gpsEnabled = true
        var networkEnabled = true
        var lastKnownGpsFix: LocationFix? = null
        var lastKnownNetworkFix: LocationFix? = null
        var onLocationCallback: ((LocationFix) -> Unit)? = null
        var onProviderStateCallback: (() -> Unit)? = null

        override fun isAvailable(): Boolean = available

        override fun isProviderEnabled(provider: String): Boolean =
            if (provider == LocationManager.GPS_PROVIDER) gpsEnabled else networkEnabled

        override fun isLocationEnabled(): Boolean = gpsEnabled || networkEnabled

        override fun getLastKnownLocation(provider: String): LocationFix? =
            if (provider == LocationManager.GPS_PROVIDER) lastKnownGpsFix else lastKnownNetworkFix

        override fun requestLocationUpdates(
            provider: String,
            minTimeMs: Long,
            minDistanceM: Float,
            onLocationChanged: (LocationFix) -> Unit,
            onProviderStateChanged: () -> Unit
        ) {
            onLocationCallback = onLocationChanged
            onProviderStateCallback = onProviderStateChanged
        }

        override fun removeLocationUpdates() {
            onLocationCallback = null
            onProviderStateCallback = null
        }
    }

    @Before
    fun setUp() {
        mockFinePermission = true
        mockCoarsePermission = true
        currentTime = 1700000000000L

        fakeHardwareAdapter = FakeLocationHardwareAdapter()

        val mockContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
            override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?): android.content.Intent? = null
            override fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) {}
        }

        tracker = DefaultLocationTracker(mockContext)
        tracker.hardwareAdapter = fakeHardwareAdapter
        tracker.currentTimeProvider = { currentTime }
        tracker.permissionChecker = { _, perm ->
            when (perm) {
                android.Manifest.permission.ACCESS_FINE_LOCATION -> mockFinePermission
                android.Manifest.permission.ACCESS_COARSE_LOCATION -> mockCoarsePermission
                else -> false
            }
        }
    }

    @Test
    fun initialState_isSearchingWithoutFabricatedCoordinates() = runBlocking {
        val state = tracker.locationState.first()
        assertEquals(LocationStatus.SEARCHING, state.status)
        assertNull("Initial location must be null; never fabricate coordinates", state.coordinate)
        assertNull(state.accuracyMeters)
        assertFalse(state.isFixed)
        assertNull("getCurrentCoordinate must return null while searching", tracker.getCurrentCoordinate())
    }

    @Test
    fun validGpsFix_transitionsToFixedWithAccuracyAndTimestamp() = runBlocking {
        val testFix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 4.2f,
            altitude = 910.0,
            timestamp = currentTime,
            provider = "gps"
        )

        tracker.handleLocationUpdate(testFix)

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.FIXED, state.status)
        assertTrue(state.isFixed)
        assertNotNull(state.coordinate)
        assertEquals(12.9716, state.coordinate!!.latitude, 0.0001)
        assertEquals(77.5946, state.coordinate!!.longitude, 0.0001)
        assertEquals(4.2f, state.accuracyMeters!!, 0.01f)
        assertEquals(910.0, state.coordinate!!.altitudeMeters!!, 0.1)
        assertEquals(currentTime, state.timestamp)
        assertEquals("gps", state.provider)

        val currentCoord = tracker.getCurrentCoordinate()
        assertNotNull(currentCoord)
        assertEquals(12.9716, currentCoord!!.latitude, 0.0001)
    }

    @Test
    fun corruptedOrInvalidCoordinates_areRejected() = runBlocking {
        // Null Island default (0.0, 0.0) from uninitialized chip
        tracker.handleLocationUpdate(LocationFix(latitude = 0.0, longitude = 0.0))
        assertEquals(LocationStatus.SEARCHING, tracker.locationState.value.status)
        assertNull(tracker.locationState.value.coordinate)

        // Invalid Latitude
        tracker.handleLocationUpdate(LocationFix(latitude = 95.0, longitude = 77.0))
        assertEquals(LocationStatus.SEARCHING, tracker.locationState.value.status)

        // Invalid Longitude
        tracker.handleLocationUpdate(LocationFix(latitude = 12.0, longitude = 185.0))
        assertEquals(LocationStatus.SEARCHING, tracker.locationState.value.status)
    }

    @Test
    fun permissionDenied_transitionsToPermissionDenied() = runBlocking {
        mockFinePermission = false
        mockCoarsePermission = false

        tracker.evaluateAndRegisterUpdates()

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.PERMISSION_DENIED, state.status)
        assertNull(state.coordinate)
        assertNull(tracker.getCurrentCoordinate())
    }

    @Test
    fun locationDisabled_transitionsToLocationDisabled() = runBlocking {
        mockFinePermission = true
        fakeHardwareAdapter.gpsEnabled = false
        fakeHardwareAdapter.networkEnabled = false

        tracker.evaluateAndRegisterUpdates()

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.LOCATION_DISABLED, state.status)
        assertNull(state.coordinate)
    }

    @Test
    fun locationUnavailable_transitionsToUnavailable() = runBlocking {
        fakeHardwareAdapter.available = false

        tracker.evaluateAndRegisterUpdates()

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.UNAVAILABLE, state.status)
    }

    @Test
    fun staleFix_detectedWhenFixExceedsStaleThreshold() = runBlocking {
        // Step 1: Acquire valid fix at T0
        val fixTime = currentTime
        tracker.handleLocationUpdate(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracy = 5.0f, timestamp = fixTime)
        )
        assertEquals(LocationStatus.FIXED, tracker.locationState.value.status)

        // Step 2: Advance time by 6 minutes (> 5 min threshold)
        currentTime += 6 * 60 * 1000L
        tracker.checkStaleness()

        val staleState = tracker.locationState.first()
        assertEquals(LocationStatus.STALE, staleState.status)
        assertNotNull(staleState.coordinate)
        assertEquals(12.9716, staleState.coordinate!!.latitude, 0.0001)

        // In STALE state, coordinate remains accessible as best-effort for rescue
        assertNotNull(tracker.getCurrentCoordinate())
    }

    @Test
    fun lastKnownLocation_freshFix_transitionsToFixed() = runBlocking {
        fakeHardwareAdapter.lastKnownGpsFix = LocationFix(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracy = 10.0f,
            timestamp = currentTime - 60_000L // 1 minute old (< 5 min)
        )

        tracker.evaluateAndRegisterUpdates()

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.FIXED, state.status)
        assertEquals(13.0827, state.coordinate!!.latitude, 0.0001)
        assertEquals(80.2707, state.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun lastKnownLocation_staleFix_transitionsToStale() = runBlocking {
        fakeHardwareAdapter.lastKnownGpsFix = LocationFix(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracy = 15.0f,
            timestamp = currentTime - 10 * 60 * 1000L // 10 minutes old (> 5 min)
        )

        tracker.evaluateAndRegisterUpdates()

        val state = tracker.locationState.first()
        assertEquals(LocationStatus.STALE, state.status)
        assertEquals(13.0827, state.coordinate!!.latitude, 0.0001)
    }
}
