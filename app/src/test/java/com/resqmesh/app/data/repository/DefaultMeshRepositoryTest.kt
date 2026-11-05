package com.resqmesh.app.data.repository

import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultMeshRepositoryTest {

    private lateinit var repository: DefaultMeshRepository

    @Before
    fun setUp() {
        repository = DefaultMeshRepository()
    }

    @Test
    fun getDiscoveredNodes_returnsInitialNodes() = runBlocking {
        val nodes = repository.getDiscoveredNodes().first()
        assertTrue("Repository should provide initial mesh peers", nodes.isNotEmpty())
        assertEquals(3, nodes.size)
    }

    @Test
    fun getEmergencyBroadcasts_returnsInitialBroadcasts() = runBlocking {
        val broadcasts = repository.getEmergencyBroadcasts().first()
        assertTrue("Repository should have initial broadcasts", broadcasts.isNotEmpty())
        assertEquals(3, broadcasts.size)
    }

    @Test
    fun getSystemTelemetry_returnsHealthyDefaults() = runBlocking {
        val telemetry = repository.getSystemTelemetry().first()
        assertTrue(telemetry.isBluetoothEnabled)
        assertTrue(telemetry.isLocationEnabled)
        assertFalse(telemetry.activeSosBeacon)
    }

    @Test
    fun broadcastSos_addsBroadcastAndActivatesBeacon() = runBlocking {
        val testCoord = GeoCoordinate(12.9716, 77.5946)
        val result = repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "TEST CRITICAL SOS",
            details = "Testing emergency beacon broadcast",
            coordinate = testCoord
        )

        assertTrue(result.isSuccess)
        val broadcasts = repository.getEmergencyBroadcasts().first()
        val latest = broadcasts.first()
        assertEquals("TEST CRITICAL SOS", latest.title)
        assertEquals(IncidentPriority.CRITICAL, latest.priority)
        assertEquals(testCoord, latest.location)

        val telemetry = repository.getSystemTelemetry().first()
        assertTrue("Telemetry activeSosBeacon must be true after SOS", telemetry.activeSosBeacon)
    }

    @Test
    fun cancelSos_deactivatesBeacon() = runBlocking {
        repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "TEST SOS",
            details = "Testing",
            coordinate = null
        )
        assertTrue(repository.getSystemTelemetry().first().activeSosBeacon)

        val cancelResult = repository.cancelSos()
        assertTrue(cancelResult.isSuccess)
        assertFalse("Telemetry activeSosBeacon must be false after cancel", repository.getSystemTelemetry().first().activeSosBeacon)
    }

    @Test
    fun acknowledgeMessage_marksMessageAcknowledged() = runBlocking {
        val broadcasts = repository.getEmergencyBroadcasts().first()
        val targetId = broadcasts.first().id

        val ackResult = repository.acknowledgeMessage(targetId)
        assertTrue(ackResult.isSuccess)

        val updatedBroadcasts = repository.getEmergencyBroadcasts().first()
        val target = updatedBroadcasts.first { it.id == targetId }
        assertTrue("Message should be marked acknowledged", target.isAcknowledged)
    }
}
