package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CloudLiveStation(
    val stationId: Int = 0,
    val status: String = "FREE", // "FREE", "RUNNING", "PAUSED"
    val consoleType: String = "",
    val controllerCount: Int = 1,
    val startTimeMillis: Long = 0L,
    val lastStateChangeMillis: Long = 0L,
    val elapsedPlayingTimeMillis: Long = 0L,
    val prepaymentAmount: Long = 0L,
    val durationLimitMinutes: Int = 0,
    val customerIdsStr: String = "",
    val customerNamesStr: String = "",
    val ordersJson: String = "",
    val hourlyRate: Long = 0L,
    val currentGameCost: Long = 0L,
    val currentBuffetCost: Long = 0L,
    val splitMode: String = "ALL", // "ALL", "SINGLE", "CUSTOM"
    val payerCustomerIdsStr: String = "",
    val payerCustomerNamesStr: String = ""
)

data class CloudPaymentRecord(
    val id: Long = 0L,
    val managerId: String = "",
    val customerId: Long = 0L,
    val customerName: String = "",
    val amount: Long = 0L,
    val paymentType: String = "", // "PAY_DEBT", "BUY_POINTS", "CHARGE_WALLET"
    val pointsGained: Long = 0L,
    val trackingCode: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS"
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val endpointUrl: String = SelfHostedManager.SERVER_URL,
    val liveStationsCount: Int = 0,
    val customersCount: Int = 0,
    val readSuccess: Boolean = false,
    val writeSuccess: Boolean = false,
    val message: String = ""
)

data class CloudAnnouncement(
    val id: Long = 1L,
    val message: String = "",
    val title: String = "پیام صاحب گیم‌نت برای اعضای خانواده GameNexa",
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = "مدیر ارشد گیم‌نت"
)

data class CloudAuditLog(
    val id: Long = 0L,
    val operatorName: String = "مدیر اجرایی",
    val actionTitle: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object SelfHostedManager {
    @Volatile var lastStationStartWasTransportFailure: Boolean = false
    @Volatile var lastStationStartError: String = ""
    @Volatile var lastSettlementHttpCode: Int = 0
    @Volatile var lastSettlementErrorBody: String = ""
    private const val TAG = "SelfHostedManager"
    
    // Dedicated Production Backend Server
    val candidateUrls = listOf(
        "https://api.gamenermayket.ir"
    )

    @Volatile
    private var _activeServerUrl = "https://api.gamenermayket.ir"

    val SERVER_URL: String
        get() = _activeServerUrl

    const val BASE_URL = "https://api.gamenermayket.ir/"

    @Volatile
    private var _currentManagerId: String = ""
    private val _currentManagerIdFlow = MutableStateFlow("")
    val currentManagerIdFlow: StateFlow<String> = _currentManagerIdFlow.asStateFlow()

    val currentManagerId: String
        get() = _currentManagerId

    fun setManagerId(id: String) {
        _currentManagerId = id.trim()
        _currentManagerIdFlow.value = id.trim()
        Log.i(TAG, "Current Manager ID set to: $_currentManagerId")
    }

    fun validateCustomUrl(url: String): Pair<Boolean, String> {
        val canonicalUrl = BASE_URL.removeSuffix("/")
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.equals(canonicalUrl, ignoreCase = true)) {
            return true to canonicalUrl
        }
        return false to "فقط سرور رسمی GameNexa با دامنه https://api.gamenermayket.ir مجاز است."
    }

    fun setCustomServerUrl(url: String): Boolean {
        val (isValid, resultOrError) = validateCustomUrl(url)
        if (isValid) {
            _activeServerUrl = resultOrError
            NetworkLogger.updateActiveUrl(resultOrError)
            Log.i(TAG, "Active server URL updated to secure HTTPS endpoint")
            return true
        } else {
            Log.e(TAG, "Custom server URL rejected: $resultOrError")
            return false
        }
    }

    private fun tryNextFallbackUrl() {
        val currentIndex = candidateUrls.indexOf(_activeServerUrl)
        val nextIndex = if (currentIndex >= 0 && currentIndex + 1 < candidateUrls.size) currentIndex + 1 else 0
        val nextUrl = candidateUrls[nextIndex]
        if (nextUrl != _activeServerUrl) {
            _activeServerUrl = nextUrl
            NetworkLogger.updateActiveUrl(nextUrl)
            Log.i(TAG, "Switched active server URL fallback to: $_activeServerUrl")
        }
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
        level = if (com.example.BuildConfig.DEBUG) {
            okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
        } else {
            okhttp3.logging.HttpLoggingInterceptor.Level.NONE
        }
    }

    val client = OkHttpClient.Builder()
        .dns(GameNexaDns)
        // Respect Android's active system proxy/VPN routing instead of forcing a direct path.
        .proxySelector(java.net.ProxySelector.getDefault())
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS))
        .addInterceptor(loggingInterceptor)
        .addInterceptor(NetworkLogger.createInterceptor())
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // State flows for real-time observation
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _allLiveStations = MutableStateFlow<List<CloudLiveStation>>(emptyList())
    val allLiveStations: StateFlow<List<CloudLiveStation>> = _allLiveStations.asStateFlow()

    private val _allCloudCustomers = MutableStateFlow<List<Customer>>(emptyList())
    val allCloudCustomers: StateFlow<List<Customer>> = _allCloudCustomers.asStateFlow()
    private val _archivedCloudCustomers = MutableStateFlow<List<Customer>>(emptyList())
    val archivedCloudCustomers: StateFlow<List<Customer>> = _archivedCloudCustomers.asStateFlow()

    private val _recentPayments = MutableStateFlow<List<CloudPaymentRecord>>(emptyList())
    val recentPayments: StateFlow<List<CloudPaymentRecord>> = _recentPayments.asStateFlow()

    private val _recentReservations = MutableStateFlow<List<Reservation>>(emptyList())
    val recentReservations: StateFlow<List<Reservation>> = _recentReservations.asStateFlow()

    private val _ownerBroadcastAnnouncement = MutableStateFlow<CloudAnnouncement?>(null)
    val ownerBroadcastAnnouncement: StateFlow<CloudAnnouncement?> = _ownerBroadcastAnnouncement.asStateFlow()

    private val _cloudAuditLogs = MutableStateFlow<List<CloudAuditLog>>(emptyList())
    val cloudAuditLogs: StateFlow<List<CloudAuditLog>> = _cloudAuditLogs.asStateFlow()

    private val _cloudClubLevelsJson = MutableStateFlow<String?>(null)
    val cloudClubLevelsJson: StateFlow<String?> = _cloudClubLevelsJson.asStateFlow()

    private val _cloudAppConfigs = MutableStateFlow<Map<String, String>>(emptyMap())
    val cloudAppConfigs: StateFlow<Map<String, String>> = _cloudAppConfigs.asStateFlow()

    // Logged-in customer session for Customer App
    private val _currentLoggedInCustomer = MutableStateFlow<Customer?>(null)
    val currentLoggedInCustomer: StateFlow<Customer?> = _currentLoggedInCustomer.asStateFlow()

    private val _isCustomerKickedOut = MutableStateFlow(false)
    val isCustomerKickedOut: StateFlow<Boolean> = _isCustomerKickedOut.asStateFlow()

    fun resetKickedOutFlag() {
        _isCustomerKickedOut.value = false
    }

    private val _gnToTomanPricePer10 = MutableStateFlow(60000L)
    val gnToTomanPricePer10: StateFlow<Long> = _gnToTomanPricePer10.asStateFlow()

    private val _gnPointsPerGameHour = MutableStateFlow(50L)
    val gnPointsPerGameHour: StateFlow<Long> = _gnPointsPerGameHour.asStateFlow()

    fun setGnRatios(pricePer10: Long, pointsPerHour: Long) {
        _gnToTomanPricePer10.value = pricePer10
        _gnPointsPerGameHour.value = pointsPerHour
    }

    fun setCurrentCustomer(customer: Customer?) {
        _currentLoggedInCustomer.value = customer
    }

    fun getBaseHeaders(): Headers {
        val builder = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Accept", "application/json")
            
        if (_currentManagerId.isNotBlank()) {
            builder.add("X-Manager-ID", _currentManagerId)
        }
        NetworkClient.authToken?.takeIf { it.isNotBlank() }?.let {
            builder.add("Authorization", "Bearer $it")
        }
        
        return builder.build()
    }

    fun getCustomerHeaders(): Headers {
        val builder = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Accept", "application/json")
        NetworkClient.customerAuthToken?.takeIf { it.isNotBlank() }?.let {
            builder.add("Authorization", "Bearer $it")
        }
        return builder.build()
    }

    // --- Customer Authentication & Management ---

    suspend fun addPointLog(log: PointLog): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", System.currentTimeMillis() + (0..1000).random())
                put("customerId", log.customerId)
                put("title", log.title)
                put("points", log.points)
                put("timestamp", log.timestamp)
            }
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/club/point-logs")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "addPointLog error: ${e.message}", e)
            false
        }
    }

    suspend fun fetchCustomerPointLogs(customerId: Long): List<PointLog> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/club/point-logs/$customerId")
                .headers(getBaseHeaders())
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val array = if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<PointLog>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        PointLog(
                            id = obj.optLong("id", 0L),
                            customerId = obj.optLong("customerId", obj.optLong("customer_id", 0L)),
                            title = obj.optString("title", ""),
                            points = obj.optLong("points", 0L),
                            timestamp = obj.optLong("timestamp", 0L)
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCustomerPointLogs error: ${e.message}", e)
        }
        return@withContext emptyList()
    }

    fun toEnglishDigits(text: String): String {
        var result = text
        val persian = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        val arabic = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        for (i in 0..9) {
            result = result.replace(persian[i], i.toString()).replace(arabic[i], i.toString())
        }
        return result
    }

    fun normalizePhone(phone: String): String {
        val clean = toEnglishDigits(phone.trim())
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+98", "0")
            .replace("0098", "0")
        return clean
    }

    fun isCustomerMatch(input: String, custPhone: String, custName: String, inviteCode: String = ""): Boolean {
        val cleanInput = toEnglishDigits(input.trim())
        val normInput = normalizePhone(input)
        val normCustPhone = normalizePhone(custPhone)

        // 1. Phone Match
        if (normInput.isNotBlank() && normCustPhone.isNotBlank()) {
            if (normInput == normCustPhone) return true
            if (normInput.trimStart('0') == normCustPhone.trimStart('0')) return true
        }

        // 2. Full Name Match
        val cleanName = custName.trim().replace("\u200c", " ").replace("\\s+".toRegex(), " ")
        val cleanInputName = input.trim().replace("\u200c", " ").replace("\\s+".toRegex(), " ")
        if (cleanName.isNotBlank() && cleanName.equals(cleanInputName, ignoreCase = true)) return true
        if (cleanName.isNotBlank() && cleanInput.equals(cleanName, ignoreCase = true)) return true

        // 3. Invite Code Match
        if (inviteCode.isNotBlank()) {
            if (inviteCode.equals(cleanInput, ignoreCase = true)) return true
            if (inviteCode.removePrefix("GN-").equals(cleanInput.removePrefix("GN-"), ignoreCase = true)) return true
        }

        return false
    }

    suspend fun restoreCustomerSession(managerId: String, token: String): Customer? = withContext(Dispatchers.IO) {
        val mid = managerId.trim()
        val authToken = token.trim()
        if (mid.isBlank() || authToken.isBlank()) return@withContext null
        try {
            setManagerId(mid)
            NetworkClient.customerAuthToken = authToken
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/profile")
                .headers(getCustomerHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val customer = parseCustomerObject(JSONObject(body))
                if (customer.id <= 0L) return@withContext null
                _currentLoggedInCustomer.value = customer
                _isConnected.value = true
                customer
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loginCustomer(phoneNumberOrUsername: String, passwordText: String): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val cleanInput = toEnglishDigits(phoneNumberOrUsername.trim())
            val cleanPass = toEnglishDigits(passwordText.trim())
            val normInput = normalizePhone(phoneNumberOrUsername)

            if (cleanInput.isBlank() || cleanPass.isBlank()) {
                return@withContext Result.failure(Exception("لطفاً نام کاربری/شماره همراه و رمز عبور را وارد کنید."))
            }

            val managerId = currentManagerId.trim()
            if (managerId.isBlank()) {
                return@withContext Result.failure(Exception("شناسه مدیر برای ورود مشتری مشخص نشده است."))
            }

            val jsonBody = JSONObject().apply {
                put("phone_number", normInput.ifBlank { cleanInput })
                put("manager_id", managerId)
                put("password", cleanPass)
            }

            val request = Request.Builder()
                .url("$SERVER_URL/api/auth/customer/login")
                .headers(getBaseHeaders())
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(Exception(if (response.code == 401) "رمز عبور وارد شده اشتباه است." else "ورود مشتری ناموفق بود."))
            }

            val auth = JSONObject(body)
            val token = auth.optString("token", "")
            val customerId = auth.optLong("customerId", 0L)
            if (token.isBlank() || customerId <= 0L) {
                return@withContext Result.failure(Exception("پاسخ احراز هویت مشتری معتبر نیست."))
            }

            NetworkClient.customerAuthToken = token
            val profileRequest = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/profile")
                .headers(getCustomerHeaders())
                .get()
                .build()
            val profileResponse = client.newCall(profileRequest).execute()
            val profileBody = profileResponse.body?.string() ?: ""
            if (!profileResponse.isSuccessful || profileBody.isBlank()) {
                NetworkClient.customerAuthToken = null
                return@withContext Result.failure(Exception("دریافت اطلاعات مشتری پس از ورود ناموفق بود."))
            }

            val cust = parseCustomerObject(JSONObject(profileBody))
            setManagerId(managerId)
            _currentLoggedInCustomer.value = cust
            _isConnected.value = true
            return@withContext Result.success(cust)
            Result.failure(Exception("هیچ حساب کاربری با این مشخصات یافت نشد یا توسط مدیریت حذف شده است."))
        } catch (e: Exception) {
            Log.e(TAG, "loginCustomer error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun refreshCurrentCustomerProfile(): Customer? = withContext(Dispatchers.IO) {
        try {
            if (NetworkClient.customerAuthToken.isNullOrBlank()) return@withContext null
            val request = Request.Builder().url("$SERVER_URL/api/v1/customer/profile").headers(getCustomerHeaders()).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val customer = parseCustomerObject(JSONObject(body))
                _currentLoggedInCustomer.value = customer
                customer
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshCurrentCustomerProfile error: " + e.message)
            null
        }
    }

    suspend fun registerCustomer(
        fullName: String,
        phone: String,
        passwordText: String,
        invitedByCode: String = ""
    ): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val genInviteCode = "GN" + (1000..9999).random()
            val managerId = currentManagerId.trim()
            val normPhone = normalizePhone(phone)
            if (managerId.isBlank()) return@withContext Result.failure(Exception("شناسه مدیر برای ثبت‌نام مشتری مشخص نشده است."))
            if (passwordText.trim().length < 8) return@withContext Result.failure(Exception("رمز عبور باید حداقل 8 کاراکتر باشد."))
            
            val json = JSONObject().apply {
                put("full_name", fullName.trim())
                put("phone_number", normPhone)
                put("manager_id", managerId)
                put("password", passwordText.trim())
                put("inviteCode", genInviteCode)
                put("invitedByCode", invitedByCode.trim())
            }

            val request = Request.Builder()
                .url("$SERVER_URL/api/auth/customer/register")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val jsonObj = JSONObject(body)
                if (jsonObj.optBoolean("success", true)) {
                    val cObj = if (jsonObj.has("customer")) jsonObj.getJSONObject("customer") else jsonObj
                    val newCust = parseCustomerObject(cObj)
                    val authToken = jsonObj.optString("token", "")
                    if (authToken.isNotBlank()) NetworkClient.customerAuthToken = authToken
                    setManagerId(managerId)
                    
                    val currentList = _allCloudCustomers.value.toMutableList()
                    currentList.add(0, newCust)
                    _allCloudCustomers.value = currentList
                    _currentLoggedInCustomer.value = newCust
                    _isConnected.value = true

                    return@withContext Result.success(newCust)
                } else {
                    val msg = jsonObj.optString("message", "خطا در ثبت‌نام مشتری")
                    return@withContext Result.failure(Exception(msg))
                }
            }

            Result.failure(Exception("پاسخ نامعتبر از سرور دریافت شد"))
        } catch (e: Exception) {
            Log.e(TAG, "registerCustomer error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncAllCustomersToCloud(customers: List<Customer>): Boolean = withContext(Dispatchers.IO) {
        if (customers.isEmpty()) return@withContext true
        try {
            var successCount = 0
            for (c in customers) {
                if (upsertCustomer(c)) successCount++
            }
            successCount == customers.size
        } catch (e: Exception) {
            Log.w(TAG, "syncAllCustomersToCloud failed: ${e.message}")
            false
        }
    }

    suspend fun checkCustomerExists(customerId: Long, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        val normPhone = normalizePhone(phoneNumber)
        _allCloudCustomers.value.any { it.id == customerId || (normPhone.isNotBlank() && normalizePhone(it.phoneNumber) == normPhone) }
    }

    suspend fun upsertCustomer(customer: Customer): Boolean = withContext(Dispatchers.IO) {
        try {
            val json=JSONObject().apply {
                put("id",customer.id); put("fullName",customer.fullName); put("phoneNumber",customer.phoneNumber)
                put("debt",customer.debt); put("credit",customer.credit); put("tier",customer.tier)
                // LP is server-authoritative. A stale local zero must never erase an earned
                // server LP balance during an unrelated customer/profile sync.
                if (customer.lp > 0L) put("lp", customer.lp)
                put("availableGn",customer.availableGn); put("pendingGn",customer.pendingGn); put("inviteCode",customer.inviteCode); put("invitedByCode",customer.invitedByCode); put("description",customer.description)
            }
            val req=Request.Builder().url("$SERVER_URL/api/v1/manager/customers").headers(getBaseHeaders()).post(json.toString().toRequestBody(JSON_MEDIA)).build()
            client.newCall(req).execute().use { resp ->
                if(!resp.isSuccessful) return@withContext false
                val list=_allCloudCustomers.value.toMutableList(); val idx=list.indexOfFirst{it.id==customer.id || it.phoneNumber==customer.phoneNumber}; if(idx>=0) list[idx]=customer else list.add(0,customer); _allCloudCustomers.value=list; true
            }
        } catch(e:Exception){Log.e(TAG,"upsertCustomer error: ${e.message}",e);false}
    }

    suspend fun deleteCustomer(customerId: Long, phoneNumber: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val req=Request.Builder().url("$SERVER_URL/api/v1/manager/customers/$customerId").headers(getBaseHeaders()).delete().build()
            client.newCall(req).execute().use { resp ->
                if(!resp.isSuccessful) return@withContext false
                _allCloudCustomers.value=_allCloudCustomers.value.filterNot{it.id==customerId || (phoneNumber.isNotBlank() && normalizePhone(it.phoneNumber)==normalizePhone(phoneNumber))}
                if(_currentLoggedInCustomer.value?.id==customerId){_currentLoggedInCustomer.value=null;_isCustomerKickedOut.value=true}
                true
            }
        } catch(e:Exception){Log.e(TAG,"deleteCustomer error: ${e.message}",e);false}
    }

    suspend fun deleteCustomersBatch(customerIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        if(customerIds.isEmpty()) return@withContext true
        try {
            val body=JSONObject().apply{put("customerIds",JSONArray(customerIds))}
            val req=Request.Builder().url("$SERVER_URL/api/v1/manager/customers/delete-batch").headers(getBaseHeaders()).post(body.toString().toRequestBody(JSON_MEDIA)).build()
            client.newCall(req).execute().use{resp-> if(!resp.isSuccessful)return@withContext false}
            _allCloudCustomers.value=_allCloudCustomers.value.filterNot{it.id in customerIds}; true
        }catch(e:Exception){Log.e(TAG,"deleteCustomersBatch error: ${e.message}",e);false}
    }

    suspend fun purgeAllOrphanCustomersExcept(validCustomerIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentList = _allCloudCustomers.value
            val toDelete = currentList.filter { it.id !in validCustomerIds }
            for (c in toDelete) {
                deleteCustomer(c.id, c.phoneNumber)
            }
            fetchAllFromCloud()
            true
        } catch (e: Exception) {
            Log.e(TAG, "purgeAllOrphanCustomers error: ${e.message}", e)
            false
        }
    }


    suspend fun purgeArchivedCustomer(customerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/customers/$customerId/purge")
                .headers(getBaseHeaders())
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                _archivedCloudCustomers.value = _archivedCloudCustomers.value.filterNot { it.id == customerId }
                _allCloudCustomers.value = _allCloudCustomers.value.filterNot { it.id == customerId }
                if (_currentLoggedInCustomer.value?.id == customerId) {
                    _currentLoggedInCustomer.value = null
                    _isCustomerKickedOut.value = true
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "purgeArchivedCustomer error: ${e.message}", e)
            false
        }
    }

    suspend fun fetchArchivedCustomersFromCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/customers/archived")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body?.string().orEmpty()
                val list = ArrayList<Customer>()
                if (body.isNotBlank()) {
                    extractCustomerObjects(if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).opt("data") ?: JSONArray(), list)
                }
                _archivedCloudCustomers.value = list
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchArchivedCustomersFromCloud error: " + e.message)
            false
        }
    }

    suspend fun fetchAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req=Request.Builder().url("$SERVER_URL/api/v1/manager/customers").headers(getBaseHeaders()).get().build()
            client.newCall(req).execute().use{resp->
                val body=resp.body?.string().orEmpty(); if(!resp.isSuccessful||body.isBlank()){_isConnected.value=false;return@withContext false}
                val list=ArrayList<Customer>(); extractCustomerObjects(if(body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).opt("data")?:JSONArray(),list)
                _allCloudCustomers.value=list; _isConnected.value=true
                try{fetchRecentPaymentsFromCloud();fetchAllReservationsFromCloud()}catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in SelfHostedManager.kt", e) }
                true
            }
        }catch(e:Exception){_isConnected.value=false;Log.w(TAG,"fetchAllFromCloud error: ${e.message}");false}
    }

    suspend fun fetchLiveStationsFromCloud() = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/live-stations")
                .headers(getBaseHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val list = mutableListOf<CloudLiveStation>()
                val parseAndAdd = { obj: JSONObject ->
                    val mgrId = obj.optString("manager_id", obj.optString("managerId", ""))
                    if (false) {
                        if (mgrId == _currentManagerId || mgrId.isBlank()) {
                            list.add(parseCloudLiveStation(obj))
                        }
                    } else {
                        list.add(parseCloudLiveStation(obj))
                    }
                }
                
                if (body.trim().startsWith("[")) {
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        parseAndAdd(obj)
                    }
                } else if (body.trim().startsWith("{")) {
                    val obj = JSONObject(body)
                    val array = obj.optJSONArray("data") ?: obj.optJSONArray("stations")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val stObj = array.optJSONObject(i) ?: continue
                            parseAndAdd(stObj)
                        }
                    }
                }
                _allLiveStations.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLiveStationsFromCloud error: ${e.message}", e)
        }
    }

    private fun parseCloudLiveStation(obj: JSONObject): CloudLiveStation {
        return CloudLiveStation(
            stationId = obj.optInt("stationId", obj.optInt("station_id", obj.optInt("id", 0))),
            status = obj.optString("status", "FREE"),
            consoleType = obj.optString("consoleType", obj.optString("console_type", "")),
            controllerCount = obj.optInt("controllerCount", obj.optInt("controller_count", 1)),
            startTimeMillis = obj.optLong("startTimeMillis", obj.optLong("start_time_millis", 0L)),
            lastStateChangeMillis = obj.optLong("lastStateChangeTimeMillis", obj.optLong("lastStateChangeMillis", obj.optLong("last_state_change_millis", 0L))),
            elapsedPlayingTimeMillis = obj.optLong("elapsedPlayingTimeMillis", obj.optLong("elapsed_playing_time_millis", 0L)),
            prepaymentAmount = obj.optLong("prepaymentAmount", obj.optLong("prepayment_amount", 0L)),
            durationLimitMinutes = obj.optInt("durationLimitMinutes", obj.optInt("duration_limit_minutes", 0)),
            customerIdsStr = obj.optString("selectedCustomerIdsStr", obj.optString("customerIdsStr", obj.optString("customer_ids_str", ""))),
            customerNamesStr = obj.optString("selectedCustomerNamesStr", obj.optString("customerNamesStr", obj.optString("customer_names_str", ""))),
            ordersJson = obj.optString("ordersJson", obj.optString("orders_json", "")),
            hourlyRate = obj.optLong("hourlyRate", obj.optLong("hourly_rate", 0L)),
            currentGameCost = obj.optLong("currentGameCost", obj.optLong("current_game_cost", 0L)),
            currentBuffetCost = obj.optLong("currentBuffetCost", obj.optLong("current_buffet_cost", 0L)),
            splitMode = obj.optString("splitMode", obj.optString("split_mode", "ALL")),
            payerCustomerIdsStr = obj.optString("payerCustomerIdsStr", obj.optString("payer_customer_ids_str", "")),
            payerCustomerNamesStr = obj.optString("payerCustomerNamesStr", obj.optString("payer_customer_names_str", ""))
        )
    }

    private suspend fun fetchRecentPaymentsFromCloud() = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/manual-payment-requests")
                .headers(getBaseHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val list = mutableListOf<CloudPaymentRecord>()
                if (body.trim().startsWith("[")) {
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        list.add(parseCloudPaymentRecord(obj))
                    }
                } else if (body.trim().startsWith("{")) {
                    val obj = JSONObject(body)
                    val array = obj.optJSONArray("data") ?: obj.optJSONArray("payments")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val pObj = array.optJSONObject(i) ?: continue
                            list.add(parseCloudPaymentRecord(pObj))
                        }
                    }
                }
                _recentPayments.value = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRecentPaymentsFromCloud error: ${e.message}")
        }
    }

    private fun parseCloudPaymentRecord(obj: JSONObject): CloudPaymentRecord {
        return CloudPaymentRecord(
            id = obj.optLong("id", 0L),
            managerId = obj.optString("manager_id", obj.optString("managerId", "")),
            customerId = obj.optLong("customerId", obj.optLong("customer_id", 0L)),
            customerName = obj.optString("customerName", obj.optString("customer_name", "")),
            amount = obj.optLong("amount", 0L),
            paymentType = obj.optString("transactionType", obj.optString("transaction_type", obj.optString("paymentType", ""))),
            pointsGained = obj.optLong("pointsGained", obj.optLong("gnAmount", 0L)),
            trackingCode = obj.optString("trackingCode", obj.optString("tracking_code", "")),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            status = obj.optString("status", "PENDING")
        )
    }

    private fun extractCustomerObjects(item: Any?, list: MutableList<Customer>) {
        when (item) {
            is JSONObject -> {
                val mgrId = item.optString("manager_id", item.optString("managerId", ""))
                if (false) {
                    if (mgrId == _currentManagerId || mgrId.isBlank()) {
                        list.add(parseCustomerObject(item))
                    }
                } else {
                    list.add(parseCustomerObject(item))
                }
            }
            is JSONArray -> {
                for (i in 0 until item.length()) {
                    val child = item.opt(i)
                    if (child != null) {
                        extractCustomerObjects(child, list)
                    }
                }
            }
        }
    }

    private fun parseCustomerObject(obj: JSONObject): Customer {
        val rawId = obj.opt("id")
        val resolvedId = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull() ?: (rawId.hashCode().toLong().let { if (it < 0) -it else it })
            else -> System.currentTimeMillis()
        }
        val name = obj.optString("customer_name", obj.optString("customerName", obj.optString("fullName", obj.optString("full_name", obj.optString("name", "")))))
        val phone = obj.optString("customer_phone", obj.optString("customerPhone", obj.optString("phoneNumber", obj.optString("phone_number", obj.optString("phone", obj.optString("mobile", ""))))))
        return Customer(
            id = if (resolvedId > 0) resolvedId else System.currentTimeMillis(),
            fullName = name,
            phoneNumber = phone,
            debt = obj.optLong("debt", 0L),
            credit = obj.optLong("credit", 0L),
            points = obj.optLong("points", 0L),
            availableGn = obj.optLong("availableGn", obj.optLong("available_gn", 0L)),
            pendingGn = obj.optLong("pendingGn", obj.optLong("pending_gn", 0L)),
            lp = obj.optLong("lp", obj.optLong("lp_balance", 0L)),
            tier = obj.optString("tier", "BRONZE"),
            inviteCode = obj.optString("inviteCode", obj.optString("invite_code", "")),
            invitedByCode = obj.optString("invitedByCode", obj.optString("invited_by_code", "")),
            description = obj.optString("description", "")
        )
    }

    suspend fun recordOnlinePayment(
        customerId: Long,
        customerName: String,
        amount: Long,
        paymentType: String,
        trackingCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("customerId", customerId)
                put("customerName", customerName)
                put("amount", amount)
                put("paymentType", paymentType)
                put("trackingCode", trackingCode)
                put("status", "SUCCESS")
                put("timestamp", System.currentTimeMillis())
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/manual-payment-requests")
                .headers(getCustomerHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "recordOnlinePayment error: ${e.message}", e)
            false
        }
    }

    suspend fun approvePendingPayment(payment: CloudPaymentRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", payment.id)
                put("customerId", payment.customerId)
                put("status", "SUCCESS")
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/manual-payment-requests/${payment.id}/approve")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "approvePendingPayment error: ${e.message}", e)
            false
        }
    }

    suspend fun syncAppConfig(key: String, valueString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val updated = _cloudAppConfigs.value.toMutableMap()
            updated[key] = valueString
            _cloudAppConfigs.value = updated

            val settings = JSONObject().apply {
                put(key, valueString)
            }
            val json = JSONObject().apply {
                put("settings", settings)
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/configuration")
                .headers(getBaseHeaders())
                .put(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "syncAppConfig error: ${e.message}", e)
            false
        }
    }

    suspend fun getServerTimeMs(): Long = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(SERVER_URL)
            .head()
            .build()
        val resp = client.newCall(req).execute()
        val dateStr = resp.header("Date")
        if (dateStr != null) {
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            format.parse(dateStr)?.time ?: throw Exception("Invalid Date header")
        } else {
            throw Exception("No Date header found")
        }
    }

    suspend fun updateManagerConfiguration(settings: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("settings", settings) }
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/configuration")
                .headers(getBaseHeaders())
                .put(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful || raw.isBlank()) return@withContext null
                JSONObject(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateManagerConfiguration error: ${e.message}")
            null
        }
    }

    suspend fun fetchManagerConfiguration(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/configuration")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@withContext null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchManagerConfiguration error: ${e.message}")
            null
        }
    }

    suspend fun saveManagerSetting(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("key", key); put("value", value) }
                .toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/settings")
                .headers(getBaseHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Manager setting sync failed: HTTP " + response.code + " " + responseBody)
                    return@withContext false
                }
                // Keep the in-memory cloud configuration cache coherent with the value
                // just persisted; otherwise the next sync can resurrect the old value.
                val updated = _cloudAppConfigs.value.toMutableMap()
                updated[key] = value
                _cloudAppConfigs.value = updated
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "saveManagerSetting error: ${e.message}")
            false
        }
    }

    suspend fun fetchAppConfig(key: String): String? = withContext(Dispatchers.IO) {
        _cloudAppConfigs.value[key]?.let { return@withContext it }
        try {
            val customerIdParam = _currentLoggedInCustomer.value?.id?.let { "?customerId=$it" } ?: ""
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/settings/$key")
                .headers(getBaseHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val value = json.optString("value", "")
                if (value.isNotBlank()) {
                    val updated = _cloudAppConfigs.value.toMutableMap()
                    updated[key] = value
                    _cloudAppConfigs.value = updated
                    return@withContext value
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppConfig error: ${e.message}")
        }
        null
    }

    suspend fun syncClubLevels(jsonString: String): Boolean = syncAppConfig("club_levels", jsonString)

    suspend fun publishBroadcastMessage(message: String, title: String = "پیام صاحب گیم‌نت برای اعضای خانواده GameNexa"): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("title", title)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/announcements")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "publishBroadcastMessage error: ${e.message}")
            false
        }
    }

    suspend fun logOperatorAction(operatorName: String, actionTitle: String, details: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val logItem = CloudAuditLog(
                id = System.currentTimeMillis(),
                operatorName = operatorName,
                actionTitle = actionTitle,
                details = details,
                timestamp = System.currentTimeMillis()
            )

            val currentLogs = _cloudAuditLogs.value.toMutableList()
            currentLogs.add(0, logItem)
            _cloudAuditLogs.value = currentLogs

            val json = JSONObject().apply {
                put("operatorName", operatorName)
                put("actionTitle", actionTitle)
                put("details", details)
                put("timestamp", logItem.timestamp)
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/audit-logs")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "logOperatorAction error: ${e.message}")
            false
        }
    }

    suspend fun addGnLedgerEntry(entry: GnLedgerEntry): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("customerId", entry.customerId)
                put("customerName", entry.customerName)
                put("gnAmount", entry.gnAmount)
                put("transactionType", entry.transactionType)
                put("source", entry.source)
                put("status", entry.status)
                put("timestamp", entry.timestamp)
                put("referenceId", entry.referenceId)
                put("description", entry.description)
            }

            val idem = entry.referenceId.trim().takeIf { it.isNotBlank() }
                ?: "gn-ledger:${entry.customerId}:${entry.timestamp}:${entry.gnAmount}:${entry.transactionType}"
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/club/ledger")
                .headers(getBaseHeaders().newBuilder().add("Idempotency-Key", idem).build())
                .post(json.put("idempotencyKey", idem).toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "addGnLedgerEntry error: ${e.message}")
            false
        }
    }

    suspend fun fetchGnLedgerForCustomer(customerId: Long): List<GnLedgerEntry> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/club/ledger")
                .headers(getCustomerHeaders())
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val array = if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<GnLedgerEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        GnLedgerEntry(
                            id = obj.optLong("id", 0L),
                            customerId = obj.optLong("customerId", obj.optLong("customer_id", 0L)),
                            customerName = obj.optString("customerName", obj.optString("customer_name", "")),
                            gnAmount = obj.optLong("gnAmount", obj.optLong("gn_amount", 0L)),
                            transactionType = obj.optString("transactionType", obj.optString("transaction_type", "")),
                            source = obj.optString("source", "EARNED"),
                            status = obj.optString("status", "AVAILABLE"),
                            timestamp = obj.optLong("timestamp", 0L),
                            referenceId = obj.optString("referenceId", obj.optString("reference_id", "")),
                            description = obj.optString("description", "")
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchGnLedgerForCustomer error: ${e.message}")
        }
        emptyList<GnLedgerEntry>()
    }

    suspend fun syncCustomerToCloud(customer: Customer): Boolean = withContext(Dispatchers.IO) {
        upsertCustomer(customer)
    }

    suspend fun checkAuthenticatedManagerSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/auth/check")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _isConnected.value = true
                    NetworkLogger.dismissErrorBanner()
                    true
                } else {
                    Log.w(TAG, "Authenticated manager session rejected: HTTP " + response.code)
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Authenticated manager session check failed: " + e.message)
            _isConnected.value = false
            false
        }
    }

    suspend fun fetchServerDiagnostics(): List<NetworkLogEntry> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/diagnostics")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val root = JSONObject(response.body?.string().orEmpty())
                val rows = root.optJSONArray("logs") ?: JSONArray()
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        add(NetworkLogEntry(
                            id = row.optLong("timestamp", System.currentTimeMillis()) + i,
                            method = row.optString("method", "SERVER"),
                            url = SERVER_URL + row.optString("path", ""),
                            statusCode = row.optInt("statusCode", 0),
                            durationMs = row.optLong("durationMs", 0L),
                            isSuccess = row.optInt("statusCode", 500) in 200..399,
                            errorMessage = row.optString("code", "").ifBlank { if (row.optInt("statusCode", 500) >= 400) "Server HTTP ${row.optInt("statusCode", 500)}" else null },
                            errorType = row.optString("code", "SERVER_REQUEST").ifBlank { "SERVER_REQUEST" }
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchServerDiagnostics error: ${e.message}")
            emptyList()
        }
    }

    suspend fun testServerConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetUrl = BASE_URL.removeSuffix("/")
        try {
            val api = NetworkClient.getApi(targetUrl)
            api.getServerTime()
            val latency = System.currentTimeMillis() - startTime
            _activeServerUrl = targetUrl
            NetworkLogger.updateActiveUrl(targetUrl)
            _isConnected.value = true
            NetworkLogger.dismissErrorBanner()
            ConnectionTestResult(
                isSuccess = true,
                latencyMs = latency,
                endpointUrl = BASE_URL,
                liveStationsCount = 0,
                customersCount = 0,
                readSuccess = true,
                writeSuccess = false,
                message = "اتصال پایدار با سرور و دریافت ساعت رسمی برقرار شد."
            )
        } catch (e: Exception) {
            _isConnected.value = false
            val latency = System.currentTimeMillis() - startTime
            ConnectionTestResult(
                isSuccess = false,
                latencyMs = latency,
                endpointUrl = BASE_URL,
                liveStationsCount = 0,
                customersCount = 0,
                readSuccess = false,
                writeSuccess = false,
                message = "عدم برقراری ارتباط با سرور: ${e.localizedMessage ?: "پاسخی از سرور دریافت نشد"}"
            )
        }
    }

    suspend fun syncCustomerTransactionToCloud(transaction: CustomerTransaction): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                if (false) {
                    put("manager_id", _currentManagerId)
                    put("managerId", _currentManagerId)
                }
                put("localId", transaction.id)
                put("customerId", transaction.customerId)
                put("customerName", transaction.customerName)
                put("stationName", transaction.stationName)
                put("title", transaction.title)
                put("amount", transaction.amount)
                put("paidAmount", transaction.paidAmount)
                put("status", transaction.status)
                put("dateStr", transaction.dateStr)
                put("timeStr", transaction.timeStr)
                put("segmentDetails", transaction.segmentDetails)
                put("buffetDetails", transaction.buffetDetails)
                put("timestamp", transaction.timestamp)
                put("playMinutes", transaction.playMinutes)
                put("play_minutes", transaction.playMinutes)
                put("gameCost", transaction.gameCost)
                put("game_cost", transaction.gameCost)
                put("foodCost", transaction.foodCost)
                put("food_cost", transaction.foodCost)
            }
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/customer-transactions")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "syncCustomerTransactionToCloud error: ${e.message}", e)
            false
        }
    }

    suspend fun deleteManagerCustomerTransaction(transactionId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/customer-transactions/$transactionId")
                .headers(getBaseHeaders())
                .delete()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "deleteManagerCustomerTransaction error: ${e.message}", e)
            false
        }
    }

    /** Manager-scoped financial history. A successful empty response is authoritative. */
    suspend fun fetchManagerCustomerTransactions(): List<CustomerTransaction>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/customer-transactions")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val data = if (body.trim().startsWith("[")) JSONArray(body)
                else JSONObject(body).optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<CustomerTransaction>()
                for (i in 0 until data.length()) {
                    val o = data.optJSONObject(i) ?: continue
                    val id = o.optLong("id", o.optLong("localId", 0L))
                    val customerId = o.optLong("customerId", o.optLong("customer_id", 0L))
                    val ts = o.optLong("timestamp", o.optLong("event_timestamp", 0L))
                    if (id <= 0L || customerId <= 0L) continue
                    list += CustomerTransaction(
                        id = id,
                        customerId = customerId,
                        customerName = o.optString("customerName", o.optString("customer_name", "")),
                        stationName = o.optString("stationName", o.optString("station_name", "")),
                        title = o.optString("title", ""),
                        amount = o.optLong("amount", 0L),
                        paidAmount = o.optLong("paidAmount", o.optLong("paid_amount", 0L)),
                        status = o.optString("status", "UNREVIEWED"),
                        dateStr = o.optString("dateStr", o.optString("date_str", "")),
                        timeStr = o.optString("timeStr", o.optString("time_str", "")),
                        segmentDetails = o.optString("segmentDetails", o.optString("segment_details", "")),
                        buffetDetails = o.optString("buffetDetails", o.optString("buffet_details", "")),
                        timestamp = ts,
                        playMinutes = o.optInt("playMinutes", o.optInt("play_minutes", 0)),
                        gameCost = o.optLong("gameCost", o.optLong("game_cost", 0L)),
                        foodCost = o.optLong("foodCost", o.optLong("food_cost", 0L))
                    )
                }
                list.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchManagerCustomerTransactions error: " + e.message, e)
            null
        }
    }

    suspend fun fetchCustomerTransactionsForCustomer(customerId: Long): List<CustomerTransaction> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/transactions")
                .headers(getCustomerHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val bodyStr = resp.body?.string() ?: return@withContext emptyList()
            
            val data = if (bodyStr.trim().startsWith("[")) {
                org.json.JSONArray(bodyStr)
            } else {
                val obj = JSONObject(bodyStr)
                obj.optJSONArray("data") ?: obj.optJSONArray("customer-transactions") ?: org.json.JSONArray()
            }
            
            val list = mutableListOf<CustomerTransaction>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val cid = item.optLong("customerId", 0L)
                if (cid == customerId) {
                    val localId = if (item.has("id")) item.optLong("id", 0L) else item.optLong("localId", 0L)
                    val ts = item.optLong("timestamp", 0L)
                    val finalId = if (localId > 0L) localId else if (ts > 0L) ts else (i + 1).toLong()
                    list.add(CustomerTransaction(
                        id = finalId,
                        customerId = cid,
                        customerName = item.optString("customerName", ""),
                        stationName = item.optString("stationName", ""),
                        title = item.optString("title", ""),
                        amount = item.optLong("amount", 0L),
                        paidAmount = item.optLong("paidAmount", 0L),
                        status = item.optString("status", ""),
                        dateStr = item.optString("dateStr", ""),
                        timeStr = item.optString("timeStr", ""),
                        segmentDetails = item.optString("segmentDetails", ""),
                        buffetDetails = item.optString("buffetDetails", ""),
                        timestamp = ts,
                        playMinutes = item.optInt("playMinutes", item.optInt("play_minutes", 0)),
                        gameCost = item.optLong("gameCost", item.optLong("game_cost", 0L)),
                        foodCost = item.optLong("foodCost", item.optLong("food_cost", 0L))
                    ))
                }
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchManagerStations(): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/stations")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("stations")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchManagerStations error: " + e.message)
            null
        }
    }

    suspend fun syncReservationToCloud(reservation: Reservation, stationId: Long, isVip: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", if (reservation.id > 0) reservation.id else System.currentTimeMillis())
                put("fullName", reservation.fullName)
                put("phoneNumber", reservation.phoneNumber)
                put("reservationTimeMillis", reservation.reservationTimeMillis)
                put("durationMinutes", reservation.durationMinutes)
                put("status", reservation.status)
                put("stationId", stationId)
                put("type", if (isVip) "FULL_HALL" else "NORMAL_RESERVATION")
                put("isVip", isVip)
                put("idempotencyKey", "manager-reservation:" + reservation.phoneNumber + ":" + stationId + ":" + reservation.reservationTimeMillis + ":" + reservation.durationMinutes)
            }
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservations")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(req).execute()

            logOperatorAction(
                operatorName = reservation.fullName,
                actionTitle = "درخواست رزرو جدید 🎮",
                details = "شماره: ${reservation.phoneNumber} | مدت: ${reservation.durationMinutes} دقیقه"
            )

            resp.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "syncReservationToCloud warning: ${e.message}")
            false
        }
    }

    suspend fun fetchAllReservationsFromCloud(): List<Reservation> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservations")
                .headers(getBaseHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val array = if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<Reservation>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        Reservation(
                            id = obj.optLong("id", 0L),
                            fullName = obj.optString("fullName", obj.optString("full_name", "")),
                            phoneNumber = obj.optString("phoneNumber", obj.optString("phone_number", "")),
                            reservationTimeMillis = obj.optLong("start_time_millis", obj.optLong("reservationTimeMillis", obj.optLong("reservation_time_millis", 0L))),
                            durationMinutes = obj.optInt("durationMinutes", obj.optInt("duration_minutes", 0)),
                            status = obj.optString("status", "PENDING")
                        )
                    )
                }
                _recentReservations.value = list
                return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllReservationsFromCloud error: ${e.message}")
        }
        emptyList()
    }

    suspend fun fetchCustomerReservations(): List<Reservation> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/reservations")
                .headers(getCustomerHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val arr = JSONArray(response.body?.string() ?: "[]")
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            Reservation(
                                id = o.optLong("id", 0L),
                                fullName = o.optString("full_name", o.optString("customer_name", "")),
                                phoneNumber = o.optString("phone_number", ""),
                                reservationTimeMillis = o.optString("start_time").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: o.optLong("reservationTimeMillis", 0L),
                                durationMinutes = o.optInt("duration_minutes", 0),
                                status = o.optString("status", "PENDING")
                            )
                        )
                    }
                }.sortedByDescending { it.id }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCustomerReservations error: " + e.message)
            emptyList()
        }
    }

    suspend fun fetchCustomerStations(): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/stations")
                .headers(getCustomerHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string() ?: "{}").optJSONArray("stations")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCustomerStations error: ${e.message}")
            null
        }
    }

    suspend fun fetchReservationRules(durationMinutes: Int = 0, timeSlot: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(timeSlot, "UTF-8")
            val url = "$SERVER_URL/api/v1/customer/reservations/rules?durationMinutes=" + durationMinutes + "&timeSlot=" + encoded
            val request = Request.Builder().url(url).headers(getCustomerHeaders()).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string() ?: "{}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchReservationRules error: " + e.message)
            null
        }
    }

    suspend fun previewReservationPricing(req: PricingPreviewRequest): PricingPreviewResponse? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("type", req.reservationType)
                put("stationType", JSONObject.NULL)
                put("controllersCount", req.controllersCount.coerceAtLeast(1))
                put("durationMinutes", req.durationMinutes)
                put("isVip", req.reservationType.equals("VIP", true))
                put("timeSlot", req.reservationTimeMillis)
            }
            req.stationId?.let { json.put("stationId", it) }
            val request = Request.Builder().url("$SERVER_URL/api/v1/customer/reservations/pricing-preview").headers(getCustomerHeaders()).post(json.toString().toRequestBody(JSON_MEDIA)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val data = JSONObject(response.body?.string() ?: "{}").optJSONObject("data") ?: JSONObject()
                PricingPreviewResponse(data.optDouble("finalPrice", 0.0), data.optDouble("basePrice", 0.0), data.optDouble("discount", 0.0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "previewReservationPricing error: ${e.message}")
            null
        }
    }

    suspend fun cancelCustomerReservation(reservationId: Long, idempotencyKey: String): Boolean = withContext(Dispatchers.IO) {
        if (reservationId <= 0L || idempotencyKey.isBlank()) return@withContext false
        try {
            val body = JSONObject().put("idempotencyKey", idempotencyKey)
                .toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/reservations/$reservationId/cancel")
                .headers(getCustomerHeaders().newBuilder().add("Idempotency-Key", idempotencyKey).build())
                .post(body)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "cancelCustomerReservation error: ${e.message}")
            false
        }
    }

    suspend fun submitAtomicReservation(req: AtomicReservationRequest): AtomicReservationResponse? = withContext(Dispatchers.IO) {
        try {
            val key = req.idempotencyKey.trim().takeIf { it.isNotEmpty() } ?: return@withContext null
            val json = JSONObject().apply {
                put("type", req.reservationType)
                put("stationIds", JSONArray().apply { req.stationId?.let { put(it) } })
                put("startTime", java.time.Instant.ofEpochMilli(req.reservationTimeMillis).toString())
                put("durationMinutes", req.durationMinutes)
                put("controllersCount", req.controllersCount.coerceAtLeast(1))
                put("isVip", req.reservationType.equals("VIP", true))
                put("idempotencyKey", key)
                req.customerName?.let { put("customerName", it) }
                req.customerPhone?.let { put("customerPhone", it) }
            }
            val request = Request.Builder().url("$SERVER_URL/api/v1/customer/reservations/atomic").headers(getCustomerHeaders().newBuilder().add("Idempotency-Key", key).build()).post(json.toString().toRequestBody(JSON_MEDIA)).build()
            client.newCall(request).execute().use { response ->
                val obj = JSONObject(response.body?.string() ?: "{}")
                if (!response.isSuccessful) return@withContext null
                val reservationNode = obj.opt("reservation")
                val reservationId = when (reservationNode) {
                    is org.json.JSONArray -> if (reservationNode.length() > 0) reservationNode.optLong(0) else null
                    is org.json.JSONObject -> reservationNode.optLong("id", 0L).takeIf { it > 0L }
                    is Number -> reservationNode.toLong()
                    else -> obj.optLong("reservationId", 0L).takeIf { it > 0L }
                }
                AtomicReservationResponse(
                    obj.optBoolean("success", false),
                    obj.optString("message", "").ifBlank { if (response.isSuccessful) "درخواست رزرو ثبت شد." else "ثبت رزرو ناموفق بود." },
                    reservationId
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "submitAtomicReservation error: ${e.message}")
            null
        }
    }

    suspend fun fetchManagerReservations(): List<ReservationDbDto> = withContext(Dispatchers.IO) {
        try {
            val managerId = currentManagerId
            if (managerId.isBlank()) return@withContext emptyList()
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservations")
                .headers(getBaseHeaders())
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful || body.isBlank()) return@withContext emptyList()
            val array = if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("data") ?: JSONArray()
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(ReservationDbDto::class.java)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { adapter.fromJson(it.toString())?.let(::add) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchManagerReservations error: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateReservationStatus(reservationId: Long, status: String): Boolean = withContext(Dispatchers.IO) {
        if (reservationId <= 0L) return@withContext false
        val normalizedStatus = status.trim().uppercase()
        if (normalizedStatus !in setOf("CONFIRMED", "REJECTED", "ACTIVE", "COMPLETED", "NO_SHOW", "CANCELLED")) return@withContext false
        try {
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservations/$reservationId/status")
                .headers(getBaseHeaders())
                .put(JSONObject().apply { put("status", normalizedStatus) }.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "updateReservationStatus error: \${e.message}")
            false
        }
    }

    suspend fun fetchPendingReservationPayments(): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservation-payments/pending")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string() ?: "{}").optJSONArray("requests")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPendingReservationPayments error: " + e.message)
            null
        }
    }

    suspend fun rejectManagerReservationPayment(requestId: Long, reason: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("reason", reason).toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservation-payments/$requestId/reject")
                .headers(getBaseHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "rejectManagerReservationPayment error: " + e.message)
            false
        }
    }

    suspend fun approveManagerReservationPayment(requestId: Long, approvedAmount: Long? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                approvedAmount?.let { put("approvedAmount", it) }
            }.toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservation-payments/$requestId/approve")
                .headers(getBaseHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "approveManagerReservationPayment error: " + e.message)
            false
        }
    }

    suspend fun cancelManagerReservation(reservationId: Long, idempotencyKey: String): Boolean = withContext(Dispatchers.IO) {
        if (reservationId <= 0L || idempotencyKey.isBlank()) return@withContext false
        try {
            val body = JSONObject().put("idempotencyKey", idempotencyKey).toString().toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/reservations/$reservationId/cancel")
                .headers(getBaseHeaders().newBuilder().add("Idempotency-Key", idempotencyKey).build())
                .post(body)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "cancelManagerReservation error: " + e.message)
            false
        }
    }

    suspend fun deleteReservationFromCloud(reservationId: Long): Boolean =
        cancelManagerReservation(reservationId, "manager-cancel:" + reservationId)

    suspend fun transferGnDirect(
        senderPhoneOrId: String,
        receiverPhoneOrId: String,
        amount: Long,
        description: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(Exception("مبلغ انتقال باید بیشتر از صفر باشد"))

            val sender = _currentLoggedInCustomer.value
                ?: _allCloudCustomers.value.find { it.phoneNumber == senderPhoneOrId || it.id.toString() == senderPhoneOrId }
                ?: return@withContext Result.failure(Exception("حساب فرستنده یافت نشد"))

            val transferKey = "gn-transfer:${sender.id}:${receiverPhoneOrId}:${amount}:${java.util.UUID.randomUUID()}"
            val json = JSONObject().apply {
                put("senderId", sender.id)
                put("receiverPhone", receiverPhoneOrId)
                put("gnAmount", amount)
                put("description", description)
                put("idempotencyKey", transferKey)
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/club/transfer")
                .headers(getCustomerHeaders().newBuilder().add("Idempotency-Key", transferKey).build())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (resp.isSuccessful && body.isNotBlank()) {
                val obj = JSONObject(body)
                if (obj.optBoolean("success", true)) {
                    val updatedSender = sender.copy(
                        availableGn = (sender.availableGn - amount).coerceAtLeast(0L)
                    )
                    _currentLoggedInCustomer.value = updatedSender
                    return@withContext Result.success("انتقال مبلغ $amount GN با موفقیت انجام شد.")
                } else {
                    return@withContext Result.failure(Exception(obj.optString("message", "خطا در انتقال اعتبار GN")))
                }
            }

            Result.failure(Exception("خطا در پاسخ سرور"))
        } catch (e: Exception) {
            Log.e(TAG, "transferGnDirect error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun requestReservationPaymentReview(
        reservationId: Long,
        amountToman: Long,
        paymentMethod: String,
        trackingCode: String,
        note: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (reservationId <= 0L || amountToman <= 0L) return@withContext Result.failure(Exception("مبلغ یا رزرو نامعتبر است"))
            val trkCode = trackingCode.ifBlank { "RES_" + System.currentTimeMillis().toString().takeLast(8) }
            val json = JSONObject().apply {
                put("purpose", "RESERVATION_PAYMENT")
                put("reservationId", reservationId)
                put("amount", amountToman)
                put("paymentMethod", paymentMethod)
                put("trackingCode", trkCode)
                put("description", note)
                put("idempotencyKey", "reservation-payment:$reservationId:$trkCode")
            }
            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/manual-payment-requests")
                .headers(getCustomerHeaders().newBuilder().add("Idempotency-Key", "reservation-payment:$reservationId:$trkCode").build())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success("درخواست بررسی پرداخت رزرو با کد پیگیری $trkCode ثبت شد.")
                else Result.failure(Exception("ثبت درخواست پرداخت رزرو ناموفق بود"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestPaymentReview(
        customer: Customer,
        amountToman: Long,
        paymentMethod: String,
        trackingCode: String,
        note: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trkCode = trackingCode.ifBlank { "TRK_" + System.currentTimeMillis().toString().takeLast(6) }
            val json = JSONObject().apply {
                put("customerId", customer.id)
                put("amount", amountToman.toLong())
                put("transactionType", paymentMethod)
                put("trackingCode", trkCode)
                put("description", note)
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/manual-payment-requests")
                .headers(getCustomerHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()

            if (resp.isSuccessful) {
                Result.success("درخواست بررسی پرداخت شما با کد پیگیری $trkCode با موفقیت ثبت شد.")
            } else {
                Result.failure(Exception("خطا در ثبت پرداخت"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buyGnDirect(
        customer: Customer,
        gnAmount: Long,
        totalToman: Long,
        paymentMethod: String,
        trackingCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trkCode = trackingCode.ifBlank { "GN_BUY_" + System.currentTimeMillis().toString().takeLast(6) }
            val json = JSONObject().apply {
                put("amount", totalToman.toLong())
                put("gnAmount", gnAmount)
                put("purpose", "BUY_GN")
                put("trackingCode", trkCode)
                put("description", "خرید آنلاین اعتبار GN")
            }

            val req = Request.Builder()
                .url("$SERVER_URL/api/v1/customer/manual-payment-requests")
                .headers(getCustomerHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            
            if (resp.isSuccessful) {
                val updatedCust = customer.copy(pendingGn = customer.pendingGn + gnAmount)
                _currentLoggedInCustomer.value = updatedCust
                Result.success("درخواست خرید $gnAmount اعتبار GN با کد رهگیری $trkCode ثبت شد.")
            } else {
                Result.failure(Exception("خطا در ثبت خرید GN"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncStationToCloud(
        station: StationState,
        ordersJsonStr: String = "",
        hourlyRate: Long = 0L,
        gameCost: Long = 0L,
        buffetCost: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        if (station.id <= 0) {
            Log.w(TAG, "Ignoring invalid station id ${station.id} during canonical station sync")
            return@withContext false
        }
        try {
            val json = JSONObject().apply {
                put("id", station.id)
                put("name", "ایستگاه ${station.id}")
                put("status", station.status)
                put("consoleType", station.consoleType.ifBlank { "PS5" })
                put("controllerCount", station.controllerCount.coerceAtLeast(1))
                put("reservable", true)
            }
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/stations")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) Log.w(TAG, "Canonical station sync failed: HTTP ${response.code} $body")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncStationToCloud error: ${e.message}", e)
            false
        }
    }

    /**
     * Action-Triggered API: Start Session Event
     */
    suspend fun startStationSession(
        stationId: Any,
        startTimeMillis: Long,
        hourlyRate: Long,
        selectedCustomers: List<Pair<Any, String>>,
        consoleType: String = "",
        controllerCount: Int = 1,
        prepaymentAmount: Long = 0L,
        durationLimitMinutes: Int = 0,
        customerPrepayments: Map<Long, Long> = emptyMap(),
        idempotencyKey: String = ""
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        lastStationStartWasTransportFailure = false
        lastStationStartError = ""
        val stableIdempotencyKey = idempotencyKey.trim().ifBlank { "station-start:${stationId}:${startTimeMillis}:${java.util.UUID.randomUUID()}" }
        try {
            if (_currentManagerId.isBlank()) {
                lastStationStartError = "MANAGER_ID_MISSING"
                return@withContext null
            }
            val participants = JSONArray()
            selectedCustomers.forEach { (id, name) ->
                val rawId = id.toString().toLongOrNull()
                participants.put(JSONObject().apply {
                    if (rawId != null && rawId > 0L) put("customerId", rawId)
                    else put("participantKey", "guest:" + id.toString())
                    put("name", name)
                })
            }
            val json = JSONObject().apply {
                put("stationId", stationId.toString().toIntOrNull() ?: stationId.toString())
                put("consoleType", consoleType)
                put("controllerCount", controllerCount)
                put("hourlyRate", hourlyRate)
                put("prepaymentAmount", prepaymentAmount.coerceAtLeast(0L))
                put("durationLimitMinutes", durationLimitMinutes.coerceAtLeast(0))
                put("customerPrepayments", JSONObject().apply {
                    customerPrepayments.forEach { (id, amount) ->
                        if (id > 0L && amount > 0L) put(id.toString(), amount)
                    }
                })
                put("participants", participants)
            }
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/start")
                .headers(getBaseHeaders())
                .header("Idempotency-Key", stableIdempotencyKey)
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastStationStartError = "HTTP_${response.code}:" + body.take(500)
                    Log.e(TAG, "startStationSession HTTP " + response.code + ": " + body)
                    return@withContext null
                }
                val sessionId = JSONObject(body).optString("sessionId").takeIf { it.isNotBlank() } ?: run {
                    lastStationStartError = "INVALID_SERVER_RESPONSE"
                    return@withContext null
                }
                sessionId to JSONObject(body).optLong("serverStartedAt", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            // A lost response is ambiguous: the server may already have committed the session.
            // Resolve the same idempotency key before allowing any offline fallback.
            if (e is java.io.IOException) {
                val recovered = recoverStationStart(stableIdempotencyKey)
                if (recovered != null) {
                    lastStationStartWasTransportFailure = false
                    lastStationStartError = ""
                    return@withContext recovered
                }
            }
            // Only a confirmed transport failure with no committed server session may fall back offline.
            lastStationStartWasTransportFailure = e is java.io.IOException
            lastStationStartError = (e.message ?: e.javaClass.simpleName).take(500)
            Log.e(TAG, "startStationSession error: " + e.message, e)
            null
        }
    }

    suspend fun getActiveStationSession(stationId: Int): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/active?stationId=$stationId")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext null
                JSONObject(body).optJSONObject("session")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getActiveStationSession failed: ${e.message}")
            null
        }
    }

    suspend fun fetchLiveSessionsSnapshot(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/v1/manager/live-sessions")
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Log.w(TAG, "fetchLiveSessionsSnapshot HTTP ${response.code}: $body")
                    return@withContext null
                }
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchLiveSessionsSnapshot failed: ${e.message}")
            null
        }
    }

    private suspend fun recoverStationStart(idempotencyKey: String): Pair<String, Long>? = withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url("$SERVER_URL/api/station/start/status")
                    .headers(getBaseHeaders())
                    .header("Idempotency-Key", idempotencyKey)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val sessionId = json.optString("sessionId").takeIf { it.isNotBlank() }
                        if (sessionId != null) return@withContext sessionId to json.optLong("serverStartedAt", System.currentTimeMillis())
                    }
                }
            } catch (recoveryError: Exception) {
                Log.w(TAG, "recoverStationStart attempt ${attempt + 1} failed: ${recoveryError.message}")
            }
            if (attempt < 2) kotlinx.coroutines.delay(150L * (attempt + 1))
        }
        null
    }

    suspend fun syncOfflineSessionStart(
        sessionId: String,
        stationId: Int,
        startTimeMillis: Long,
        consoleType: String,
        controllerCount: Int,
        hourlyRate: Long,
        participants: JSONArray,
        prepaymentAmount: Long = 0L,
        durationLimitMinutes: Int = 0,
        customerPrepayments: Map<Long, Long> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_currentManagerId.isBlank()) return@withContext false
            val json = JSONObject().apply {
                put("sessionId", sessionId)
                put("stationId", stationId)
                put("startTimeMillis", startTimeMillis)
                put("consoleType", consoleType)
                put("controllerCount", controllerCount)
                put("hourlyRate", hourlyRate)
                put("prepaymentAmount", prepaymentAmount.coerceAtLeast(0L))
                put("durationLimitMinutes", durationLimitMinutes.coerceAtLeast(0))
                put("customerPrepayments", JSONObject().apply {
                    customerPrepayments.forEach { (id, amount) ->
                        if (id > 0L && amount > 0L) put(id.toString(), amount)
                    }
                })
                put("participants", participants)
            }
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/offline-start")
                .headers(getBaseHeaders())
                .header("Idempotency-Key", "offline-start:$sessionId")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "syncOfflineSessionStart error: " + e.message, e)
            false
        }
    }

    /**
     * Action-Triggered API: Add Buffet Order Event
     */
    suspend fun addBuffetOrderEvent(
        stationId: Any,
        productName: String,
        quantity: Int,
        price: Long,
        targetCustomerId: Long? = null,
        idempotencyKey: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_currentManagerId.isBlank()) return@withContext false
            val json = JSONObject().apply {
                put("managerId", _currentManagerId)
                put("stationId", stationId.toString())
                put("productName", productName)
                put("quantity", quantity)
                put("price", price)
                put("targetCustomerId", targetCustomerId ?: 0)
            }
            val requestIdempotencyKey = idempotencyKey?.trim().takeIf { !it.isNullOrBlank() }
                ?: "station-order:${stationId}:${productName}:${targetCustomerId ?: 0}:${java.util.UUID.randomUUID()}"
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/order")
                .headers(getBaseHeaders())
                .header("Idempotency-Key", requestIdempotencyKey)
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "addBuffetOrderEvent error: ${e.message}", e)
            false
        }
    }

    /**
     * Action-Triggered API: Settle Station Event
     */
    suspend fun sendSessionEvent(
        sessionId: String,
        eventId: String,
        eventType: String,
        occurredAtMillis: Long,
        payload: JSONObject = JSONObject()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_currentManagerId.isBlank()) return@withContext false
            val json = JSONObject().apply {
                put("sessionId", sessionId)
                put("eventId", eventId)
                put("eventType", eventType)
                put("occurredAt", occurredAtMillis)
                put("payload", payload)
            }
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/event")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "sendSessionEvent error: " + e.message, e)
            false
        }
    }

    suspend fun settleStationSession(
        sessionId: String,
        endedAtMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_currentManagerId.isBlank()) return@withContext false
            val json = JSONObject().apply { put("sessionId", sessionId); put("endedAt", endedAtMillis) }
            val request = Request.Builder()
                .url("$SERVER_URL/api/station/settle")
                .headers(getBaseHeaders())
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                lastSettlementHttpCode = response.code
                lastSettlementErrorBody = if (response.isSuccessful) "" else body.take(1000)
                if (!response.isSuccessful) Log.e(TAG, "settleStationSession HTTP " + response.code + ": " + body)
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "settleStationSession error: " + e.message, e)
            false
        }
    }
}
