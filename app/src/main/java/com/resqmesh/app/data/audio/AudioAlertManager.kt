package com.resqmesh.app.data.audio

import com.resqmesh.app.data.model.IncidentPriority

/**
 * Interface defining offline audible alarm and emergency beacon playback.
 * Fully decoupled from internet connectivity and external cloud speech APIs.
 */
interface AudioAlertManager {
    fun playAlert(priority: IncidentPriority)
    fun stopAlert()
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
