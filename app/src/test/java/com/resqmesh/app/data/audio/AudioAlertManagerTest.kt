package com.resqmesh.app.data.audio

import android.content.ContextWrapper
import com.resqmesh.app.data.model.IncidentPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAlertManagerTest {

    @Test
    fun noOpAudioAlertManager_recordsDispatchedAlerts() {
        val manager = NoOpAudioAlertManager()
        assertTrue(manager.isEnabled())

        manager.playAlert(IncidentPriority.CRITICAL)
        manager.playAlert(IncidentPriority.HIGH)
        manager.playAlert(IncidentPriority.NORMAL)

        assertEquals(3, manager.playedPriorities.size)
        assertEquals(IncidentPriority.CRITICAL, manager.playedPriorities[0])
        assertEquals(IncidentPriority.HIGH, manager.playedPriorities[1])
        assertEquals(IncidentPriority.NORMAL, manager.playedPriorities[2])
    }

    @Test
    fun noOpAudioAlertManager_whenDisabled_doesNotRecordAlerts() {
        val manager = NoOpAudioAlertManager()
        manager.setEnabled(false)
        assertFalse(manager.isEnabled())

        manager.playAlert(IncidentPriority.CRITICAL)
        assertTrue(manager.playedPriorities.isEmpty())
    }

    @Test
    fun androidAudioAlertManager_instantiationAndToggle() {
        val mockContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
        }
        val manager = AndroidAudioAlertManager(mockContext)
        assertTrue(manager.isEnabled())

        manager.setEnabled(false)
        assertFalse(manager.isEnabled())

        // Ensure invocation with test Context executes safely without crashing
        manager.playAlert(IncidentPriority.CRITICAL)
        manager.playAlert(IncidentPriority.HIGH)
        manager.playAlert(IncidentPriority.NORMAL)
        manager.stopAlert()
    }
}
