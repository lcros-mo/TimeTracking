package com.timetracking.app.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface AuthApi {
    @GET("api/whitelist")
    suspend fun checkAuthorization(
        @Query("email") email: String,
        @Header("x-api-key") apiKey: String
    ): Response<AuthResponse>
}

// Modelo de respuesta de la API
data class AuthResponse(
    @SerializedName("authorized") val authorized: Boolean,
    @SerializedName("user") val user: User?,
    @SerializedName("message") val message: String?
)

data class User(
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String
)