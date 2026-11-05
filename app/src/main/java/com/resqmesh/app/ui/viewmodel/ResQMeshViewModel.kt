package com.resqmesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqmesh.app.data.ble.BleServiceStatus
import com.resqmesh.app.data.location.LocationState
import com.resqmesh.app.data.location.LocationStatus
import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.data.model.UploadStatus
import com.resqmesh.app.data.repository.MeshRepository
import com.resqmesh.security.keys.ResponderKeyProvisioner
import com.resqmesh.security.model.KeyProvisioningResult
import com.resqmesh.security.model.SecurityProvisioningState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class ResQMeshViewModel @Inject constructor(
    private val repository: MeshRepository,
    private val keyProvisioner: ResponderKeyProvisioner? = null
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.startDiscovery()
        }
    }

    private val _securityState = MutableStateFlow(
        keyProvisioner?.getProvisioningState() ?: SecurityProvisioningState()
    )
    val securityState: StateFlow<SecurityProvisioningState> = _securityState.asStateFlow()

    fun refreshSecurityState() {
        _securityState.value = keyProvisioner?.getProvisioningState() ?: SecurityProvisioningState()
    }

    /**
     * Imports an ephemeral randomly-generated 32-byte test key for device verification.
     * Temporary key bytes are immediately zeroized in memory upon import.
     */
    fun provisionEphemeralTestKey(keyId: String = "responder-group-test"): Result<KeyProvisioningResult> {
        val provisioner = keyProvisioner ?: return Result.failure(IllegalStateException("Key provisioner unavailable"))
        val testBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val result = provisioner.provisionKey(keyId, testBytes, clearSourceBytes = true)
        refreshSecurityState()
        return result
    }

    /**
     * Removes the provisioned key from secure storage.
     */
    fun removeProvisionedKey(keyId: String = "responder-group-test"): Boolean {
        val provisioner = keyProvisioner ?: return false
        val removed = provisioner.removeKey(keyId)
        refreshSecurityState()
        return removed
    }

    val nodes: StateFlow<List<MeshNode>> = repository.getDiscoveredNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val broadcasts: StateFlow<List<EmergencyMessage>> = repository.getEmergencyBroadcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<SystemTelemetry> = repository.getSystemTelemetry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemTelemetry())

    val locationState: StateFlow<LocationState> = repository.getLocationState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationState(status = LocationStatus.SEARCHING))

    val location: StateFlow<GeoCoordinate?> = repository.getLastKnownLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingUploadCount: StateFlow<Int> = repository.getPendingUploadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestUploadStatus: StateFlow<UploadStatus?> = repository.getLatestUploadStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bleStatus: StateFlow<BleServiceStatus> = repository.getBleStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleServiceStatus())

    fun startDiscovery() {
        viewModelScope.launch {
            repository.startDiscovery()
        }
    }

    fun stopDiscovery() {
        viewModelScope.launch {
            repository.stopDiscovery()
        }
    }

    fun sendSos(priority: IncidentPriority, title: String, details: String) {
        viewModelScope.launch {
            // Coordinate from real location state if acquired/usable, or null if acquiring/unavailable (never block SOS)
            val coord = locationState.value.coordinate
            repository.broadcastSos(priority, title, details, coord)
        }
    }

    fun retryUploads() {
        viewModelScope.launch {
            repository.retryPendingUploads()
        }
    }

    fun cancelSos() {
        viewModelScope.launch {
            repository.cancelSos()
        }
    }

    fun acknowledgeAlert(id: String) {
        viewModelScope.launch {
            repository.acknowledgeMessage(id)
        }
    }

    fun refreshMeshTopology() {
        viewModelScope.launch {
            repository.refreshTopology()
        }
    }
}
