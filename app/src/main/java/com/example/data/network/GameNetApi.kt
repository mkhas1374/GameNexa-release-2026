package com.example.data.network

import com.example.BuildConfig
import com.example.data.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

object GameNexaDns : Dns {
    private const val PRODUCTION_HOST = "api.gamenermayket.ir"
    private const val KNOWN_IPV4_FALLBACK = "87.248.152.3"

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.equals(PRODUCTION_HOST, ignoreCase = true)) return Dns.SYSTEM.lookup(hostname)
        val addresses = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        val fallback = runCatching { InetAddress.getByName(KNOWN_IPV4_FALLBACK) }.getOrNull()
        return buildList {
            addAll(ipv4)
            if (fallback != null && none { it.hostAddress == fallback.hostAddress }) add(fallback)
            if (isEmpty()) addAll(addresses)
        }
    }
}

data class ServerClockResponse(
    @com.squareup.moshi.Json(name = "serverTime") val serverTime: Long
)

interface GameNetApi {
    
    @GET("api/v1/time")
    suspend fun getServerTime(): ServerClockResponse

    @GET("api/v1/time")
    suspend fun healthCheck(): retrofit2.Response<ServerClockResponse>

    @GET("api/v1/super-manager/managers")
    suspend fun getSuperManagers(): List<AdminManagerDto>

    @POST("api/v1/super-manager/add-manager")
    suspend fun addManagerContract(@Body request: Map<String, Any>): okhttp3.ResponseBody


    @POST("api/v1/super-manager/recover")
    suspend fun recoverSuperManager(@Body body: Map<String, String>): okhttp3.ResponseBody

    @POST("api/v1/manager/customers")
    suspend fun addManagerCustomer(@Body body: Map<String, Any>): okhttp3.ResponseBody


    @POST("api/v1/trial/status")
    suspend fun checkTrialStatus(@Body body: CheckTrialRequest): CheckTrialResponse

    @GET("api/v1/super-manager/trial-devices")
    suspend fun getAllDeviceTrials(): okhttp3.ResponseBody

    @DELETE("api/v1/super-manager/trial-devices/{id}")
    suspend fun deleteDeviceTrial(@Path("id") id: String): retrofit2.Response<okhttp3.ResponseBody>

    @POST("api/v1/super-manager/trial-devices/{id}/extend")
    suspend fun extendDeviceTrial(@Path("id") id: String): retrofit2.Response<okhttp3.ResponseBody>



    @POST("api/v1/super-manager/managers")
    suspend fun createSuperManager(@Body request: CreateManagerRequestDto): okhttp3.ResponseBody

    @PUT("api/v1/super-manager/managers/{id}")
    suspend fun updateManagerStatus(@Path("id") id: String, @Body request: UpdateManagerRequestDto): retrofit2.Response<okhttp3.ResponseBody>

    @PUT("api/v1/super-manager/managers/{id}")
    suspend fun updateManager(@Path("id") id: String, @Body request: EditManagerRequestDto): retrofit2.Response<okhttp3.ResponseBody>

    @DELETE("api/v1/super-manager/managers/{id}")
    suspend fun deleteManager(@Path("id") id: String): retrofit2.Response<okhttp3.ResponseBody>


    @POST("api/v1/super-manager/create-manager")
    suspend fun createSuperManagerAlt(@Body request: CreateManagerRequestDto): okhttp3.ResponseBody

    @POST("api/v1/super-manager/add-manager")
    suspend fun createManager(@Body request: CreateManagerRequestDto): okhttp3.ResponseBody
    




    @GET("api/v1/manager/stations")
    suspend fun getStations(): List<StationState>

    @POST("api/v1/manager/stations")
    suspend fun saveStation(@Body state: StationState): StationState





    @GET("api/v1/manager/console-types")
    suspend fun getConsoleTypes(): List<ConsoleType>

    @POST("api/v1/manager/console-types")
    suspend fun saveConsoleType(@Body console: ConsoleType): ConsoleType

    @DELETE("api/v1/manager/console-types/{name}")
    suspend fun deleteConsoleType(@Path("name") name: String): Response<Unit>

    @GET("api/v1/manager/products")
    suspend fun getProducts(): List<Product>

    @POST("api/v1/manager/products")
    suspend fun saveProduct(@Body product: Product): Product

    @DELETE("api/v1/manager/products/{name}")
    suspend fun deleteProduct(@Path("name") name: String): Response<Unit>

    @GET("api/v1/manager/orders/{stationId}")
    suspend fun getOrders(@Path("stationId") stationId: Int): List<StationOrder>

    @POST("api/v1/manager/orders")
    suspend fun saveOrder(@Body order: StationOrder): StationOrder

    @DELETE("api/v1/manager/orders/{id}")
    suspend fun deleteOrder(@Path("id") id: String): Response<Unit>

    @DELETE("api/v1/manager/orders/station/{stationId}")
    suspend fun clearOrders(@Path("stationId") stationId: Int): Response<Unit>

    @GET("api/v1/manager/session-history")
    suspend fun getSessionHistory(): List<SessionHistory>

    @POST("api/v1/manager/session-history")
    suspend fun addSessionHistory(@Body history: SessionHistory): SessionHistory

    @DELETE("api/v1/manager/session-history")
    suspend fun clearSessionHistory(): Response<Unit>

    @GET("api/v1/manager/customers")
    suspend fun getCustomers(): List<Customer>

    @POST("api/v1/manager/customers")
    suspend fun saveCustomer(@Body customer: Customer): Customer

    @DELETE("api/v1/manager/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/manager/customers/delete-batch")
    suspend fun deleteCustomerBatch(@Body body: Map<String, List<Long>>): Response<Map<String, Any>>

    @GET("api/v1/manager/reservations")
    suspend fun getReservations(): List<Reservation>


    @DELETE("api/v1/manager/reservations/{id}")
    suspend fun deleteReservation(@Path("id") id: Long): Response<Unit>

    @GET("api/v1/manager/settings/{key}")
    suspend fun getSetting(@Path("key") key: String): Map<String, String>

    @POST("api/v1/manager/settings")
    suspend fun saveSetting(@Body body: Map<String, String>): Response<Unit>


    // Auth & Subscription Endpoints
    @POST("api/auth/customer/register")
    suspend fun registerUser(@Body body: UserRegisterRequest): UserAuthResponse

    @POST("api/auth/manager/login")
    suspend fun loginUser(@Body body: UserLoginRequest): UserAuthResponse

    // Diagnostic, Health & Ping Endpoints
    @GET("api/v1/super-manager/ping")
    suspend fun pingSuperManager(): retrofit2.Response<okhttp3.ResponseBody>

    @POST("api/v1/super-manager/ping")
    suspend fun pingSuperManagerPost(): retrofit2.Response<okhttp3.ResponseBody>






    @GET("api/v1/auth/check")
    suspend fun checkAuth(): Map<String, Any>




    // User Device Endpoints
    @POST("api/v1/manager/device")
    suspend fun addUserDevice(@Body body: Map<String, Any>): Map<String, Any>





    // Gaming Session Endpoints



    // Announcements and Banners




    // Canonical subscription/license contract
    @GET("api/v1/subscriptions/check")
    suspend fun checkSubscriptionStatus(
        @Query("user_id") userId: String?,
        @Query("device_id") deviceId: String,
        @Query("device_fingerprint") deviceFingerprint: String
    ): SubscriptionCheckResponse

    @GET("api/v1/subscriptions/check")
    suspend fun checkSubscription(
        @Query("device_id") deviceId: String,
        @Query("license_code") licenseCode: String? = null,
        @Query("user_phone") userPhone: String? = null
    ): LicenseCheckResponse

    @POST("api/v1/subscriptions/status")
    suspend fun checkLicenseStatus(@Body body: LicenseCheckRequest): LicenseCheckResponse

    @POST("api/v1/subscriptions/activate")
    suspend fun activateLicense(@Body body: LicenseActivateRequest): LicenseCheckResponse

    @POST("api/v1/trial/start")
    suspend fun startFreeTrial(@Body body: TrialStartRequest): TrialStartResponse

    @POST("api/v1/subscriptions/buy")
    suspend fun buySubscription(@Body body: LicenseBuyRequest): LicenseBuyResponse

    @POST("api/v1/subscriptions/set-password")
    suspend fun setLicensePassword(@Body body: SetPasswordRequest): Boolean

    @POST("api/v1/coupons/validate")
    suspend fun validateCoupon(@Body params: Map<String, String>): Map<String, Any>

    @GET("api/v1/plans")
    suspend fun getSubscriptionPlans(): List<SubscriptionPlanDto>

    @GET("api/v1/super-manager/subscription-plans")
    suspend fun getSubscriptionPlansAdmin(): SubscriptionPlansAdminResponse

    @PUT("api/v1/super-manager/subscription-plans")
    suspend fun updateSubscriptionPlansAdmin(@Body body: Map<String, Any>): SubscriptionPlansAdminResponse

}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UserRegisterRequest(
    @com.squareup.moshi.Json(name = "username") val username: String,
    @com.squareup.moshi.Json(name = "password") val password: String,
    @com.squareup.moshi.Json(name = "phone") val phone: String? = null,
    @com.squareup.moshi.Json(name = "email") val email: String? = null,
    @com.squareup.moshi.Json(name = "role") val role: String = "OPERATOR"
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UserLoginRequest(
    @com.squareup.moshi.Json(name = "username") val username: String,
    @com.squareup.moshi.Json(name = "password") val password: String,
    @com.squareup.moshi.Json(name = "deviceId") val deviceId: String? = null,
    @com.squareup.moshi.Json(name = "deviceFingerprint") val deviceFingerprint: String? = null,
    @com.squareup.moshi.Json(name = "device_fingerprint") val deviceFingerprintSnake: String? = null,
    @com.squareup.moshi.Json(name = "deviceModel") val deviceModel: String? = null,
    @com.squareup.moshi.Json(name = "osVersion") val osVersion: String? = null,
    @com.squareup.moshi.Json(name = "appVersion") val appVersion: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UserDto(
    @com.squareup.moshi.Json(name = "id") val id: Any? = null,
    @com.squareup.moshi.Json(name = "username") val username: String? = null,
    @com.squareup.moshi.Json(name = "phone") val phone: String? = null,
    @com.squareup.moshi.Json(name = "role") val role: String? = null,
    @com.squareup.moshi.Json(name = "email") val email: String? = null,
    @com.squareup.moshi.Json(name = "created_at") val createdAtRaw: Long? = null,
    @com.squareup.moshi.Json(name = "createdAt") val altCreatedAt: Long? = null
) {
    val realId: String get() = id?.toString() ?: ""
    val realPhone: String get() = phone ?: username ?: ""
    val realCreatedAt: Long get() = createdAtRaw ?: altCreatedAt ?: 0L
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UserAuthResponse(
    @com.squareup.moshi.Json(name = "token") val token: String? = null,
    @com.squareup.moshi.Json(name = "user") val user: UserDto? = null,
    @com.squareup.moshi.Json(name = "success") val success: Boolean = true,
    @com.squareup.moshi.Json(name = "message") val message: String = "",
    @com.squareup.moshi.Json(name = "error") val error: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LicenseCheckRequest(
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String,
    @com.squareup.moshi.Json(name = "license_code") val licenseCode: String? = null,
    @com.squareup.moshi.Json(name = "user_phone") val userPhone: String? = null,
    @com.squareup.moshi.Json(name = "deviceId") val altDeviceId: String? = null,
    @com.squareup.moshi.Json(name = "licenseCode") val altLicenseCode: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LicenseCheckResponse(
    @com.squareup.moshi.Json(name = "valid") val valid: Boolean = false,
    @com.squareup.moshi.Json(name = "type") val type: String? = null,
    @com.squareup.moshi.Json(name = "expires_at") val expiresAtRaw: Long? = null,
    @com.squareup.moshi.Json(name = "expiresAt") val altExpiresAt: Long? = null,
    @com.squareup.moshi.Json(name = "server_time") val serverTimeRaw: Long? = null,
    @com.squareup.moshi.Json(name = "message") val message: String = "",
    @com.squareup.moshi.Json(name = "activated_at") val activatedAtRaw: Long? = null,
    @com.squareup.moshi.Json(name = "trial_active") val trialActiveRaw: Boolean? = null,
    @com.squareup.moshi.Json(name = "trial_used") val trialUsedRaw: Boolean? = null,
    @com.squareup.moshi.Json(name = "trial_start_time") val trialStartTimeRaw: Long? = null,
    @com.squareup.moshi.Json(name = "trial_duration_minutes") val trialDurationMinutesRaw: Int? = null,
    @com.squareup.moshi.Json(name = "has_password") val hasPasswordRaw: Boolean? = null,
    @com.squareup.moshi.Json(name = "license_code") val license_code: String? = null,
    @com.squareup.moshi.Json(name = "licenseCode") val licenseCode: String? = null,
    @com.squareup.moshi.Json(name = "expiresAt") val expiresAt: Long = 0L,
    @com.squareup.moshi.Json(name = "serverTime") val serverTime: Long = 0L,
    @com.squareup.moshi.Json(name = "activatedAt") val activatedAt: Long = 0L,
    @com.squareup.moshi.Json(name = "trialActive") val trialActive: Boolean = false,
    @com.squareup.moshi.Json(name = "trialUsed") val trialUsed: Boolean = false,
    @com.squareup.moshi.Json(name = "trialStartTime") val trialStartTime: Long = 0L,
    @com.squareup.moshi.Json(name = "trialDurationMinutes") val trialDurationMinutes: Int = 30,
    @com.squareup.moshi.Json(name = "hasPassword") val hasPassword: Boolean = false,
    @com.squareup.moshi.Json(name = "success") val success: Boolean = false
) {
    val isValid: Boolean get() = valid || success
    val realLicenseCode: String get() = licenseCode ?: license_code ?: ""
    val realExpiresAt: Long get() = expiresAtRaw ?: (if (expiresAt > 0L) expiresAt else 0L)
    val realServerTime: Long get() = serverTimeRaw ?: (if (serverTime > 0L) serverTime else System.currentTimeMillis())
    val realActivatedAt: Long get() = activatedAtRaw ?: (if (activatedAt > 0L) activatedAt else 0L)
    val isTrialActive: Boolean get() = trialActiveRaw ?: trialActive
    val isTrialUsed: Boolean get() = trialUsedRaw ?: trialUsed
    val realTrialStartTime: Long get() = trialStartTimeRaw ?: trialStartTime
    val realTrialDurationMinutes: Int get() = trialDurationMinutesRaw ?: trialDurationMinutes
    val realHasPassword: Boolean get() = hasPasswordRaw ?: hasPassword
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SubscriptionTrialRequest(
    @com.squareup.moshi.Json(name = "user_id") val userId: String? = null,
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String,
    @com.squareup.moshi.Json(name = "device_fingerprint") val deviceFingerprint: String,
    @com.squareup.moshi.Json(name = "device_model") val deviceModel: String = android.os.Build.MODEL,
    @com.squareup.moshi.Json(name = "os_version") val osVersion: String = android.os.Build.VERSION.RELEASE
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SubscriptionCheckResponse(
    @com.squareup.moshi.Json(name = "license_status") val licenseStatusRaw: String? = null,
    @com.squareup.moshi.Json(name = "licenseStatus") val altLicenseStatus: String? = null,
    @com.squareup.moshi.Json(name = "status") val statusRaw: String? = null,
    @com.squareup.moshi.Json(name = "active") val activeRaw: Boolean? = null,
    @com.squareup.moshi.Json(name = "is_active") val isActiveRaw: Boolean? = null,
    @com.squareup.moshi.Json(name = "plan_type") val planTypeRaw: String? = null,
    @com.squareup.moshi.Json(name = "planType") val altPlanType: String? = null,
    @com.squareup.moshi.Json(name = "plan") val justPlan: String? = null,
    @com.squareup.moshi.Json(name = "expire_time") val expireTimeRaw: Long? = null,
    @com.squareup.moshi.Json(name = "expireTime") val altExpireTime: Long? = null,
    @com.squareup.moshi.Json(name = "expires_at") val expiresAtRaw: Long? = null,
    @com.squareup.moshi.Json(name = "expiresAt") val altExpiresAt: Long? = null,
    @com.squareup.moshi.Json(name = "server_time") val serverTimeRaw: Long? = null,
    @com.squareup.moshi.Json(name = "serverTime") val altServerTime: Long? = null,
    @com.squareup.moshi.Json(name = "message") val message: String = "",
    @com.squareup.moshi.Json(name = "trial_used") val trialUsed: Boolean? = null,
    @com.squareup.moshi.Json(name = "license_code") val licenseCode: String? = null,
    @com.squareup.moshi.Json(name = "valid") val validRaw: Boolean? = null
) {
    val realLicenseStatus: String
        get() = (licenseStatusRaw ?: altLicenseStatus ?: statusRaw ?: (if (activeRaw == true || isActiveRaw == true || validRaw == true) "ACTIVE" else "INACTIVE")).uppercase()
    val realPlanType: String
        get() = (planTypeRaw ?: altPlanType ?: justPlan ?: "MONTH1").uppercase()
    val isLicenseActive: Boolean
        get() = activeRaw == true || isActiveRaw == true || validRaw == true || realLicenseStatus.equals("ACTIVE", ignoreCase = true) || realLicenseStatus.equals("TRIAL", ignoreCase = true)
    val realExpireTime: Long
        get() = expireTimeRaw ?: altExpireTime ?: expiresAtRaw ?: altExpiresAt ?: 0L
    val realServerTime: Long
        get() = serverTimeRaw ?: altServerTime ?: System.currentTimeMillis()
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    @com.squareup.moshi.Json(name = "plan_id") val planId: String,
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String,
    @com.squareup.moshi.Json(name = "user_phone") val userPhone: String? = null,
    @com.squareup.moshi.Json(name = "existing_license_code") val existingLicenseCode: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CreateOrderResponse(
    @com.squareup.moshi.Json(name = "success") val success: Boolean = true,
    @com.squareup.moshi.Json(name = "order_id") val orderId: String = "",
    @com.squareup.moshi.Json(name = "product_id") val productId: String = "MONTH1",
    @com.squareup.moshi.Json(name = "amount") val amount: Double = 0.0,
    @com.squareup.moshi.Json(name = "message") val message: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MyketVerifyRequest(
    @com.squareup.moshi.Json(name = "order_id") val orderId: String,
    @com.squareup.moshi.Json(name = "purchase_token") val purchaseToken: String,
    @com.squareup.moshi.Json(name = "transaction_id") val transactionId: String,
    @com.squareup.moshi.Json(name = "purchase_state") val purchaseState: Int = 0,
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String? = null,
    @com.squareup.moshi.Json(name = "user_phone") val userPhone: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MyketVerifyResponse(
    @com.squareup.moshi.Json(name = "success") val success: Boolean = false,
    @com.squareup.moshi.Json(name = "valid") val valid: Boolean = false,
    @com.squareup.moshi.Json(name = "message") val message: String = "",
    @com.squareup.moshi.Json(name = "license_code") val licenseCode: String? = null,
    @com.squareup.moshi.Json(name = "licenseCode") val altLicenseCode: String? = null,
    @com.squareup.moshi.Json(name = "type") val type: String? = "MONTH1",
    @com.squareup.moshi.Json(name = "expires_at") val expiresAt: Long? = 0L,
    @com.squareup.moshi.Json(name = "expiresAt") val altExpiresAt: Long? = 0L,
    @com.squareup.moshi.Json(name = "server_time") val serverTime: Long? = 0L,
    @com.squareup.moshi.Json(name = "serverTime") val altServerTime: Long? = 0L
) {
    val isVerified: Boolean get() = success || valid
    val realLicenseCode: String get() = licenseCode ?: altLicenseCode ?: ""
    val realExpiresAt: Long get() = expiresAt ?: altExpiresAt ?: 0L
    val realServerTime: Long get() = serverTime ?: altServerTime ?: System.currentTimeMillis()
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SubscriptionPlansAdminResponse(
    @com.squareup.moshi.Json(name = "success") val success: Boolean = false,
    @com.squareup.moshi.Json(name = "version") val version: Int = 0,
    @com.squareup.moshi.Json(name = "settings") val settings: Map<String, Any?> = emptyMap()
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SubscriptionPlanDto(
    @com.squareup.moshi.Json(name = "id") val id: String? = null,
    @com.squareup.moshi.Json(name = "name") val name: String? = null,
    @com.squareup.moshi.Json(name = "price") val price: Double? = null,
    @com.squareup.moshi.Json(name = "durationDays") val durationDays: Int? = null,
    @com.squareup.moshi.Json(name = "paymentUrl") val paymentUrl: String? = null,
    @com.squareup.moshi.Json(name = "savingText") val savingText: String? = null,
    @com.squareup.moshi.Json(name = "extraDevicePrice") val extraDevicePrice: Double? = null,
    @com.squareup.moshi.Json(name = "minDevices") val minDevices: Int? = null,
    @com.squareup.moshi.Json(name = "maxDevices") val maxDevices: Int? = null,
    @com.squareup.moshi.Json(name = "allowExtraDevices") val allowExtraDevices: Boolean? = null,
    @com.squareup.moshi.Json(name = "discountedPrice") val discountedPrice: Double? = null,
    @com.squareup.moshi.Json(name = "discounted_price") val discounted_price: Double? = null,
    @com.squareup.moshi.Json(name = "discountPaymentUrl") val rawDiscountPaymentUrl: String? = null,
    @com.squareup.moshi.Json(name = "discount_payment_url") val discount_payment_url: String? = null,
    @com.squareup.moshi.Json(name = "discountpaymenturl") val discountpaymenturl: String? = null,
    @com.squareup.moshi.Json(name = "payment_url") val payment_url: String? = null,
    @com.squareup.moshi.Json(name = "paymenturl") val paymenturl: String? = null,
    @com.squareup.moshi.Json(name = "duration_days") val duration_days: Int? = null,
    @com.squareup.moshi.Json(name = "durationdays") val durationdays: Int? = null,
    @com.squareup.moshi.Json(name = "saving_text") val saving_text: String? = null,
    @com.squareup.moshi.Json(name = "savingtext") val savingtext: String? = null,
    @com.squareup.moshi.Json(name = "extra_device_price") val extra_device_price: Double? = null,
    @com.squareup.moshi.Json(name = "extradeviceprice") val extradeviceprice: Double? = null,
    @com.squareup.moshi.Json(name = "min_devices") val min_devices: Int? = null,
    @com.squareup.moshi.Json(name = "max_devices") val max_devices: Int? = null,
    @com.squareup.moshi.Json(name = "allow_extra_devices") val allow_extra_devices: Boolean? = null,
    @com.squareup.moshi.Json(name = "sortOrder") val rawSortOrder: Int? = null,
    @com.squareup.moshi.Json(name = "sort_order") val sort_order: Int? = null,
    @com.squareup.moshi.Json(name = "display_order") val display_order: Int? = null,
    @com.squareup.moshi.Json(name = "nameFa") val nameFa: String? = null,
    @com.squareup.moshi.Json(name = "nameEn") val nameEn: String? = null,
    @com.squareup.moshi.Json(name = "descriptionFa") val descriptionFa: String? = null,
    @com.squareup.moshi.Json(name = "descriptionEn") val descriptionEn: String? = null,
    @com.squareup.moshi.Json(name = "pageTitleFa") val pageTitleFa: String? = null,
    @com.squareup.moshi.Json(name = "pageTitleEn") val pageTitleEn: String? = null,
    @com.squareup.moshi.Json(name = "pageSubtitleFa") val pageSubtitleFa: String? = null,
    @com.squareup.moshi.Json(name = "pageSubtitleEn") val pageSubtitleEn: String? = null,
    @com.squareup.moshi.Json(name = "paymentInstructionFa") val paymentInstructionFa: String? = null,
    @com.squareup.moshi.Json(name = "paymentInstructionEn") val paymentInstructionEn: String? = null,
    @com.squareup.moshi.Json(name = "purchaseButtonFa") val purchaseButtonFa: String? = null,
    @com.squareup.moshi.Json(name = "purchaseButtonEn") val purchaseButtonEn: String? = null,
    @com.squareup.moshi.Json(name = "currencyFa") val currencyFa: String? = null,
    @com.squareup.moshi.Json(name = "currencyEn") val currencyEn: String? = null,
    @com.squareup.moshi.Json(name = "supportMessageFa") val supportMessageFa: String? = null,
    @com.squareup.moshi.Json(name = "supportMessageEn") val supportMessageEn: String? = null
) {
    val realId: String
        get() = id ?: ""

    val realName: String
        get() = name ?: ""

    val realPrice: Double
        get() = price ?: 0.0

    val sortOrder: Int
        get() = rawSortOrder ?: sort_order ?: display_order ?: 0

    val realPaymentUrl: String
        get() = paymentUrl?.ifBlank { null } ?: payment_url ?: paymenturl ?: ""

    val realDiscountPaymentUrl: String
        get() = rawDiscountPaymentUrl?.ifBlank { null } ?: discount_payment_url ?: discountpaymenturl ?: realPaymentUrl

    val realDurationDays: Int
        get() = durationDays ?: duration_days ?: durationdays ?: 30

    val realSavingText: String
        get() = savingText?.ifBlank { null } ?: saving_text ?: savingtext ?: ""

    val realExtraDevicePrice: Double
        get() {
            val raw = extraDevicePrice ?: extra_device_price ?: extradeviceprice
            return if (raw != null && raw > 0.0) raw else (if (realId.equals("PERMANENT", ignoreCase = true)) 350000.0 else 100000.0)
        }

    val realMinDevices: Int
        get() = minDevices ?: min_devices ?: 1

    val realMaxDevices: Int
        get() = maxDevices ?: max_devices ?: 10

    val realAllowExtraDevices: Boolean
        get() = allowExtraDevices ?: allow_extra_devices ?: true

    val realDiscountedPrice: Double?
        get() = discountedPrice ?: discounted_price
}

data class LicenseActivateRequest(
    val deviceId: String,
    val licenseCode: String,
    val activationSecret: String? = null,
    val userPhone: String? = null,
    val extensionType: String? = null,
    val extensionExpiresAt: Long? = null,
    val extensionMaxDevices: Int? = null,
    val transactionId: String? = null,
    val recoveryPassword: String? = null
)

data class LicenseBuyRequest(
    val deviceId: String,
    val plan: String,
    val coupon: String? = null,
    val gateway: String? = null,
    val extraDevices: Int = 0,
    val calculatedPrice: Double = 0.0,
    val extendLicense: Boolean = false,
    val existingLicenseCode: String? = null,
    val userPhone: String? = null,
    val userName: String? = null,
    val gameNetName: String? = null,
    val password: String? = null,
    val idempotencyKey: String = ""
)

data class LicenseInfoResponse(
    val exists: Boolean,
    val deviceId: String? = null,
    val lastActiveAt: Long = 0L,
    val hasPassword: Boolean = false,
    val licenseCode: String = "",
    val message: String = ""
)

data class SetPasswordRequest(
    val licenseCode: String,
    val password: String,
    val activationSecret: String? = null,
    val userPhone: String? = null,
    val deviceId: String? = null
)

data class LicenseBuyResponse(
    val paymentUrl: String,
    val licenseCode: String,
    val activationSecret: String? = null,
    val expiresAt: Long = 0L,
    val maxDevices: Int = 1,
    val type: String = "",
    val transactionId: String? = null,
    val amount: Double? = null,
    val discountApplied: String? = null
)

data class AdminLogDto(
    val id: Long = 0L,
    val timestamp: Long,
    val action: String,
    val operator: String,
    val details: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class HealthCheckResponse(
    @com.squareup.moshi.Json(name = "status") val status: String? = "ok",
    @com.squareup.moshi.Json(name = "database") val database: String? = null,
    @com.squareup.moshi.Json(name = "timestamp") val timestamp: Long? = null
)

class AnyAdapter {
    @com.squareup.moshi.FromJson
    fun jsonToAny(reader: com.squareup.moshi.JsonReader): Any? {
        return reader.readJsonValue()
    }

    @com.squareup.moshi.ToJson
    fun anyToJson(writer: com.squareup.moshi.JsonWriter, value: Any?) {
        value?.let { writer.value(it.toString()) } ?: writer.nullValue()
    }
}


@com.squareup.moshi.JsonClass(generateAdapter = true)
data class DeviceTrialDto(
    @com.squareup.moshi.Json(name = "id") val rawId: Any? = null,
    @com.squareup.moshi.Json(name = "deviceId") val rawDeviceId: String? = null,
    @com.squareup.moshi.Json(name = "device_id") val altDeviceId: String? = null,
    @com.squareup.moshi.Json(name = "deviceFingerprint") val deviceFingerprint: String? = null,
    @com.squareup.moshi.Json(name = "device_fingerprint") val deviceFingerprintSnake: String? = null,
    @com.squareup.moshi.Json(name = "deviceName") val deviceName: String? = null,
    @com.squareup.moshi.Json(name = "device_name") val deviceNameSnake: String? = null,
    @com.squareup.moshi.Json(name = "status") val status: String? = null,
    @com.squareup.moshi.Json(name = "startTime") val startTime: Long? = null,
    @com.squareup.moshi.Json(name = "started_at") val startedAtSnake: String? = null,
    @com.squareup.moshi.Json(name = "expiryDate") val expiryDate: Long? = null,
    @com.squareup.moshi.Json(name = "expires_at") val expiresAtSnake: String? = null,
    @com.squareup.moshi.Json(name = "durationMinutes") val durationMinutes: Int? = null,
    @com.squareup.moshi.Json(name = "expireTime") val expireTime: Long? = null,
    @com.squareup.moshi.Json(name = "isUsed") val isUsed: Boolean? = null,
    @com.squareup.moshi.Json(name = "isResetByAdmin") val isResetByAdmin: Boolean? = null
) {
    val deviceId: String get() = rawDeviceId ?: altDeviceId ?: rawId?.toString() ?: ""
    val displayDeviceName: String get() = deviceName ?: deviceNameSnake ?: "دستگاه تستی"
    val id: String? get() = rawId?.toString()
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class DeviceTrialsResponse(
    @com.squareup.moshi.Json(name = "status") val status: String? = null,
    @com.squareup.moshi.Json(name = "trial_devices") val trialDevicesAlt: List<DeviceTrialDto>? = null,
    @com.squareup.moshi.Json(name = "trialDevices") val trialDevicesCamel: List<DeviceTrialDto>? = null,
    @com.squareup.moshi.Json(name = "devices") val devices: List<DeviceTrialDto>? = null,
    @com.squareup.moshi.Json(name = "data") val data: List<DeviceTrialDto>? = null,
    @com.squareup.moshi.Json(name = "trials") val trials: List<DeviceTrialDto>? = null,
    @com.squareup.moshi.Json(name = "items") val itemsList: List<DeviceTrialDto>? = null
) {
    val items: List<DeviceTrialDto>
        get() = trialDevicesAlt ?: trialDevicesCamel ?: devices ?: data ?: trials ?: itemsList ?: emptyList()
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ManagerProfileDto(
    @com.squareup.moshi.Json(name = "id") val rawId: Any? = null,
    @com.squareup.moshi.Json(name = "userId") val userId: Long = 0L,
    @com.squareup.moshi.Json(name = "managerId") val managerId: String = "",
    @com.squareup.moshi.Json(name = "fullName") val fullName: String = "",
    @com.squareup.moshi.Json(name = "gameneName") val gameneName: String = "",
    @com.squareup.moshi.Json(name = "phone") val phone: String = "",
    @com.squareup.moshi.Json(name = "planType") val planType: String = "",
    @com.squareup.moshi.Json(name = "plan_name") val planName: String? = null,
    @com.squareup.moshi.Json(name = "maxDevices") val maxDevices: Int = 1,
    @com.squareup.moshi.Json(name = "activeDevices") val activeDevices: Int = 0,
    @com.squareup.moshi.Json(name = "subscriptionStatus") val subscriptionStatus: String = "ACTIVE",
    @com.squareup.moshi.Json(name = "subscriptionStart") val subscriptionStart: Long = 0L,
    @com.squareup.moshi.Json(name = "subscriptionEnd") val subscriptionEnd: Long = 0L,
    @com.squareup.moshi.Json(name = "paymentStatus") val paymentStatus: String = "PAID",
    @com.squareup.moshi.Json(name = "paymentAmount") val paymentAmount: Double = 0.0,
    @com.squareup.moshi.Json(name = "amount_paid") val altAmountPaid: Double? = null,
    @com.squareup.moshi.Json(name = "licenseCode") val licenseCode: String? = null
) {
    val id: Long get() = (rawId as? Number)?.toLong() ?: (rawId?.toString()?.toLongOrNull() ?: 0L)
    val stringId: String get() = rawId?.toString() ?: (if (managerId.isNotBlank()) managerId else id.toString())
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AdminManagerDto(
    @com.squareup.moshi.Json(name = "id") val rawId: Any? = null,
    @com.squareup.moshi.Json(name = "name") val name: String? = null,
    @com.squareup.moshi.Json(name = "fullName") val fullName: String? = null,
    @com.squareup.moshi.Json(name = "full_name") val altFullName: String? = null,
    @com.squareup.moshi.Json(name = "username") val username: String? = null,
    @com.squareup.moshi.Json(name = "phone") val phone: String? = null,
    @com.squareup.moshi.Json(name = "phone_number") val phoneNumber: String? = null,
    @com.squareup.moshi.Json(name = "mobile") val mobile: String? = null,
    @com.squareup.moshi.Json(name = "password") val password: String? = null,
    @com.squareup.moshi.Json(name = "gameNetName") val gameNetName: String? = null,
    @com.squareup.moshi.Json(name = "gameneName") val gameneName: String? = null,
    @com.squareup.moshi.Json(name = "gamenet_name") val gamenetName: String? = null,
    @com.squareup.moshi.Json(name = "planName") val planName: String? = null,
    @com.squareup.moshi.Json(name = "plan_name") val altPlanName: String? = null,
    @com.squareup.moshi.Json(name = "amountPaid") val amountPaid: Double? = null,
    @com.squareup.moshi.Json(name = "amount_paid") val altAmountPaid: Double? = null,
    @com.squareup.moshi.Json(name = "paymentAmount") val paymentAmount: Double? = null,
    @com.squareup.moshi.Json(name = "payment_amount") val altPaymentAmount: Double? = null,
    @com.squareup.moshi.Json(name = "planType") val planType: String? = null,
    @com.squareup.moshi.Json(name = "plan_type") val altPlanType: String? = null,
    @com.squareup.moshi.Json(name = "email") val email: String? = null,
    @com.squareup.moshi.Json(name = "status") val status: String? = null,
    @com.squareup.moshi.Json(name = "paymentStatus") val paymentStatus: String? = null,
    @com.squareup.moshi.Json(name = "payment_status") val altPaymentStatus: String? = null,
    @com.squareup.moshi.Json(name = "licenseCode") val licenseCode: String? = null,
    @com.squareup.moshi.Json(name = "license_code") val altLicenseCode: String? = null,
    @com.squareup.moshi.Json(name = "firstLoginDate") val firstLoginDate: Long? = null,
    @com.squareup.moshi.Json(name = "expiryDate") val expiryDate: Long? = null,
    @com.squareup.moshi.Json(name = "expires_at") val expiresAtSnake: String? = null,
    @com.squareup.moshi.Json(name = "expires_at") val altExpiresAt: String? = null,
    @com.squareup.moshi.Json(name = "activation_date") val activationDate: String? = null,
    @com.squareup.moshi.Json(name = "expiry_date") val expiryDateStr: String? = null,
    @com.squareup.moshi.Json(name = "role") val role: String? = null,
    @com.squareup.moshi.Json(name = "is_super_admin") val isSuperAdmin: Boolean? = null,
    @com.squareup.moshi.Json(name = "is_super_manager") val isSuperManager: Boolean? = null,
    @com.squareup.moshi.Json(name = "createdAt") val createdAt: Long? = null,
    @com.squareup.moshi.Json(name = "created_at") val altCreatedAt: Long? = null
) {
    val id: Long get() = (rawId as? Number)?.toLong() ?: (rawId?.toString()?.toLongOrNull() ?: 0L)
    val stringId: String get() = rawId?.toString() ?: id.toString()
    val isSuperRole: Boolean get() {
        val r = (role ?: "").lowercase()
        val pl = (planName ?: altPlanName ?: planType ?: altPlanType ?: "").lowercase()
        val un = (username ?: "").lowercase()
        val nm = (name ?: fullName ?: "").lowercase()
        return isSuperAdmin == true || isSuperManager == true ||
               r.contains("super") || pl.contains("super") || pl.contains("ارشد") ||
               un.contains("super") || nm.contains("مدیر ارشد") || nm.contains("ارشد")
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CreateManagerRequestDto(
    @com.squareup.moshi.Json(name = "fullName") val fullName: String,
    @com.squareup.moshi.Json(name = "gameneName") val gameneName: String,
    @com.squareup.moshi.Json(name = "phone") val phone: String,
    @com.squareup.moshi.Json(name = "password") val password: String,
    @com.squareup.moshi.Json(name = "planType") val planType: String,
    @com.squareup.moshi.Json(name = "durationDays") val durationDays: Int,
    @com.squareup.moshi.Json(name = "maxDevices") val maxDevices: Int,
    @com.squareup.moshi.Json(name = "paymentAmount") val paymentAmount: Double,
    @com.squareup.moshi.Json(name = "paymentStatus") val paymentStatus: String,
    @com.squareup.moshi.Json(name = "username") val username: String? = null,
    @com.squareup.moshi.Json(name = "full_name") val altFullName: String? = null,
    @com.squareup.moshi.Json(name = "gamenet_name") val altGameNetName: String? = null,
    @com.squareup.moshi.Json(name = "plan_name") val altPlanName: String? = null,
    @com.squareup.moshi.Json(name = "amount_paid") val altAmountPaid: Long? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AssignLicenseRequestDto(
    @com.squareup.moshi.Json(name = "managerId") val managerId: String,
    @com.squareup.moshi.Json(name = "transactionId") val transactionId: String?
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UpdateManagerRequestDto(
    @com.squareup.moshi.Json(name = "status") val status: String,
    @com.squareup.moshi.Json(name = "paymentStatus") val paymentStatus: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class EditManagerRequestDto(
    @com.squareup.moshi.Json(name = "name") val name: String? = null,
    @com.squareup.moshi.Json(name = "gameNetName") val gameNetName: String? = null,
    @com.squareup.moshi.Json(name = "password") val password: String? = null,
    @com.squareup.moshi.Json(name = "planType") val planType: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CheckTrialRequest(
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String,
    @com.squareup.moshi.Json(name = "device_fingerprint") val deviceFingerprint: String? = null,
    @com.squareup.moshi.Json(name = "deviceId") val altDeviceId: String? = null,
    @com.squareup.moshi.Json(name = "deviceName") val deviceName: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CheckTrialResponse(
    @com.squareup.moshi.Json(name = "status") val status: String? = null,
    @com.squareup.moshi.Json(name = "expiresAt") val expiresAt: Long? = null,
    @com.squareup.moshi.Json(name = "serverTime") val serverTime: Long? = null,
    @com.squareup.moshi.Json(name = "hoursLeft") val hoursLeft: Double? = null,
    @com.squareup.moshi.Json(name = "hours_left") val altHoursLeft: Double? = null,
    @com.squareup.moshi.Json(name = "remainingHours") val remainingHoursField: Double? = null,
    @com.squareup.moshi.Json(name = "message") val message: String? = null,
    @com.squareup.moshi.Json(name = "responseMessage") val altResponseMessage: String? = null,
    @com.squareup.moshi.Json(name = "expired") val expired: Boolean? = null,
    @com.squareup.moshi.Json(name = "isExpired") val altIsExpired: Boolean? = null,
    @com.squareup.moshi.Json(name = "isTampered") val isTamperedFlag: Boolean? = null,
    @com.squareup.moshi.Json(name = "tampered") val tamperedFlag: Boolean? = null
) {
    val isTampered: Boolean
        get() = isTamperedFlag == true || tamperedFlag == true || status?.contains("TAMPER", ignoreCase = true) == true

    val isExpired: Boolean
        get() {
            if (isTampered) return true
            if (status != null) {
                return status.equals("expired", ignoreCase = true) || status.equals("TAMPERED_BLOCKED", ignoreCase = true)
            }
            if (remainingHoursField != null) return remainingHoursField <= 0
            if (hoursLeft != null) return hoursLeft <= 0
            if (altHoursLeft != null) return altHoursLeft <= 0
            return altIsExpired ?: expired ?: false
        }

    val remainingHours: Double
        get() = if (isTampered) 0.0 else (remainingHoursField ?: hoursLeft ?: altHoursLeft ?: (if (isExpired) 0.0 else 24.0))

    val remainingMilliseconds: Long
        get() = (remainingHours * 3600 * 1000L).toLong()

    val responseMessage: String
        get() = if (isTampered) "اکانت اشتراک ۲۴ ساعته شما به علت تقلب و دستکاری در زمان و تاریخ گوشی تان مسدود شده است در حال حاضر اجازه ورود به حساب های خریداری شده و یا تهیه اشتراک را دارید با تشکر ادمین برنامه GameNexa"
                else (altResponseMessage ?: message ?: if (isExpired) "مدت زمان تست رایگان ۲۴ ساعته این دستگاه به پایان رسیده است." else "نسخه تست فعال است.")
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ReservationDbDto(
    @com.squareup.moshi.Json(name = "id") val id: Long = 0L,
    @com.squareup.moshi.Json(name = "customer_name") val customerName: String? = null,
    @com.squareup.moshi.Json(name = "status") val status: String = "",
    @com.squareup.moshi.Json(name = "type") val reservationType: String = "",
    @com.squareup.moshi.Json(name = "start_time_millis") val reservationTimeMillis: Long = 0L,
    @com.squareup.moshi.Json(name = "duration_minutes") val durationMinutes: Int = 0,
    @com.squareup.moshi.Json(name = "station_id") val stationId: Long? = null,
    @com.squareup.moshi.Json(name = "snap_final_price") val finalPrice: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PricingPreviewRequest(
    val reservationType: String = "",
    val stationId: Long? = null,
    val durationMinutes: Int = 0,
    val reservationTimeMillis: Long = 0L,
    val controllersCount: Int = 1
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PricingPreviewResponse(
    val finalPrice: Double = 0.0,
    val basePrice: Double = 0.0,
    val discount: Double = 0.0
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AtomicReservationRequest(
    val reservationType: String = "",
    val stationId: Long? = null,
    val durationMinutes: Int = 0,
    val reservationTimeMillis: Long = 0L,
    val controllersCount: Int = 1,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val idempotencyKey: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AtomicReservationResponse(
    val success: Boolean = false,
    val message: String = "",
    val reservationId: Long? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class TrialStartRequest(
    val deviceId: String,
    val deviceFingerprint: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class TrialStartResponse(
    val success: Boolean,
    val trialActive: Boolean,
    val isExpired: Boolean,
    val remainingMinutes: Int? = null,
    val expiresAt: Long? = null,
    val serverTime: Long? = null,
    val message: String? = null
)

object NetworkClient {
    const val DEFAULT_BASE_URL = "https://api.gamenermayket.ir/"
    private var baseUrl: String = DEFAULT_BASE_URL
    private var api: GameNetApi? = null

    @Volatile
    var authToken: String? = null
    @Volatile
    var isTrialMode: Boolean = false

    fun getApi(url: String = ""): GameNetApi {
        val targetUrl = if (url.isBlank()) SelfHostedManager.SERVER_URL else url
        val sanitizedUrl = if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/"

        if (api == null || baseUrl != sanitizedUrl) {
            baseUrl = sanitizedUrl
            
            val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) okhttp3.logging.HttpLoggingInterceptor.Level.BASIC else okhttp3.logging.HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
                redactHeader("X-GameNet-Signature")
                redactHeader("X-Manager-ID")
            }

            val okHttpClientBuilder = OkHttpClient.Builder()
                .dns(GameNexaDns)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS))
                .addInterceptor(loggingInterceptor)
                .addInterceptor(NetworkLogger.createInterceptor())
                .addInterceptor(RetryInterceptor())
                .addInterceptor { chain ->
                    if (isTrialMode) {
                        val path = chain.request().url.encodedPath
                        // Trial is local-only for operational data. Only authoritative
                        // trial status/start endpoints may reach the server.
                        val isTrialStatusEndpoint =
                            path == "/api/v1/trial/status" ||
                            path == "/api/v1/trial/start" ||
                            path == "/api/v1/time"
                        if (!isTrialStatusEndpoint) {
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(409)
                                .message("Trial Local-Only Operation")
                                .body("{\"success\":false,\"code\":\"TRIAL_LOCAL_ONLY\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                                .build()
                        }
                    }
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                        .header("Accept", "application/json")

                    val currentManagerId = SelfHostedManager.currentManagerId
                    if (currentManagerId.isNotBlank()) {
                        requestBuilder.header("X-Manager-ID", currentManagerId)
                    }

                    val path = original.url.encodedPath
                    val isPublicAuthEndpoint = path.contains("auth/register") || path.contains("auth/login")

                    val token = authToken
                    if (!token.isNullOrBlank() && !isPublicAuthEndpoint) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }

                    chain.proceed(requestBuilder.build())
                }

            val okHttpClient = okHttpClientBuilder.build()

            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(AnyAdapter())
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            api = retrofit.create(GameNetApi::class.java)
        }
        return api!!
    }
}
