package com.resqmesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.data.repository.MeshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResQMeshViewModel @Inject constructor(
    private val repository: MeshRepository
) : ViewModel() {

    val nodes: StateFlow<List<MeshNode>> = repository.getDiscoveredNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val broadcasts: StateFlow<List<EmergencyMessage>> = repository.getEmergencyBroadcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<SystemTelemetry> = repository.getSystemTelemetry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemTelemetry())

    val location: StateFlow<GeoCoordinate?> = repository.getLastKnownLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sendSos(priority: IncidentPriority, title: String, details: String) {
        viewModelScope.launch {
            repository.broadcastSos(priority, title, details, location.value)
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
