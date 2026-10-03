package com.industri.fleettrack.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class DeliveryDto(
    @SerializedName("order_id") val orderId: String,
    @SerializedName("tracking_number") val trackingNumber: String,
    @SerializedName("recipient_name") val recipientName: String,
    @SerializedName("recipient_phone") val recipientPhone: String,
    @SerializedName("destination_address") val destinationAddress: String,
    @SerializedName("cod_amount") val codAmount: Double,
    @SerializedName("status") val status: String,
    @SerializedName("failure_reason") val failureReason: String? = null
)

interface CourierApiService {
    @GET("api/v1/courier/manifest/today")
    suspend fun getTodayManifest(): Response<List<DeliveryDto>>

    @POST("api/v1/courier/deliveries/{order_id}/status")
    suspend fun updateDeliveryStatus(
        @Path("order_id") orderId: String,
        @Query("status") status: String
    ): Response<Map<String, Any>>

    // Upsert penuh (dipakai untuk tambah, edit, dan retry sinkronisasi)
    @PUT("api/v1/courier/deliveries/{order_id}")
    suspend fun upsertDelivery(
        @Path("order_id") orderId: String,
        @Body body: DeliveryDto
    ): Response<Map<String, Any>>

    @DELETE("api/v1/courier/deliveries/{order_id}")
    suspend fun deleteDelivery(@Path("order_id") orderId: String): Response<Map<String, Any>>

    companion object {
        private const val BASE_URL = "https://mock-api.fleettrack.id/"

        fun create(isDebug: Boolean = true): CourierApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (isDebug) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CourierApiService::class.java)
        }
    }
}
