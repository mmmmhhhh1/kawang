package org.example.kawang.adminmobile.data

import okhttp3.ResponseBody
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AdminApi {

    @POST("api/admin/auth/login")
    suspend fun login(@Body request: AdminLoginRequest): ApiResponse<AdminLoginResponse>

    @GET("api/admin/auth/me")
    suspend fun me(): ApiResponse<AdminProfile>

    @GET("api/admin/recharges")
    suspend fun getRecharges(
        @Query("size") size: Int = 30,
        @Query("cursor") cursor: String? = null,
        @Query("status") status: String? = null,
        @Query("userKeyword") userKeyword: String? = null,
    ): ApiResponse<CursorPageResponse<RechargeItem>>

    @GET("api/admin/recharges/{id}")
    suspend fun getRechargeDetail(@Path("id") id: Long): ApiResponse<RechargeDetail>

    @PATCH("api/admin/recharges/{id}/approve")
    suspend fun approveRecharge(@Path("id") id: Long): ApiResponse<RechargeDetail>

    @PATCH("api/admin/recharges/{id}/reject")
    suspend fun rejectRecharge(
        @Path("id") id: Long,
        @Body request: RejectRechargeRequest,
    ): ApiResponse<RechargeDetail>

    @GET("api/admin/support/sessions")
    suspend fun getSupportSessions(
        @Query("size") size: Int = 30,
        @Query("cursor") cursor: String? = null,
        @Query("updatedAfter") updatedAfter: String? = null,
        @Query("status") status: String? = null,
        @Query("keyword") keyword: String? = null,
    ): ApiResponse<CursorPageResponse<SupportSessionItem>>

    @GET("api/admin/support/sessions/{sessionId}/messages")
    suspend fun getSupportMessages(
        @Path("sessionId") sessionId: Long,
        @Query("size") size: Int = 30,
        @Query("cursor") cursor: String? = null,
        @Query("after") after: String? = null,
    ): ApiResponse<CursorPageResponse<SupportMessage>>

    @POST("api/admin/support/sessions/{sessionId}/messages")
    suspend fun sendSupportMessage(
        @Path("sessionId") sessionId: Long,
        @Body request: SupportSendMessageRequest,
    ): ApiResponse<SupportDispatchPayload>

    @Multipart
    @POST("api/admin/support/sessions/{sessionId}/attachments")
    suspend fun sendSupportAttachment(
        @Path("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part,
        @Part("content") content: RequestBody?,
    ): ApiResponse<SupportDispatchPayload>

    @POST("api/admin/support/sessions/{sessionId}/read")
    suspend fun markSupportRead(@Path("sessionId") sessionId: Long): ApiResponse<SupportUnread>

    @POST("api/admin/mobile/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): ApiResponse<Unit>

    @POST("api/admin/mobile/devices/unregister")
    suspend fun unregisterDevice(@Body request: DeviceUnregisterRequest): ApiResponse<Unit>

    @GET
    @Streaming
    suspend fun downloadFile(@retrofit2.http.Url url: String): ResponseBody
}
