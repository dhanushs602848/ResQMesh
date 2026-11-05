
package com.resqmesh.app

import com.resqmesh.app.data.remote.RetrofitClient

object BackendConnectionTest {

    suspend fun testConnection(): String {
        return try {
            val response = RetrofitClient.api.checkHealth()

            if (response.isSuccessful && response.body()?.success == true) {
                "CONNECTED: ResQMesh backend is ONLINE"
            } else {
                "FAILED: Backend returned ${response.code()}"
            }

        } catch (e: Exception) {
            "CONNECTION ERROR: ${e.message}"
        }
    }
}
