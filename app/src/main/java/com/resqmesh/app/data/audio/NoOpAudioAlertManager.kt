package com.resqmesh.app.data.audio

import com.resqmesh.app.data.model.IncidentPriority

/**
 * Lightweight test implementation of [AudioAlertManager] capturing emitted alerts without audio hardware.
 */
class NoOpAudioAlertManager : AudioAlertManager {
    val playedPriorities = mutableListOf<IncidentPriority>()
    private var isAlertEnabled: Boolean = true

    override fun playAlert(priority: IncidentPriority) {
        if (isAlertEnabled) {
            playedPriorities.add(priority)
        }
    }

    override fun stopAlert() {}
    override fun isEnabled(): Boolean = isAlertEnabled
    override fun setEnabled(enabled: Boolean) {
        isAlertEnabled = enabled
    }
}
