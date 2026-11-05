
package com.resqmesh.app.data.remote

import com.resqmesh.app.data.remote.dto.HealthCheckResponse
import com.resqmesh.app.data.remote.dto.IncidentUploadRequest
import com.resqmesh.app.data.remote.dto.IncidentUploadResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ResQMeshApi {

    @GET("api/health")
    suspend fun checkHealth(): Response<HealthCheckResponse>

    @POST("api/uplink")
    suspend fun uploadIncident(
        @Body incident: IncidentUploadRequest
    ): Response<IncidentUploadResponse>
}
