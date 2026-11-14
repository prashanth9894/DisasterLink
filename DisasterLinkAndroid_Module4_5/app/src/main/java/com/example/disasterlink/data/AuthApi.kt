package com.example.disasterlink.data

import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class RegisterRequest(
    val name: String, val phone: String, val email: String, val governmentId: String,
    val address: String, val pincode: String, val password: String, val role: String,
    val authorityType: String? = null, val district: String = "", val inviteCode: String? = null
)

data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val token: String, val user: UserDto)

data class UserDto(
    val id: String, val name: String, val phone: String, val email: String,
    val governmentId: String, val address: String, val pincode: String,
    val role: String, val authorityType: String? = null, val district: String = ""
)

data class AuthResponse(val token: String, val user: UserDto, val refreshToken: String? = null)

data class MintInviteRequest(val authorityType: String, val district: String? = null, val expiresInHours: Int? = null)
data class InviteCodeResponse(val code: String, val authorityType: String, val district: String?, val expiresAt: Long)

data class EmailOtpRequestBody(val email: String, val role: String)
data class EmailOtpRequestResponse(val message: String)
data class EmailOtpVerifyBody(val email: String, val otp: String, val role: String)

private data class ApiErrorBody(val error: String? = null)

/** Pulls the human-readable "error" field out of a failed response body, with a safe fallback. */
fun extractApiError(body: ResponseBody?): String = try {
    body?.string()?.let { Gson().fromJson(it, ApiErrorBody::class.java)?.error } ?: "Request failed. Please try again."
} catch (_: Exception) {
    "Request failed. Please try again."
}

interface AuthApi {
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>
    @POST("api/auth/refresh") suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>
    @POST("api/auth/logout") suspend fun logout(@Header("Authorization") bearer: String, @Body body: RefreshRequest): Response<Unit>
    @POST("api/invites") suspend fun mintInvite(@Header("Authorization") bearer: String, @Body body: MintInviteRequest): Response<InviteCodeResponse>
    @GET("api/invites") suspend fun myInvites(@Header("Authorization") bearer: String): Response<List<InviteCodeResponse>>
    @POST("api/auth/email-otp/request") suspend fun requestEmailOtp(@Body body: EmailOtpRequestBody): Response<EmailOtpRequestResponse>
    @POST("api/auth/email-otp/verify") suspend fun verifyEmailOtp(@Body body: EmailOtpVerifyBody): Response<AuthResponse>
}
