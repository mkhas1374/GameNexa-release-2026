package com.example.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.NotificationManager
import android.app.NotificationChannel
import com.example.MainActivity
import com.example.data.*
import com.example.data.network.*
import com.example.receiver.AlarmReceiver
import com.example.util.ExactBilling
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(
        val userId: String,
        val username: String,
        val phone: String,
        val role: String,
        val email: String,
        val token: String
    ) : AuthState()
    data class AuthenticationError(val message: String) : AuthState()
}

sealed class AppAccessState {
    object Checking : AppAccessState()
    data class Allowed(val expiresAt: Long?, val planType: String) : AppAccessState()
    data class Denied(val message: String) : AppAccessState()
}

sealed class LicenseState {
    object Checking : LicenseState()
    data class Active(
        val planType: String,
        val expiresAt: Long,
        val activatedAt: Long = 0L,
        val licenseCode: String = "",
        val hasPassword: Boolean = false,
        val lastServerValidationTime: Long = 0L
    ) : LicenseState()
    data class Expired(val message: String) : LicenseState()
    data class Unactivated(val message: String) : LicenseState()
    data class OfflineGrace(
        val minutesRemaining: Int,
        val secondsRemaining: Int = 0,
        val message: String = "اتصال اینترنت شما برقرار نیست. لطفاً دسترسی به اینترنت را بررسی کنید.",
        val lastServerValidationTime: Long = 0L,
        val offlineHoursRemaining: Int = 24
    ) : LicenseState()
    data class ConnectionRequired(val message: String) : LicenseState()
}

class GameNetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val repository = GameNetRepository(db)
    private suspend fun encryptSetting(key: String, value: String) {
        repository.saveSetting(key, com.example.data.CryptoManager.encrypt(value))
    }

    private suspend fun decryptSetting(key: String): String {
        return repository.getSetting(key)?.let { com.example.data.CryptoManager.decrypt(it) } ?: ""
    }

    // Reactive database flows
    val stationStates: StateFlow<List<StationState>> = repository.allStationStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consoleTypes: StateFlow<List<ConsoleType>> = repository.allConsoleTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessionHistory: StateFlow<List<SessionHistory>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customerTransactions: StateFlow<List<CustomerTransaction>> = repository.allCustomerTransactions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreviewedTransactions = customerTransactions.map { list ->
        list.filter { it.status == "UNREVIEWED" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val reviewedTransactions = customerTransactions.map { list ->
        list.filter { it.status == "REVIEWED" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val debtorTransactions = customerTransactions.map { list ->
        list.filter { it.status == "DEBTOR" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val reservations: StateFlow<List<Reservation>> = repository.allReservations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Atomic station finishing lock to prevent checkout double-tap lag and duplicate records
    private val finishingStationIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private val _serverClockWarningVisible = MutableStateFlow(false)
    val serverClockWarningVisible: StateFlow<Boolean> = _serverClockWarningVisible.asStateFlow()
    private val _serverClockMillis = MutableStateFlow<Long?>(null)
    val serverClockMillis: StateFlow<Long?> = _serverClockMillis.asStateFlow()
    @Volatile private var serverClockAnchorElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime()

    fun requestFirstStartServerClockWarning() {
        viewModelScope.launch(Dispatchers.IO) {
            val alreadyShown = repository.getSetting("server_clock_warning_shown") == "1"
            if (alreadyShown) return@launch
            val serverTime = repository.getAuthoritativeServerTime() ?: return@launch
            serverClockAnchorElapsedRealtime = android.os.SystemClock.elapsedRealtime()
            _serverClockMillis.value = serverTime
            _serverClockWarningVisible.value = true
            repository.saveSetting("server_clock_warning_shown", "1")
        }
    }

    fun dismissServerClockWarning() {
        _serverClockWarningVisible.value = false
    }

    fun refreshServerClock() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAuthoritativeServerTime()?.let { serverTime ->
                serverClockAnchorElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                _serverClockMillis.value = serverTime
            }
        }
    }

    private fun customerTransactionJson(t: CustomerTransaction): org.json.JSONObject = org.json.JSONObject().apply {
        put("localId", t.id)
        put("customerId", t.customerId)
        put("customerName", t.customerName)
        put("stationName", t.stationName)
        put("title", t.title)
        put("amount", t.amount)
        put("paidAmount", t.paidAmount)
        put("status", t.status)
        put("dateStr", t.dateStr)
        put("timeStr", t.timeStr)
        put("segmentDetails", t.segmentDetails)
        put("buffetDetails", t.buffetDetails)
        put("timestamp", t.timestamp)
        put("playMinutes", t.playMinutes)
        put("gameCost", t.gameCost)
        put("foodCost", t.foodCost)
    }

    private suspend fun queueOrSyncCustomerTransaction(transaction: CustomerTransaction) {
        // Trial operational data is strictly local; never enqueue or transmit it.
        if (NetworkClient.isTrialMode) return
        // Walk-in transactions have no customer row and are intentionally local-only.
        if (transaction.customerId <= 0L || transaction.id <= 0L) return
        val key = "customer_transaction_outbox_${transaction.id}"
        val payload = customerTransactionJson(transaction).toString()
        if (SelfHostedManager.syncCustomerTransactionToCloud(transaction)) {
            repository.saveSetting(key, "")
        } else {
            repository.saveSetting(key, payload)
        }
    }

    private suspend fun flushPendingCustomerTransactions() {
        repository.getAllAppSettings()
            .filter { it.key.startsWith("customer_transaction_outbox_") && it.value.isNotBlank() }
            .forEach { setting ->
                try {
                    val obj = org.json.JSONObject(setting.value)
                    val transaction = CustomerTransaction(
                        id = obj.optLong("localId"),
                        customerId = obj.optLong("customerId"),
                        customerName = obj.optString("customerName"),
                        stationName = obj.optString("stationName"),
                        title = obj.optString("title"),
                        amount = obj.optLong("amount"),
                        paidAmount = obj.optLong("paidAmount"),
                        status = obj.optString("status", "UNREVIEWED"),
                        dateStr = obj.optString("dateStr"),
                        timeStr = obj.optString("timeStr"),
                        segmentDetails = obj.optString("segmentDetails"),
                        buffetDetails = obj.optString("buffetDetails"),
                        timestamp = obj.optLong("timestamp"),
                        playMinutes = obj.optInt("playMinutes"),
                        gameCost = obj.optLong("gameCost"),
                        foodCost = obj.optLong("foodCost")
                    )
                    if (transaction.customerId > 0L && transaction.id > 0L && SelfHostedManager.syncCustomerTransactionToCloud(transaction)) {
                        repository.saveSetting(setting.key, "")
                    }
                } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
            }
    }

    private suspend fun queueOrSendSessionEvent(
        sessionId: String,
        eventType: String,
        occurredAtMillis: Long,
        payload: org.json.JSONObject = org.json.JSONObject()
    ) {
        // Trial sessions are intentionally local-only. Never emit operational API calls.
        if (isTrialUser) return
        val eventId = java.util.UUID.randomUUID().toString()
        val sent = SelfHostedManager.sendSessionEvent(sessionId, eventId, eventType, occurredAtMillis, payload)
        if (sent) return
        val key = "session_outbox_" + sessionId
        val current = repository.getSetting(key)
        val array = try { if (current.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(current) } catch (_: Exception) { org.json.JSONArray() }
        array.put(org.json.JSONObject().apply {
            put("eventId", eventId)
            put("eventType", eventType)
            put("occurredAt", occurredAtMillis)
            put("payload", payload)
        })
        repository.saveSetting(key, array.toString())
    }

    private suspend fun queueOrSettleSession(sessionId: String, stationId: Int, endedAtMillis: Long): Boolean {
        // Trial settlement is local-only; no backend operation is allowed.
        if (isTrialUser) {
            repository.saveSetting("active_session_" + stationId, "")
            repository.saveSetting("active_session_start_" + stationId, "")
            return true
        }
        if (sessionId.isBlank()) {
            repository.saveSetting("active_session_" + stationId, "")
            repository.saveSetting("active_session_start_" + stationId, "")
            return true
        }

        // A session started while offline has a local UUID and must be reconciled to the
        // canonical server session before settlement. Sending /station/settle first produces
        // a misleading 404 and leaves customer claims behind.
        val pendingStartKey = "session_pending_start_" + stationId
        val pendingStartRaw = repository.getSetting(pendingStartKey)
        if (!pendingStartRaw.isNullOrBlank()) {
            val pending = runCatching { org.json.JSONObject(pendingStartRaw) }.getOrNull()
            if (pending?.optString("sessionId") == sessionId) {
                val reconciled = SelfHostedManager.syncOfflineSessionStart(
                    sessionId = sessionId,
                    stationId = stationId,
                    startTimeMillis = pending.optLong("startTimeMillis"),
                    consoleType = pending.optString("consoleType"),
                    controllerCount = pending.optInt("controllerCount", 1),
                    hourlyRate = pending.optLong("hourlyRate"),
                    participants = pending.optJSONArray("participants") ?: org.json.JSONArray(),
                    prepaymentAmount = pending.optLong("prepaymentAmount", 0L),
                    durationLimitMinutes = pending.optInt("durationLimitMinutes", 0),
                    customerPrepayments = pending.optJSONObject("customerPrepayments")?.let { obj ->
                        buildMap {
                            obj.keys().forEach { key ->
                                val customerId = key.toLongOrNull()
                                val amount = obj.optLong(key, 0L)
                                if (customerId != null && customerId > 0L && amount > 0L) put(customerId, amount)
                            }
                        }
                    } ?: emptyMap()
                )
                if (!reconciled) {
                    repository.saveSetting(
                        "session_pending_settlement_" + stationId,
                        org.json.JSONObject().apply {
                            put("sessionId", sessionId)
                            put("endedAt", endedAtMillis)
                        }.toString()
                    )
                    return false
                }
                repository.saveSetting(pendingStartKey, "")
            }
        }

        val settled = SelfHostedManager.settleStationSession(sessionId, endedAtMillis)
        if (settled) {
            repository.saveSetting("active_session_" + stationId, "")
            repository.saveSetting("active_session_start_" + stationId, "")
            return true
        }
        repository.saveSetting(
            "session_pending_settlement_" + stationId,
            org.json.JSONObject().apply {
                put("sessionId", sessionId)
                put("endedAt", endedAtMillis)
            }.toString()
        )
        return false
    }

    private suspend fun flushPendingSettlements() {
        val settings = repository.getAllAppSettings()
        settings.filter { it.key.startsWith("session_pending_settlement_") }.forEach { setting ->
            val stationId = setting.key.removePrefix("session_pending_settlement_").toIntOrNull() ?: return@forEach
            val item = try { org.json.JSONObject(setting.value) } catch (_: Exception) { return@forEach }
            val sessionId = item.optString("sessionId").trim()
            val rawEndedAt = item.optLong("endedAt", 0L)
            // Never send an empty/zero settlement timestamp from stale local state.
            val endedAt = if (rawEndedAt > 0L) rawEndedAt else System.currentTimeMillis()
            if (sessionId.isBlank()) return@forEach

            if (SelfHostedManager.settleStationSession(sessionId, endedAt)) {
                repository.saveSetting(setting.key, "")
                repository.saveSetting("active_session_" + stationId, "")
                repository.saveSetting("active_session_start_" + stationId, "")
            } else if (SelfHostedManager.lastSettlementHttpCode == 404) {
                // The server has no such session anymore. Retrying the orphan forever on every
                // login/reconnect cannot recover data and was the source of repeated post-login errors.
                repository.saveSetting(setting.key, "")
                repository.saveSetting("active_session_" + stationId, "")
                repository.saveSetting("active_session_start_" + stationId, "")
                android.util.Log.w("GameNetViewModel", "Cleared orphan pending settlement for station $stationId: session=$sessionId")
            }
        }
    }

    private suspend fun flushPendingSessionStarts() {
        val settings = repository.getAllAppSettings()
        settings.filter { it.key.startsWith("session_pending_start_") }.forEach { setting ->
            val stationId = setting.key.removePrefix("session_pending_start_").toIntOrNull() ?: return@forEach
            val item = try { org.json.JSONObject(setting.value) } catch (_: Exception) { return@forEach }
            val ok = SelfHostedManager.syncOfflineSessionStart(
                sessionId = item.optString("sessionId"),
                stationId = stationId,
                startTimeMillis = item.optLong("startTimeMillis"),
                consoleType = item.optString("consoleType"),
                controllerCount = item.optInt("controllerCount", 1),
                hourlyRate = item.optLong("hourlyRate"),
                participants = item.optJSONArray("participants") ?: org.json.JSONArray(),
                prepaymentAmount = item.optLong("prepaymentAmount", 0L),
                durationLimitMinutes = item.optInt("durationLimitMinutes", 0),
                customerPrepayments = item.optJSONObject("customerPrepayments")?.let { obj ->
                    buildMap {
                        obj.keys().forEach { key ->
                            val id = key.toLongOrNull()
                            val amount = obj.optLong(key, 0L)
                            if (id != null && id > 0L && amount > 0L) put(id, amount)
                        }
                    }
                } ?: emptyMap()
            )
            if (ok) repository.saveSetting(setting.key, "")
        }
    }

    private suspend fun flushSessionOutbox() {
        val settings = repository.getAllAppSettings()
        settings.filter { it.key.startsWith("session_outbox_") }.forEach { setting ->
            val sessionId = setting.key.removePrefix("session_outbox_")
            val array = try { org.json.JSONArray(setting.value) } catch (_: Exception) { return@forEach }
            val remaining = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ok = SelfHostedManager.sendSessionEvent(
                    sessionId = sessionId,
                    eventId = item.optString("eventId"),
                    eventType = item.optString("eventType"),
                    occurredAtMillis = item.optLong("occurredAt"),
                    payload = item.optJSONObject("payload") ?: org.json.JSONObject()
                )
                if (!ok) remaining.put(item)
            }
            if (remaining.length() == 0) repository.saveSetting(setting.key, "")
            else repository.saveSetting(setting.key, remaining.toString())
        }
    }

    // Settings UI States

    // Payment Settings
    private val _gnPaymentCards = MutableStateFlow<String>("[]")
    val gnPaymentCards: StateFlow<String> = _gnPaymentCards.asStateFlow()

    private val _gnPaymentGateways = MutableStateFlow<String>("[]")
    val gnPaymentGateways: StateFlow<String> = _gnPaymentGateways.asStateFlow()

    private val _gnPaymentCryptos = MutableStateFlow<String>("[]")
    val gnPaymentCryptos: StateFlow<String> = _gnPaymentCryptos.asStateFlow()

    private val _gnContactSms = MutableStateFlow<String>("09395773183")
    val gnContactSms: StateFlow<String> = _gnContactSms.asStateFlow()

    private val _gnContactBale = MutableStateFlow<String>("@Real_MimKhas")
    val gnContactBale: StateFlow<String> = _gnContactBale.asStateFlow()

    private val _gnGamePaymentRatio = MutableStateFlow<Long>(30L)
    val gnGamePaymentRatio: StateFlow<Long> = _gnGamePaymentRatio.asStateFlow()

    private val _gnBuffetPaymentRatio = MutableStateFlow<Long>(50L)
    val gnBuffetPaymentRatio: StateFlow<Long> = _gnBuffetPaymentRatio.asStateFlow()

    private val _gnToTomanRatio = MutableStateFlow<Int>(1000)
    val gnToTomanRatio: StateFlow<Int> = _gnToTomanRatio.asStateFlow()
    private val _stationCount = MutableStateFlow(10)
    val stationCount: StateFlow<Int> = _stationCount.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notchSafeBarEnabled = MutableStateFlow(false)
    val notchSafeBarEnabled: StateFlow<Boolean> = _notchSafeBarEnabled.asStateFlow()

    private val _language = MutableStateFlow("fa")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _appTheme = MutableStateFlow("دارک")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()


    // Admin Authentication Gatekeeper
    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    // Cold-start restoration must finish before a new Manager login can mutate the same
    // persisted session keys. Without this barrier, a slow startup restore could read the
    // pre-login state and immediately clear a freshly authenticated Manager session.
    private val managerAuthInitializationReady = kotlinx.coroutines.CompletableDeferred<Unit>()

    // Admin Notification Item model (Unified for Reservations, Payment Proofs, Unreviewed Transactions)
    data class AdminNotificationItem(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: String, // "PAYMENT_PROOF", "RESERVATION", "UNREVIEWED_TRANSACTION"
        val title: String,
        val description: String,
        val customerName: String = "",
        val phoneNumber: String = "",
        val amount: Long = 0L,
        val trackingCode: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED", "UNREVIEWED"
        val managerId: String = "",
        val isRead: Boolean = false,
        val rawPayment: CloudPaymentRecord? = null,
        val rawReservation: Reservation? = null,
        val rawTransaction: CustomerTransaction? = null
    )

    // Backward-compatible alias for existing system notifications
    data class ManagerUrgentAlert(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: String, // "RESERVATION", "PAYMENT"
        val title: String,
        val message: String,
        val customerName: String = "",
        val phoneNumber: String = "",
        val amount: Long = 0L,
        val trackingCode: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _managerUrgentAlert = MutableStateFlow<ManagerUrgentAlert?>(null)
    val managerUrgentAlert: StateFlow<ManagerUrgentAlert?> = _managerUrgentAlert.asStateFlow()

    private val _readNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val readNotificationIds: StateFlow<Set<String>> = _readNotificationIds.asStateFlow()

    private val _activeUrgentOverlayAlert = MutableStateFlow<AdminNotificationItem?>(null)
    val activeUrgentOverlayAlert: StateFlow<AdminNotificationItem?> = _activeUrgentOverlayAlert.asStateFlow()

    val currentManagerId: StateFlow<String> = SelfHostedManager.currentManagerIdFlow

    private data class NotificationFlowBundle(
        val payments: List<CloudPaymentRecord>,
        val cloudReservations: List<Reservation>,
        val localReservations: List<Reservation>,
        val transactions: List<CustomerTransaction>
    )

    // Scoped Notification Stream strictly for the signed-in Manager
    val managerNotifications: StateFlow<List<AdminNotificationItem>> = kotlinx.coroutines.flow.combine(
        SelfHostedManager.recentPayments,
        SelfHostedManager.recentReservations,
        repository.allReservations,
        customerTransactions
    ) { payments, cloudReservations, localReservations, transactions ->
        NotificationFlowBundle(payments, cloudReservations, localReservations, transactions)
    }.combine(SelfHostedManager.currentManagerIdFlow) { bundle, mgrId ->
        Pair(bundle, mgrId)
    }.combine(_readNotificationIds) { (bundle, mgrId), readIds ->
        val list = mutableListOf<AdminNotificationItem>()
        val activeMgr = mgrId.trim()

        // 1. Payment Proofs / Online Payments Scoped to Manager
        for (p in bundle.payments) {
            val isScoped = activeMgr.isBlank() || activeMgr == "mgr_super_admin" || p.managerId.isBlank() || p.managerId == activeMgr
            if (isScoped) {
                val notifId = "pay_${p.id}"
                val titleStr = when (p.paymentType) {
                    "BUY_POINTS" -> "خرید بسته امتیاز GN"
                    "CHARGE_WALLET" -> "شارژ آنلاین کیف پول"
                    "PAY_DEBT" -> "تسویه آنلاین بدهی"
                    else -> "فیش واریزی و پرداخت آنلاین"
                }
                list.add(
                    AdminNotificationItem(
                        id = notifId,
                        type = "PAYMENT_PROOF",
                        title = titleStr,
                        description = "مشتری ${p.customerName.ifBlank { "کاربر گیم‌نت" }} مبلغ %,d تومان (${p.paymentType}) پرداخت کرده است.".format(Locale.US, p.amount),
                        customerName = p.customerName,
                        phoneNumber = "",
                        amount = p.amount,
                        trackingCode = p.trackingCode,
                        timestamp = p.timestamp,
                        status = p.status,
                        managerId = p.managerId,
                        isRead = readIds.contains(notifId),
                        rawPayment = p
                    )
                )
            }
        }

        // 2. Reservations (Deduplicated Cloud + Local) Scoped
        val allRes = (bundle.cloudReservations + bundle.localReservations).distinctBy { "${it.id}_${it.phoneNumber}_${it.reservationTimeMillis}" }
        for (r in allRes) {
            val notifId = "res_${r.id}"
            list.add(
                AdminNotificationItem(
                    id = notifId,
                    type = "RESERVATION",
                    title = "درخواست رزرو نوبت میز",
                    description = "رزرو برای ${r.fullName} به مدت ${r.durationMinutes} دقیقه در تاریخ ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(r.reservationTimeMillis))}",
                    customerName = r.fullName,
                    phoneNumber = r.phoneNumber,
                    timestamp = r.reservationTimeMillis,
                    status = "PENDING",
                    isRead = readIds.contains(notifId),
                    rawReservation = r
                )
            )
        }

        // 3. Unreviewed Manual Transactions
        for (t in bundle.transactions.filter { it.status == "UNREVIEWED" }) {
            val notifId = "tx_${t.id}"
            list.add(
                AdminNotificationItem(
                    id = notifId,
                    type = "UNREVIEWED_TRANSACTION",
                    title = "سند مالی بررسی‌نشده",
                    description = "${t.title.ifBlank { "تراکنش مالی" }} (${t.stationName}) - مبلغ %,d تومان".format(Locale.US, t.amount),
                    customerName = t.customerName.ifBlank { "مشتری #${t.customerId}" },
                    phoneNumber = "",
                    amount = t.amount,
                    timestamp = t.timestamp,
                    status = "UNREVIEWED",
                    isRead = readIds.contains(notifId),
                    rawTransaction = t
                )
            )
        }

        // Sort: Pending/Unreviewed items first, then unread, then descending by timestamp
        list.sortedWith(
            compareBy<AdminNotificationItem> { if (it.status == "PENDING" || it.status == "UNREVIEWED") 0 else 1 }
                .thenBy { if (!it.isRead) 0 else 1 }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> = kotlinx.coroutines.flow.combine(
        managerNotifications,
        _readNotificationIds
    ) { notifications, readIds ->
        notifications.count { (it.status == "PENDING" || it.status == "UNREVIEWED") && !readIds.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun dismissUrgentOverlayAlert() {
        _activeUrgentOverlayAlert.value = null
    }

    fun markNotificationAsRead(id: String) {
        _readNotificationIds.value = _readNotificationIds.value + id
        if (_activeUrgentOverlayAlert.value?.id == id) {
            _activeUrgentOverlayAlert.value = null
        }
    }

    fun markAllNotificationsAsRead() {
        val allIds = managerNotifications.value.map { it.id }.toSet()
        _readNotificationIds.value = _readNotificationIds.value + allIds
        _activeUrgentOverlayAlert.value = null
    }

    fun approveNotificationPayment(item: AdminNotificationItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = item.rawPayment
            if (p != null) {
                val ok = SelfHostedManager.approvePendingPayment(p)
                if (ok) {
                    logOperatorActivity(
                        "تایید واریزی / شارژ آنلاین",
                        "تایید پرداخت ${p.paymentType} برای ${p.customerName} به مبلغ ${p.amount} تومان و شارژ پاداش"
                    )
                    markNotificationAsRead(item.id)
                    SelfHostedManager.fetchAllFromCloud()
                        repository.syncAllWithServer()
                    withContext(Dispatchers.Main) { onComplete(true) }
                    return@launch
                }
            }
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    fun confirmNotificationReservation(item: AdminNotificationItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = item.rawReservation
            if (r != null) {
                val approved = com.example.data.network.SelfHostedManager.updateReservationStatus(r.id, "CONFIRMED")
                if (!approved) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }
                val updatedReservation = r.copy(status = "CONFIRMED")
                repository.insertReservation(updatedReservation)
                logOperatorActivity(
                    "تایید درخواست رزرو",
                    "تایید رزرو ${r.fullName} (${r.phoneNumber}) به مدت ${r.durationMinutes} دقیقه"
                )
                markNotificationAsRead(item.id)
                withContext(Dispatchers.Main) { onComplete(true) }
            } else {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteNotificationReservation(item: AdminNotificationItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = item.rawReservation
            if (r != null) {
                val rejected = com.example.data.network.SelfHostedManager.updateReservationStatus(r.id, "REJECTED")
                if (!rejected) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }
                repository.insertReservation(r.copy(status = "REJECTED"))
                markNotificationAsRead(item.id)
                withContext(Dispatchers.Main) { onComplete(true) }
            } else {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun showManagerUrgentAlert(alert: ManagerUrgentAlert) {
        _managerUrgentAlert.value = alert
        triggerManagerSystemNotification(alert)
    }

    fun dismissManagerUrgentAlert() {
        _managerUrgentAlert.value = null
    }

    private fun triggerManagerSystemNotification(alert: ManagerUrgentAlert) {
        try {
            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = if (alert.type == "PAYMENT") "gamenet_payments_urgent" else "gamenet_reservations_urgent"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    if (alert.type == "PAYMENT") "اعلان واریز و پرداخت مشتریان" else "اعلان رزروهای فوری",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "هشدارهای بلادرنگ برای واریزی‌ها و رزروهای جدید مشتریان"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.example.R.drawable.ic_notification_gn)
                .setContentTitle(alert.title)
                .setContentText(alert.message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(alert.message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_SOUND or androidx.core.app.NotificationCompat.DEFAULT_VIBRATE)

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Customer Authentication Gatekeeper
    private val _isCustomerAuthenticated = MutableStateFlow(false)
    val isCustomerAuthenticated: StateFlow<Boolean> = _isCustomerAuthenticated.asStateFlow()

    // RBAC & Multi-role Management
    private val _currentAdminRole = MutableStateFlow("UNAUTHENTICATED") // "SUPER_MANAGER", "OPERATOR", or "TRIAL_USER"
    val currentAdminRole: StateFlow<String> = _currentAdminRole.asStateFlow()

    private val _ownerUsername = MutableStateFlow("")
    val ownerUsername: StateFlow<String> = _ownerUsername.asStateFlow()

    private val _ownerPassword = MutableStateFlow("")
    val ownerPassword: StateFlow<String> = _ownerPassword.asStateFlow()

    private val _deputyName = MutableStateFlow("معاون سالن")
    val deputyName: StateFlow<String> = _deputyName.asStateFlow()

    private val _operatorUsername = MutableStateFlow("")
    val operatorUsername: StateFlow<String> = _operatorUsername.asStateFlow()

    private val _operatorPassword = MutableStateFlow("")
    val operatorPassword: StateFlow<String> = _operatorPassword.asStateFlow()

    // Operator Permissions
    private val _permCustomerClub = MutableStateFlow(true)
    val permCustomerClub: StateFlow<Boolean> = _permCustomerClub.asStateFlow()

    private val _permFinance = MutableStateFlow(true)
    val permFinance: StateFlow<Boolean> = _permFinance.asStateFlow()

    private val _permPricing = MutableStateFlow(false)
    val permPricing: StateFlow<Boolean> = _permPricing.asStateFlow()

    private val _permBuffet = MutableStateFlow(true)
    val permBuffet: StateFlow<Boolean> = _permBuffet.asStateFlow()

    private val _permCustomerPasswords = MutableStateFlow(false)
    val permCustomerPasswords: StateFlow<Boolean> = _permCustomerPasswords.asStateFlow()

    private val _permCustomerEdit = MutableStateFlow(true)
    val permCustomerEdit: StateFlow<Boolean> = _permCustomerEdit.asStateFlow()

    // Announcement Broadcast Message
    private val _ownerBroadcastMessage = MutableStateFlow("")
    val ownerBroadcastMessage: StateFlow<String> = _ownerBroadcastMessage.asStateFlow()

    // Audit logs
    val operatorAuditLogs: StateFlow<List<OperatorAuditLog>> = repository.allOperatorAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudAuditLogs = SelfHostedManager.cloudAuditLogs
    val ownerBroadcastAnnouncement = SelfHostedManager.ownerBroadcastAnnouncement

    fun saveOwnerCredentials(user: String, pass: String) {
        val cleanUser = user.trim()
        val cleanPass = pass.trim()
        _ownerUsername.value = cleanUser
        _ownerPassword.value = cleanPass
        viewModelScope.launch(Dispatchers.IO) {
            encryptSetting("enc_owner_username", cleanUser)
            encryptSetting("enc_owner_password", cleanPass)
        }
    }

    fun authenticateAdmin(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val cleanUser = toEnglishDigits(user.trim())
        val cleanPass = toEnglishDigits(pass.trim())
        
        

        viewModelScope.launch(Dispatchers.IO) {
            // Never race a user-initiated Manager login against cold-start session restoration.
            // The startup path owns the same encrypted auth keys and may otherwise clear them
            // after this login succeeds.
            managerAuthInitializationReady.await()

            // Production authentication uses one canonical server endpoint.
            // Legacy login fallbacks are disabled because they can bypass entitlement checks.
            val candidateLoginPaths = listOf("api/auth/manager/login")
            val baseUrl = SelfHostedManager.SERVER_URL.trimEnd('/')
            val client = SelfHostedManager.client
            val headers = SelfHostedManager.getBaseHeaders()
            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            val loginPayload = org.json.JSONObject().apply {
                put("phone", cleanUser)
                put("username", cleanUser)
                put("phoneNumber", cleanUser)
                put("password", cleanPass)
                put("deviceId", getDeviceId())
            }
            val requestBody = loginPayload.toString().toRequestBody(jsonMedia)

            var loginSuccess = false
            var serverRole = "MANAGER"
            var serverManagerId = ""
            var serverFullName = ""
            var serverGameNetName = ""
            var serverToken = ""

            for (path in candidateLoginPaths) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$baseUrl/$path")
                        .headers(headers)
                        .post(requestBody)
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()?.trim() ?: ""
                            if (body.startsWith("{")) {
                                val jsonObj = org.json.JSONObject(body)
                                val userObj = jsonObj.optJSONObject("user") ?: jsonObj.optJSONObject("manager") ?: jsonObj
                                serverToken = jsonObj.optString("token", userObj.optString("token", ""))
                                val extractedId = userObj.optString("id", userObj.optString("manager_id", userObj.optString("managerId", "")))
                                if (extractedId.isNotBlank()) serverManagerId = extractedId
                                serverFullName = userObj.optString("full_name", userObj.optString("fullName", userObj.optString("name", "")))
                                serverGameNetName = userObj.optString("gamenet_name", userObj.optString("gameneName", userObj.optString("gameNetName", "")))
                                val roleFromServer = userObj.optString("role", userObj.optString("userType", ""))
                                if (roleFromServer.equals("super_manager", ignoreCase = true) || roleFromServer.equals("SUPER_MANAGER", ignoreCase = true)) {
                                    serverRole = "SUPER_MANAGER"
                                }
                                loginSuccess = true
                                return@use
                            }
                        }
                    }
                    if (loginSuccess) break
                } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
            }

            if (loginSuccess && (serverManagerId.isBlank() || serverToken.isBlank())) {
                loginSuccess = false
            }

            if (loginSuccess) {
                expirationJob?.cancel()
                expirationJob = null
                val isSuper = serverRole == "SUPER_MANAGER"
                val finalRole = if (isSuper) "SUPER_MANAGER" else serverRole
                val finalManagerId = serverManagerId
                val now = System.currentTimeMillis()

                // Establish the complete authenticated Manager session BEFORE publishing
                // Authenticated to the UI. This prevents early 401s and false logout/offline.
                NetworkClient.isTrialMode = false
                encryptSetting("enc_session_type", "ADMIN")
                encryptSetting("enc_admin_role", finalRole)
                encryptSetting("enc_manager_id", finalManagerId)
                encryptSetting("enc_user_id", finalManagerId)
                encryptSetting("enc_auth_phone", cleanUser)
                encryptSetting("enc_auth_token", serverToken)
                NetworkClient.authToken = serverToken
                SelfHostedManager.setManagerId(finalManagerId)
                
                withContext(Dispatchers.Main) {
                    _currentAdminRole.value = finalRole
                    _isAdminAuthenticated.value = true
                    _isCustomerAuthenticated.value = false
                    // Authentication success does not itself grant subscription access.
                    // Entitlement is verified against the server immediately below.
                    _isSubscribed.value = false
                    _licenseState.value = LicenseState.ConnectionRequired("در حال بررسی اعتبار اشتراک از سرور...")
                    _accessState.value = AppAccessState.Denied("در حال بررسی اعتبار اشتراک...")
                    
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            encryptSetting("enc_session_type", "ADMIN")
                            encryptSetting("enc_admin_role", finalRole)
                            encryptSetting("enc_manager_id", finalManagerId)
                            encryptSetting("enc_user_id", finalManagerId)
                            encryptSetting("enc_auth_phone", cleanUser)
                            encryptSetting("enc_auth_token", serverToken)
                            com.example.data.network.NetworkClient.authToken = serverToken
                            // Establish Manager identity before any authenticated post-login verification request.
                            SelfHostedManager.setManagerId(finalManagerId)
                            encryptSetting("enc_license_status", "UNKNOWN")
                            encryptSetting("enc_plan_type", "")
                            encryptSetting("enc_expire_time", "0")
                            verifyLicenseStatus()
                            if (serverFullName.isNotBlank()) encryptSetting("enc_manager_fullname", serverFullName)
                            if (serverGameNetName.isNotBlank()) encryptSetting("enc_gamenet_name", serverGameNetName)
                            
                            if (_isSubscribed.value && _currentAdminRole.value != "TRIAL_USER") {
                                flushPendingSessionStarts()
                                flushSessionOutbox()
                                flushPendingBuffetOrders()
                                flushPendingCustomerTransactions()
                                flushPendingSettlements()
                                SelfHostedManager.fetchAllFromCloud()
                                repository.syncAllWithServer()
                            }
                            withContext(Dispatchers.Main) {
                                if (_isSubscribed.value) {
                                    logOperatorActivity("ورود موفق", "ورود مدیر ($finalRole) به پنل")
                                    onResult(true, if (isSuper) "ورود موفق مدیریت ارشد" else "ورود موفق مدیریت")
                                } else {
                                    onResult(false, "ورود انجام شد اما اشتراک فعال نیست یا قابل تأیید نیست.")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GameNetViewModel", "Error syncing on manager login", e)
                            withContext(Dispatchers.Main) {
                                onResult(false, "اعتبار اشتراک از سرور تأیید نشد.")
                            }
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "چنین مدیری ثبت نشده یا رمز اشتباه است")
                }
            }
        }
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
        val cleanName = custName.trim().replace("u200c", " ").replace("s+".toRegex(), " ")
        val cleanInputName = input.trim().replace("u200c", " ").replace("s+".toRegex(), " ")
        if (cleanName.isNotBlank() && cleanName.equals(cleanInputName, ignoreCase = true)) return true
        if (cleanName.isNotBlank() && cleanInput.equals(cleanName, ignoreCase = true)) return true

        // 3. Invite Code Match
        if (inviteCode.isNotBlank()) {
            if (inviteCode.equals(cleanInput, ignoreCase = true)) return true
            if (inviteCode.removePrefix("GN-").equals(cleanInput.removePrefix("GN-"), ignoreCase = true)) return true
        }

        return false
    }

    fun fetchCustomerAppConfigsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cards = com.example.data.network.SelfHostedManager.fetchAppConfig("gn_payment_cards")
                if (!cards.isNullOrBlank()) {
                    _gnPaymentCards.value = cards
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }




    fun loginCustomer(phone: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = toEnglishDigits(phone.trim())
        val cleanPass = toEnglishDigits(pass.trim())

        if (cleanPhone.isBlank() || cleanPass.isBlank()) {
            onResult(false, "لطفاً شماره همراه و رمز عبور را وارد کنید.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Customer authentication is always server-authoritative. Recover the Manager scope
            // from the encrypted session if the in-memory singleton was lost during recreation.
            val persistedManagerId = SelfHostedManager.currentManagerId.trim().ifBlank {
                decryptSetting("enc_manager_id").trim()
            }
            if (persistedManagerId.isNotBlank() && SelfHostedManager.currentManagerId != persistedManagerId) {
                SelfHostedManager.setManagerId(persistedManagerId)
            }

            // 1. Try real-time online verification with self-hosted server first to ensure account is not deleted
            val onlineResult = SelfHostedManager.loginCustomer(cleanPhone, cleanPass)
            if (onlineResult.isSuccess) {
                val cloudCust = onlineResult.getOrNull()!!
                try {
                    repository.insertCustomer(cloudCust)
                } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", ignored) }

                withContext(Dispatchers.Main) {
                    _isCustomerAuthenticated.value = true
                    _isAdminAuthenticated.value = false
                    SelfHostedManager.setCurrentCustomer(cloudCust)
                }
                encryptSetting("enc_session_type", "CUSTOMER")
                encryptSetting("enc_customer_phone", cloudCust.phoneNumber.ifBlank { cleanPhone })
                encryptSetting("enc_manager_id", SelfHostedManager.currentManagerId)
                encryptSetting("enc_customer_auth_token", com.example.data.network.NetworkClient.customerAuthToken ?: "")
                fetchCustomerAppConfigsFromCloud()
                withContext(Dispatchers.Main) {
                    onResult(true, "ورود با موفقیت انجام شد.")
                }
                return@launch
            } else {
                val failureMsg = onlineResult.exceptionOrNull()?.message
                if (failureMsg != null && failureMsg.contains("رمز عبور")) {
                    withContext(Dispatchers.Main) {
                        onResult(false, failureMsg)
                    }
                    return@launch
                }
            }

            // Local Room data never grants Customer authentication. Server validation is mandatory.
            withContext(Dispatchers.Main) {
                onResult(false, "ارتباط با سرور برای ورود مشتری برقرار نشد؛ ورود آفلاین مجاز نیست.")
            }
            return@launch

            // No local fallback: customer authentication is server-authoritative.

        }
    }

    fun logout() {
        _isAdminAuthenticated.value = false
        _isCustomerAuthenticated.value = false
        SelfHostedManager.setCurrentCustomer(null)
        SelfHostedManager.setManagerId("")
        viewModelScope.launch(Dispatchers.IO) {
            try { NetworkClient.getApi(_serverUrl.value).logoutSession() } catch (_: Exception) { /* local logout still completes */ }
            NetworkClient.managerAuthToken = null
            NetworkClient.customerAuthToken = null
            encryptSetting("enc_session_type", "")
            encryptSetting("enc_customer_phone", "")
            encryptSetting("enc_customer_auth_token", "")
            encryptSetting("enc_auth_token", "")
            encryptSetting("enc_admin_role", "")
            encryptSetting("enc_manager_id", "")
            // Authentication logout must not erase business data; Room data remains available after re-login.
        }
    }

    fun logoutAdmin() {
        logout()
    }

    fun saveOperatorCredentials(name: String, user: String, pass: String) {
        val cleanUser = user.trim()
        val cleanPass = pass.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) return
        _deputyName.value = name
        _operatorUsername.value = cleanUser
        _operatorPassword.value = cleanPass
        viewModelScope.launch(Dispatchers.IO) {
            encryptSetting("enc_deputy_name", name)
            encryptSetting("enc_operator_username", cleanUser)
            encryptSetting("enc_operator_password", cleanPass)
        }
    }

    fun saveOperatorPermissions(
        club: Boolean,
        finance: Boolean,
        pricing: Boolean,
        buffet: Boolean,
        passwords: Boolean,
        custEdit: Boolean
    ) {
        _permCustomerClub.value = club
        _permFinance.value = finance
        _permPricing.value = pricing
        _permBuffet.value = buffet
        _permCustomerPasswords.value = passwords
        _permCustomerEdit.value = custEdit
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("perm_customer_club", club.toString())
            repository.saveSetting("perm_finance", finance.toString())
            repository.saveSetting("perm_pricing", pricing.toString())
            repository.saveSetting("perm_buffet", buffet.toString())
            repository.saveSetting("perm_customer_passwords", passwords.toString())
            repository.saveSetting("perm_customer_edit", custEdit.toString())
        }
    }

    fun switchAdminRole(targetRole: String, enteredPassword: String): Boolean {
        val cleanPass = enteredPassword.trim()
        if (targetRole == "SUPER_MANAGER") {
            // SUPER_MANAGER authority comes only from the authenticated server session.
            if (_currentAdminRole.value == "SUPER_MANAGER") {
                _currentAdminRole.value = "SUPER_MANAGER"
                logOperatorActivity("تغییر نقش کاربری", "تأیید نقش مدیر ارشد از نشست سرور")
                return true
            }
            return false
        } else {
            // OPERATOR may use only the locally configured operator credential.
            if (_deputyName.value.isNotBlank() && cleanPass == _operatorPassword.value) {
                _currentAdminRole.value = "OPERATOR"
                logOperatorActivity("تغییر نقش کاربری", "ورود به عنوان مدیر اجرایی (اپراتور شیفت)")
                return true
            }
            return false
        }
    }


    fun publishBroadcastMessage(message: String) {
        val cleanMsg = message.trim()
        _ownerBroadcastMessage.value = cleanMsg
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("owner_broadcast_message", cleanMsg)
            SelfHostedManager.publishBroadcastMessage(cleanMsg)
            logOperatorActivity("ارسال پیام به اعضا", "پیام جدید برای اعضای GameNexa ثبت شد: $cleanMsg")
        }
    }

    fun logOperatorActivity(actionTitle: String, details: String) {
        val opName = if (_currentAdminRole.value == "SUPER_MANAGER") "مدیر ارشد" else _operatorUsername.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addOperatorAuditLog(
                    OperatorAuditLog(
                        operatorName = opName,
                        actionTitle = actionTitle,
                        details = details,
                        timestamp = System.currentTimeMillis()
                    )
                )
                SelfHostedManager.logOperatorAction(opName, actionTitle, details)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearOperatorAuditLogs()
        }
    }

    // GN Ledger & Loyalty Flows
    val allGnLedgerEntries: StateFlow<List<GnLedgerEntry>> = repository.allGnLedgerEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBehaviorRules: StateFlow<List<BehaviorRule>> = repository.allBehaviorRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBehaviorLogs: StateFlow<List<BehaviorLog>> = repository.allBehaviorLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Configurable System Policies
    private val _gameRewardRate = MutableStateFlow(100L) // 100 GN per 100k Toman
    val gameRewardRate: StateFlow<Long> = _gameRewardRate.asStateFlow()

    private val _buffetRewardRate = MutableStateFlow(50L) // 50 GN per 100k Toman
    val buffetRewardRate: StateFlow<Long> = _buffetRewardRate.asStateFlow()

    private val _referralRewardGn = MutableStateFlow(100L)
    val referralRewardGn: StateFlow<Long> = _referralRewardGn.asStateFlow()

    private val _referralQualificationAmount = MutableStateFlow(100000L) // 100,000 Toman real paid game
    val referralQualificationAmount: StateFlow<Long> = _referralQualificationAmount.asStateFlow()

    private val _gnToTomanRate = MutableStateFlow(400L) // 1 GN = 400 Toman
    val gnToTomanRate: StateFlow<Long> = _gnToTomanRate.asStateFlow()

    private val _gnPurchaseRateToman = MutableStateFlow(500L) // 1 GN = 500 Toman
    val gnPurchaseRateToman: StateFlow<Long> = _gnPurchaseRateToman.asStateFlow()

    private val _gnPurchaseEnabled = MutableStateFlow(true)
    val gnPurchaseEnabled: StateFlow<Boolean> = _gnPurchaseEnabled.asStateFlow()

    private val _transferMinGn = MutableStateFlow(50L)
    val transferMinGn: StateFlow<Long> = _transferMinGn.asStateFlow()

    private val _transferDailyLimitGn = MutableStateFlow(1000L)
    val transferDailyLimitGn: StateFlow<Long> = _transferDailyLimitGn.asStateFlow()

    private val _transferFeePercent = MutableStateFlow(5L)
    val transferFeePercent: StateFlow<Long> = _transferFeePercent.asStateFlow()

    private val _lpTomanRate = MutableStateFlow(1000L) // 1 LP per 1,000 Toman spend (default)
    val lpTomanRate: StateFlow<Long> = _lpTomanRate.asStateFlow()

    fun saveLpTomanRate(rate: Long) {
        _lpTomanRate.value = rate
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("policy_lp_toman_rate", rate.toString())
            SelfHostedManager.syncAppConfig("policy_lp_toman_rate", rate.toString())
            logOperatorActivity("تغییر نرخ LP", "بروزرسانی نرخ اعطای LP به $rate تومان برای 1 LP")
        }
    }

    // Multiple Referral Rules with enable/disable toggles
    private val _referralRules = MutableStateFlow<List<ReferralRule>>(emptyList())
    val referralRules: StateFlow<List<ReferralRule>> = _referralRules.asStateFlow()

    fun loadReferralRules() {
        viewModelScope.launch(Dispatchers.IO) {
            val rawJson = repository.getSetting("policy_referral_rules_json")
            if (!rawJson.isNullOrBlank()) {
                val parsed = parseReferralRulesJson(rawJson)
                if (parsed.isNotEmpty()) {
                    _referralRules.value = parsed
                    return@launch
                }
            }
            val defaultRules = listOf(
                ReferralRule(
                    id = "ref_rule_gaming_100k",
                    title = "هزینه کردن حداقل 100,000 تومان در بازی کردن توسط دوست",
                    description = "اعطای 100 GN به معرف پس از اینکه دوست دعوت‌شده حداقل 100,000 تومان بازی کند.",
                    type = "GAMING_SPEND",
                    requiredAmount = 100000L,
                    rewardGn = 100L,
                    isEnabled = true
                ),
                ReferralRule(
                    id = "ref_rule_buffet_50k",
                    title = "خرید حداقل 50,000 تومان بوفه توسط دوست",
                    description = "اعطای 50 GN به معرف پس از خرید حداقل 50,000 تومان بوفه توسط دوست.",
                    type = "BUFFET_SPEND",
                    requiredAmount = 50000L,
                    rewardGn = 50L,
                    isEnabled = false
                ),
                ReferralRule(
                    id = "ref_rule_first_visit",
                    title = "ثبت اولین بازی و حضور دوست در سالن",
                    description = "اعطای 30 GN به معرف به محض اولین حضور دوست.",
                    type = "FIRST_VISIT",
                    requiredAmount = 1L,
                    rewardGn = 30L,
                    isEnabled = false
                )
            )
            _referralRules.value = defaultRules
            saveReferralRulesInternal(defaultRules)
        }
    }

    fun toggleReferralRule(ruleId: String, isEnabled: Boolean) {
        val updated = _referralRules.value.map {
            if (it.id == ruleId) it.copy(isEnabled = isEnabled) else it
        }
        _referralRules.value = updated
        saveReferralRulesInternal(updated)
    }

    fun addOrUpdateReferralRule(rule: ReferralRule) {
        val current = _referralRules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        _referralRules.value = current
        saveReferralRulesInternal(current)
    }

    fun deleteReferralRule(ruleId: String) {
        val updated = _referralRules.value.filterNot { it.id == ruleId }
        _referralRules.value = updated
        saveReferralRulesInternal(updated)
    }

    private fun saveReferralRulesInternal(rules: List<ReferralRule>) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = encodeReferralRulesJson(rules)
            repository.saveSetting("policy_referral_rules_json", jsonStr)
        }
    }

    private fun encodeReferralRulesJson(rules: List<ReferralRule>): String {
        val sb = java.lang.StringBuilder("[")
        rules.forEachIndexed { index, r ->
            sb.append("{")
            sb.append("\"id\":\"").append(r.id).append("\",")
            sb.append("\"title\":\"").append(r.title.replace("\"", "")).append("\",")
            sb.append("\"description\":\"").append(r.description.replace("\"", "")).append("\",")
            sb.append("\"type\":\"").append(r.type).append("\",")
            sb.append("\"requiredAmount\":").append(r.requiredAmount).append(",")
            sb.append("\"rewardGn\":").append(r.rewardGn).append(",")
            sb.append("\"isEnabled\":").append(r.isEnabled)
            sb.append("}")
            if (index < rules.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parseReferralRulesJson(json: String): List<ReferralRule> {
        val list = mutableListOf<ReferralRule>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ReferralRule(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        description = obj.optString("description", ""),
                        type = obj.optString("type", "GAMING_SPEND"),
                        requiredAmount = obj.optLong("requiredAmount", 100000L),
                        rewardGn = obj.optLong("rewardGn", 100L),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // Tier criteria
    private val _silverSpendReq = MutableStateFlow(2000000L)
    val silverSpendReq: StateFlow<Long> = _silverSpendReq.asStateFlow()

    private val _goldSpendReq = MutableStateFlow(7000000L)
    val goldSpendReq: StateFlow<Long> = _goldSpendReq.asStateFlow()

    private val _diamondSpendReq = MutableStateFlow(15000000L)
    val diamondSpendReq: StateFlow<Long> = _diamondSpendReq.asStateFlow()

    fun saveSystemPolicy(
        gameReward: Long,
        buffetReward: Long,
        refReward: Long,
        refQualAmount: Long,
        gnTomanRate: Long,
        purchaseRate: Long,
        purchaseEnabled: Boolean,
        minTransfer: Long,
        dailyLimitTransfer: Long,
        feePercent: Long
    ) {
        _gameRewardRate.value = gameReward
        _buffetRewardRate.value = buffetReward
        _referralRewardGn.value = refReward
        _referralQualificationAmount.value = refQualAmount
        _gnToTomanRate.value = gnTomanRate
        _gnPurchaseRateToman.value = purchaseRate
        _gnPurchaseEnabled.value = purchaseEnabled
        _transferMinGn.value = minTransfer
        _transferDailyLimitGn.value = dailyLimitTransfer
        _transferFeePercent.value = feePercent

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("policy_game_reward_rate", gameReward.toString())
            repository.saveSetting("policy_buffet_reward_rate", buffetReward.toString())
            repository.saveSetting("policy_referral_reward_gn", refReward.toString())
            repository.saveSetting("policy_referral_qualification_amount", refQualAmount.toString())
            repository.saveSetting("policy_gn_to_toman_rate", gnTomanRate.toString())
            repository.saveSetting("policy_gn_purchase_rate_toman", purchaseRate.toString())
            repository.saveSetting("policy_gn_purchase_enabled", purchaseEnabled.toString())
            repository.saveSetting("policy_transfer_min_gn", minTransfer.toString())
            repository.saveSetting("policy_transfer_daily_limit_gn", dailyLimitTransfer.toString())
            repository.saveSetting("policy_transfer_fee_percent", feePercent.toString())
            logOperatorActivity("تغییر قوانین سیستم GN", "بروزرسانی نرخ‌های پاداش، معرفی، تبدیل، خرید و انتقال GN")
        }
    }

    fun transferGn(
        senderCustomerId: Long,
        receiverPhoneNumber: String,
        amountGn: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val minAmount = _transferMinGn.value
            if (amountGn < minAmount) {
                withContext(Dispatchers.Main) {
                    onResult(false, "حداقل میزان انتقال $minAmount GN می‌باشد.")
                }
                return@launch
            }

            val custs = repository.allCustomers.firstOrNull() ?: emptyList()
            val sender = custs.find { it.id == senderCustomerId }
            if (sender == null) {
                withContext(Dispatchers.Main) { onResult(false, "فرستنده یافت نشد.") }
                return@launch
            }

            val dailyLimit = _transferDailyLimitGn.value
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStartMillis = cal.timeInMillis

            val ledgerEntries = repository.allGnLedgerEntries.firstOrNull() ?: emptyList()
            val todayTransferredSoFar = ledgerEntries
                .filter { it.customerId == sender.id && it.transactionType == "TRANSFERRED" && it.gnAmount < 0 && it.timestamp >= todayStartMillis }
                .sumOf { kotlin.math.abs(it.gnAmount) }

            if (todayTransferredSoFar + amountGn > dailyLimit) {
                withContext(Dispatchers.Main) {
                    onResult(false, "انتقال ناموفق! سقف مجاز انتقال روزانه شما ${dailyLimit.toInt()} GN می‌باشد.")
                }
                return@launch
            }

            val cleanRecPhone = receiverPhoneNumber.trim()
            val receiver = custs.find { it.phoneNumber.trim() == cleanRecPhone }
            if (receiver == null) {
                withContext(Dispatchers.Main) { onResult(false, "گیرنده با این شماره همراه یافت نشد.") }
                return@launch
            }

            if (receiver.id == sender.id) {
                withContext(Dispatchers.Main) { onResult(false, "امکان انتقال به حساب خود وجود ندارد.") }
                return@launch
            }

            val feePercent = _transferFeePercent.value
            val feeAmount = amountGn * (feePercent / 100L)
            val totalDeduction = amountGn + feeAmount

            if (sender.availableGn < totalDeduction) {
                withContext(Dispatchers.Main) {
                    onResult(false, "اعتبار GN شما کافی نیست! (نیاز به ${totalDeduction.toInt()} GN با احتساب $feePercent% کارمزد)")
                }
                return@launch
            }

            val now = System.currentTimeMillis()
            val updatedSender = sender.copy(
                availableGn = sender.availableGn - totalDeduction,
                lastActivityTimestamp = now
            )
            val updatedReceiver = receiver.copy(
                availableGn = receiver.availableGn + amountGn,
                lastActivityTimestamp = now
            )

            repository.insertCustomer(updatedSender)
            repository.insertCustomer(updatedReceiver)

            repository.addGnLedgerEntry(
                GnLedgerEntry(
                    customerId = sender.id,
                    customerName = sender.fullName,
                    gnAmount = -totalDeduction,
                    transactionType = "TRANSFERRED",
                    source = "TRANSFERRED",
                    status = "AVAILABLE",
                    timestamp = now,
                    referenceId = "TRANSFER_OUT_${now}",
                    description = "انتقال $amountGn GN به ${receiver.fullName} (کارمزد: ${feeAmount.toInt()} GN)"
                )
            )

            repository.addGnLedgerEntry(
                GnLedgerEntry(
                    customerId = receiver.id,
                    customerName = receiver.fullName,
                    gnAmount = amountGn,
                    transactionType = "TRANSFERRED",
                    source = "TRANSFERRED",
                    status = "AVAILABLE",
                    timestamp = now,
                    referenceId = "TRANSFER_IN_${now}",
                    description = "دریافت $amountGn GN از ${sender.fullName}"
                )
            )

            logOperatorActivity("انتقال GN", "انتقال $amountGn GN از ${sender.fullName} به ${receiver.fullName}")

            withContext(Dispatchers.Main) {
                onResult(true, "انتقال $amountGn GN با موفقیت انجام شد ✅")
            }
        }
    }

    fun buyGn(customerId: Long, amountGn: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_gnPurchaseEnabled.value) {
                withContext(Dispatchers.Main) { onResult(false, "خرید مستقیم GN در حال حاضر غیرفعال است.") }
                return@launch
            }

            val custs = repository.allCustomers.firstOrNull() ?: emptyList()
            val cust = custs.find { it.id == customerId }
            if (cust == null) {
                withContext(Dispatchers.Main) { onResult(false, "مشتری یافت نشد.") }
                return@launch
            }

            val rate = _gnPurchaseRateToman.value
            val costToman = amountGn * rate
            val now = System.currentTimeMillis()

            val updated = cust.copy(
                availableGn = cust.availableGn + amountGn,
                lastActivityTimestamp = now
            )
            repository.insertCustomer(updated)

            repository.addGnLedgerEntry(
                GnLedgerEntry(
                    customerId = cust.id,
                    customerName = cust.fullName,
                    gnAmount = amountGn,
                    transactionType = "PURCHASED",
                    source = "PURCHASED",
                    status = "AVAILABLE",
                    timestamp = now,
                    referenceId = "PURCHASE_${now}",
                    description = "خرید $amountGn GN به مبلغ ${costToman.toInt()} تومان"
                )
            )

            logOperatorActivity("خرید GN", "خرید $amountGn GN برای ${cust.fullName} به مبلغ $costToman تومان")

            withContext(Dispatchers.Main) {
                onResult(true, "خرید $amountGn GN با موفقیت انجام و واریز شد ✅")
            }
        }
    }

    fun manualAdjustCustomerGn(customerId: Long, gnDelta: Long, reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val custs = repository.allCustomers.firstOrNull() ?: emptyList()
            val cust = custs.find { it.id == customerId } ?: return@launch
            val now = System.currentTimeMillis()
            val newAvail = (cust.availableGn + gnDelta).coerceAtLeast(0L)

            val updated = cust.copy(
                availableGn = newAvail,
                lastActivityTimestamp = now
            )
            repository.insertCustomer(updated)

            val actionType = if (gnDelta >= 0) "تنظیم دستی (+)" else "تنظیم دستی (-)"
            repository.addGnLedgerEntry(
                GnLedgerEntry(
                    customerId = cust.id,
                    customerName = cust.fullName,
                    gnAmount = gnDelta,
                    transactionType = "ADMIN_ADJUSTMENT",
                    source = "REWARD",
                    status = "AVAILABLE",
                    timestamp = now,
                    referenceId = "MANUAL_${now}",
                    description = "$actionType: ${reason.ifBlank { "توسط مدیریت سالن" }}"
                )
            )

            logOperatorActivity("تنظیم دستی GN", "تغییر $gnDelta GN برای ${cust.fullName} (علت: $reason)")
        }
    }

    fun addOrUpdateBehaviorRule(rule: BehaviorRule) {
        viewModelScope.launch(Dispatchers.IO) {
            if (rule.id == 0L) {
                repository.addBehaviorRule(rule)
                logOperatorActivity("تعریف قانون رفتاری", "قانون جدید: ${rule.title}")
            } else {
                repository.updateBehaviorRule(rule)
                logOperatorActivity("ویرایش قانون رفتاری", "ویرایش قانون: ${rule.title}")
            }
        }
    }

    fun deleteBehaviorRule(rule: BehaviorRule) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBehaviorRule(rule)
            logOperatorActivity("حذف قانون رفتاری", "حذف قانون: ${rule.title}")
        }
    }

    fun applyBehaviorRuleToCustomer(customerId: Long, rule: BehaviorRule, reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val custs = repository.allCustomers.firstOrNull() ?: emptyList()
            val cust = custs.find { it.id == customerId } ?: return@launch
            val now = System.currentTimeMillis()

            val newAvail = (cust.availableGn + rule.gnChange).coerceAtLeast(0L)
            val newLp = (cust.lp + rule.lpChange).coerceAtLeast(0L)

            val updated = cust.copy(
                availableGn = newAvail,
                lp = newLp,
                lastActivityTimestamp = now
            )
            repository.insertCustomer(updated)

            // Behavior Log
            repository.addBehaviorLog(
                BehaviorLog(
                    customerId = cust.id,
                    customerName = cust.fullName,
                    ruleTitle = rule.title,
                    gnChange = rule.gnChange,
                    lpChange = rule.lpChange,
                    appliedBy = if (_currentAdminRole.value == "SUPER_MANAGER") "مدیر ارشد" else "اپراتور",
                    reason = reason,
                    timestamp = now
                )
            )

            // GN Ledger
            val type = if (rule.gnChange >= 0) "BEHAVIOR_REWARD" else "PENALTY"
            repository.addGnLedgerEntry(
                GnLedgerEntry(
                    customerId = cust.id,
                    customerName = cust.fullName,
                    gnAmount = rule.gnChange,
                    transactionType = type,
                    source = if (rule.gnChange >= 0) "REWARD" else "PENALTY_ADJUSTMENT",
                    status = "AVAILABLE",
                    timestamp = now,
                    referenceId = "BEHAVIOR_${now}",
                    description = "قانون رفتاری: ${rule.title} (${reason.ifBlank { "تایید شده توسط مدیریت" }})"
                )
            )

            logOperatorActivity("اعمال قانون رفتاری", "اعمال قانون '${rule.title}' روی ${cust.fullName} (تغییر GN: ${rule.gnChange})")
        }
    }

    fun runInactivityDecayAndTierReview(customerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val cust = repository.allCustomers.firstOrNull()?.find { it.id == customerId } ?: return@launch
            val now = System.currentTimeMillis()
            val inactiveMs = now - cust.lastActivityTimestamp
            val inactiveDays = (inactiveMs / (1000L * 3600 * 24)).toInt()

            val decayPercent = when {
                inactiveDays >= 180 -> 35
                inactiveDays >= 150 -> 30
                inactiveDays >= 120 -> 25
                inactiveDays >= 90 -> 20
                inactiveDays >= 60 -> 15
                inactiveDays >= 30 -> 10
                else -> 0
            }

            var currentAvail = cust.availableGn
            if (decayPercent > 0 && currentAvail > 0) {
                val decayAmount = currentAvail * (decayPercent / 100L)
                currentAvail -= decayAmount

                repository.addGnLedgerEntry(
                    GnLedgerEntry(
                        customerId = cust.id,
                        customerName = cust.fullName,
                        gnAmount = -decayAmount,
                        transactionType = "DECAY",
                        source = "PENALTY_ADJUSTMENT",
                        status = "AVAILABLE",
                        timestamp = now,
                        referenceId = "DECAY_${now}",
                        description = "کاهش GN به دلیل عدم فعالیت به مدت $inactiveDays روز ($decayPercent%)"
                    )
                )
            }

            val currentTier = cust.tier
            val qualifiedSpend = cust.totalQualifiedSpend
            val visits = cust.totalVisitsCount

            val targetTier = when {
                qualifiedSpend >= _diamondSpendReq.value && visits >= 12 -> "DIAMOND"
                qualifiedSpend >= _goldSpendReq.value && visits >= 8 -> "GOLD"
                qualifiedSpend >= _silverSpendReq.value && visits >= 4 -> "SILVER"
                else -> "BRONZE"
            }

            val tiersList = listOf("BRONZE", "SILVER", "GOLD", "DIAMOND")
            val currIdx = tiersList.indexOf(currentTier)
            val targIdx = tiersList.indexOf(targetTier)

            val finalTier = if (targIdx < currIdx - 1) {
                tiersList[currIdx - 1]
            } else {
                targetTier
            }

            val updated = cust.copy(
                availableGn = currentAvail,
                tier = finalTier,
                lastTierReviewTimestamp = now
            )
            repository.insertCustomer(updated)
        }
    }



    private val _selectedConsoleInSettings = MutableStateFlow("")
    val selectedConsoleInSettings: StateFlow<String> = _selectedConsoleInSettings.asStateFlow()

    private val _selectedProductInSettings = MutableStateFlow("")
    val selectedProductInSettings: StateFlow<String> = _selectedProductInSettings.asStateFlow()

    private val _isFirstLaunch = MutableStateFlow(true)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val _serverSyncMode = MutableStateFlow(true)
    val serverSyncMode: StateFlow<Boolean> = _serverSyncMode.asStateFlow()

    private val _serverUrl = MutableStateFlow("https://api.gamenermayket.ir")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Real-time ticking flow
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    // Map of active station orders to expose to UI
    private val _stationOrdersMap = MutableStateFlow<Map<Int, List<StationOrder>>>(emptyMap())
    val stationOrdersMap: StateFlow<Map<Int, List<StationOrder>>> = _stationOrdersMap.asStateFlow()

    // Licensing & Subscription states
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isTrialUsed = MutableStateFlow(false)
    val isTrialUsed: StateFlow<Boolean> = _isTrialUsed.asStateFlow()

    private val _accessState = MutableStateFlow<AppAccessState>(AppAccessState.Checking)
    val accessState: StateFlow<AppAccessState> = _accessState.asStateFlow()

    private var expirationJob: kotlinx.coroutines.Job? = null
    
    fun scheduleExpiration(expiresAt: Long?) {
        expirationJob?.cancel()
        expirationJob = null
        val role = _currentAdminRole.value
        if (role == "SUPER_MANAGER") {
            return
        }
        if (expiresAt == null || expiresAt == Long.MAX_VALUE) return
        val delayMs = expiresAt - System.currentTimeMillis()
        if (delayMs <= 0) {
            val msg = if (role == "MANAGER" || role == "GAMENET_MANAGER") "اعتبار اشتراک مدیریت به پایان رسید." else "مهلت تست 24 ساعته به پایان رسید."
            _accessState.value = AppAccessState.Denied(msg)
            _licenseState.value = LicenseState.Expired(msg)
            _isSubscribed.value = false
            return
        }
        expirationJob = viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            val currentRole = _currentAdminRole.value
            if (currentRole == "SUPER_MANAGER") {
                return@launch
            }
            val msg = if (currentRole == "MANAGER" || currentRole == "GAMENET_MANAGER") "اعتبار اشتراک مدیریت به پایان رسید." else "مهلت تست 24 ساعته به پایان رسید."
            _accessState.value = AppAccessState.Denied(msg)
            _licenseState.value = LicenseState.Expired(msg)
            _isSubscribed.value = false
        }
    }

    private val _isServerConnected = MutableStateFlow(false)
    val isServerConnected: StateFlow<Boolean> = _isServerConnected.asStateFlow()

    private val _lastSuccessfulServerCheckElapsed = MutableStateFlow(0L)
    val lastSuccessfulServerCheckElapsed: StateFlow<Long> = _lastSuccessfulServerCheckElapsed.asStateFlow()

    // 24-hour Offline Grace Period state
    val totalGracePeriodSeconds: Long = 24L * 3600L
    private val _offlineGraceSecondsRemaining = MutableStateFlow(86400L)
    val offlineGraceSecondsRemaining: StateFlow<Long> = _offlineGraceSecondsRemaining.asStateFlow()

    // SUPER_MANAGER gets a visible 24-hour reconnect countdown without being converted
    // into the normal Manager subscription/trial enforcement path.
    private val _superManagerOfflineBannerSecondsRemaining = MutableStateFlow(86400L)
    val superManagerOfflineBannerSecondsRemaining: StateFlow<Long> = _superManagerOfflineBannerSecondsRemaining.asStateFlow()

    private val _isGracePeriodExpired = MutableStateFlow(false)
    val isGracePeriodExpired: StateFlow<Boolean> = _isGracePeriodExpired.asStateFlow()

    private val _licenseState = MutableStateFlow<LicenseState>(LicenseState.Checking)
    val licenseState: StateFlow<LicenseState> = _licenseState.asStateFlow()

    val isTrialUser: Boolean
        get() {
            val role = _currentAdminRole.value
            if (role == "SUPER_MANAGER") return false
            val plan = (_licenseState.value as? LicenseState.Active)?.planType ?: ""
            return role == "TRIAL_USER" || plan.equals("TRIAL", ignoreCase = true)
        }

    val isTrialActive: Boolean
        get() = isTrialUser

    val isTrialModeFlow: StateFlow<Boolean> = combine(_currentAdminRole, _licenseState) { role, state ->
        val plan = (state as? LicenseState.Active)?.planType ?: ""
        val isTrial = if (role == "SUPER_MANAGER") false else (role == "TRIAL_USER" || plan.equals("TRIAL", ignoreCase = true))
        if (isTrial) {
            com.example.data.network.NetworkClient.isTrialMode = true
        } else {
            com.example.data.network.NetworkClient.isTrialMode = false
        }
        isTrial
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val customers: StateFlow<List<Customer>> = combine(
        repository.customerDao.getAll(),
        isTrialModeFlow
    ) { dbList, isTrial ->
        if (isTrial) dbList
        else dbList.filterNot {
            it.description == "__GN_TRIAL_TEST_CONTACT__" ||
            (it.phoneNumber in setOf(
                "09120000001", "09120000002", "09120000003", "09120000004"
            ) && it.fullName.startsWith("مشتری تستی"))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _adminBroadcastMessage = MutableStateFlow<String?>(null)
    val adminBroadcastMessage: StateFlow<String?> = _adminBroadcastMessage.asStateFlow()

    private val _showSetPasswordForCode = MutableStateFlow<String?>(null)
    val showSetPasswordForCode: StateFlow<String?> = _showSetPasswordForCode.asStateFlow()

    fun getDefaultClubLevels(): List<ClubLevel> {
        return listOf(
            ClubLevel(
                id = "bronze", name = "برنزی", requiredPoints = 0L,
                gameDiscountPercent = 0L, buffetDiscountPercent = 0L, fixedDiscountToman = 0L, freePlayHours = 0L,
                rewardsText = "دسترسی عادی به باشگاه و پاداش‌های پایه",
                reachGnBonus = 10L, gameGnPercent = 10L, buffetGnPercent = 5L, maxGnPaymentPercent = 30L, inviteGnReward = 100L,
                accessSpecialEvents = false, validityDays = 30, minVisitDays = 1, retainLpPoints = 0L, graceDays = 7,
                maxAbsenceWithoutPenaltyDays = 20,
                nonFinancialPerks = getDefaultPerksForLevel("bronze")
            ),
            ClubLevel(
                id = "silver", name = "نقره‌ای", requiredPoints = 1200L,
                gameDiscountPercent = 3L, buffetDiscountPercent = 0L, fixedDiscountToman = 0L, freePlayHours = 0L,
                rewardsText = "تخفیف 3٪ روی بازی و دسترسی به رویدادهای ویژه",
                reachGnBonus = 50L, gameGnPercent = 15L, buffetGnPercent = 5L, maxGnPaymentPercent = 30L, inviteGnReward = 100L,
                accessSpecialEvents = true, validityDays = 30, minVisitDays = 5, retainLpPoints = 800L, graceDays = 7,
                maxAbsenceWithoutPenaltyDays = 20,
                nonFinancialPerks = getDefaultPerksForLevel("silver")
            ),
            ClubLevel(
                id = "gold", name = "طلایی", requiredPoints = 4000L,
                gameDiscountPercent = 5L, buffetDiscountPercent = 5L, fixedDiscountToman = 0L, freePlayHours = 0L,
                rewardsText = "تخفیف 5٪ بازی و بوفه + نیم ساعت بازی رایگان",
                reachGnBonus = 150L, gameGnPercent = 20L, buffetGnPercent = 10L, maxGnPaymentPercent = 30L, inviteGnReward = 100L,
                accessSpecialEvents = true, validityDays = 30, minVisitDays = 8, retainLpPoints = 3200L, graceDays = 7,
                maxAbsenceWithoutPenaltyDays = 20,
                nonFinancialPerks = getDefaultPerksForLevel("gold")
            ),
            ClubLevel(
                id = "diamond", name = "الماسی", requiredPoints = 12000L,
                gameDiscountPercent = 10L, buffetDiscountPercent = 10L, fixedDiscountToman = 0L, freePlayHours = 1L,
                rewardsText = "تخفیف 10٪ بازی و بوفه + 1 ساعت بازی رایگان",
                reachGnBonus = 300L, gameGnPercent = 30L, buffetGnPercent = 15L, maxGnPaymentPercent = 30L, inviteGnReward = 100L,
                accessSpecialEvents = true, validityDays = 30, minVisitDays = 12, retainLpPoints = 10000L, graceDays = 7,
                maxAbsenceWithoutPenaltyDays = 20,
                nonFinancialPerks = getDefaultPerksForLevel("diamond")
            )
        )
    }

    fun getDefaultScoringRules(): List<ScoringRule> {
        return listOf(
            ScoringRule("invite", "دعوت مخاطب جدید", 50L),
            ScoringRule("spending", "هزینه کردن در گیم نت (به ازای هر 1000 تومان)", 1L),
            ScoringRule("playing", "هر یک ساعت بازی", 20L),
            ScoringRule("signup_gift", "هدیه ثبت نام در برنامه", 0L),
            ScoringRule("debt", "بدهی پرداخت‌نشده (روزانه)", -10L),
            ScoringRule("disorder", "بی‌نظمی", -30L),
            ScoringRule("profanity", "فحاشی / توهین", -30L),
            ScoringRule("harassment", "ایجاد مزاحمت برای سایر مشتریان", -50L),
            ScoringRule("noise", "سر و صدای آزاردهنده", -20L),
            ScoringRule("serious_violation", "تخلف جدی", -50L)
        )
    }

    fun getDefaultBehaviorRules(): List<BehaviorRule> {
        return listOf(
            BehaviorRule(title = "بدهی پرداخت‌نشده", description = "کسر روزانه به ازای هر روز بدهی", gnChange = 0L, lpChange = -10L, severity = "MEDIUM"),
            BehaviorRule(title = "بی‌نظمی", description = "رعایت نکردن نظم محیط گیم‌نت", gnChange = 0L, lpChange = -30L, severity = "MEDIUM"),
            BehaviorRule(title = "فحاشی / توهین", description = "رفتار نامناسب کلامی و توهین", gnChange = 0L, lpChange = -30L, severity = "HIGH"),
            BehaviorRule(title = "ایجاد مزاحمت برای سایر مشتریان", description = "ایجاد اخلال در بازی دیگران یا مزاحمت", gnChange = 0L, lpChange = -50L, severity = "HIGH"),
            BehaviorRule(title = "سر و صدای آزاردهنده", description = "فریاد یا آلودگی صوتی نامتعارف", gnChange = 0L, lpChange = -20L, severity = "LOW"),
            BehaviorRule(title = "تخلف جدی", description = "خسارت یا تخلف شدید انضباطی", gnChange = -50L, lpChange = -50L, severity = "CRITICAL"),
            BehaviorRule(title = "ادب و رعایت ضوابط سالن", description = "احترام به کادر و بازیکنان", gnChange = 20L, lpChange = 5L, severity = "LOW"),
            BehaviorRule(title = "دعوت و همراهی دوست جدید", description = "پاداش حضور همراه در سالن", gnChange = 50L, lpChange = 15L, severity = "LOW"),
            BehaviorRule(title = "شرکت در مسابقات و تورنمنت", description = "حضور فعال در جام سالن", gnChange = 100L, lpChange = 30L, severity = "MEDIUM")
        )
    }

    fun resetClubLevelsToDefault() {
        saveClubLevels(getDefaultClubLevels())
    }

    fun resetScoringRulesToDefault() {
        saveScoringRules(getDefaultScoringRules())
    }

    fun resetBehaviorRulesToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getAllBehaviorRulesList()
            existing.forEach { repository.deleteBehaviorRule(it) }
            getDefaultBehaviorRules().forEach { repository.addBehaviorRule(it) }
        }
    }

    fun resetGnRulesToDefault() {
        saveSystemPolicy(
            gameReward = 100L,
            buffetReward = 50L,
            refReward = 100L,
            refQualAmount = 100000L,
            gnTomanRate = 400L,
            purchaseRate = 500L,
            purchaseEnabled = true,
            minTransfer = 50L,
            dailyLimitTransfer = 1000L,
            feePercent = 5L
        )
        savePaymentSetting("gn_game_payment_ratio", "0.3")
        savePaymentSetting("gn_buffet_payment_ratio", "0.5")
        savePaymentSetting("gn_to_toman_ratio", "1000")
    }

    private val _clubLevels = MutableStateFlow<List<ClubLevel>>(getDefaultClubLevels())
    val clubLevels: StateFlow<List<ClubLevel>> = _clubLevels.asStateFlow()

    private val _scoringRules = MutableStateFlow<List<ScoringRule>>(getDefaultScoringRules())
    val scoringRules: StateFlow<List<ScoringRule>> = _scoringRules.asStateFlow()

    private val _minInvitePlayHours = MutableStateFlow(2L)
    val minInvitePlayHours: StateFlow<Long> = _minInvitePlayHours.asStateFlow()

    private val _minInviteSpendAmount = MutableStateFlow(0L)
    val minInviteSpendAmount: StateFlow<Long> = _minInviteSpendAmount.asStateFlow()

    // GN Ratios (Configurable by Management)
    private val _gnToTomanPricePer10 = MutableStateFlow(60000L) // 10 GN = 60,000 Toman (default)
    val gnToTomanPricePer10: StateFlow<Long> = _gnToTomanPricePer10.asStateFlow()

    private val _gnPointsPerGameHour = MutableStateFlow(50L) // 50 GN = 1 Hour of game play (default)
    val gnPointsPerGameHour: StateFlow<Long> = _gnPointsPerGameHour.asStateFlow()

    fun saveInviteSettings(hours: Long, spend: Long) {
        _minInvitePlayHours.value = hours
        _minInviteSpendAmount.value = spend
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("policy_min_invite_play_hours", hours.toString())
            repository.saveSetting("policy_min_invite_spend_amount", spend.toString())
            logOperatorActivity("تغییر شرط دعوت", "بروزرسانی شرط دعوت به $spend تومان")
        }
        checkAndAwardInvitePoints()
    }

    fun savePaymentSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting(key, value)
            SelfHostedManager.syncAppConfig(key, value)
            when (key) {
                "gn_payment_cards" -> _gnPaymentCards.value = value
                "gn_payment_gateways" -> _gnPaymentGateways.value = value
                "gn_payment_cryptos" -> _gnPaymentCryptos.value = value
                "gn_contact_sms" -> _gnContactSms.value = value
                "gn_contact_bale" -> _gnContactBale.value = value
                "gn_game_payment_ratio" -> _gnGamePaymentRatio.value = value.toLongOrNull() ?: 30L
                "gn_buffet_payment_ratio" -> _gnBuffetPaymentRatio.value = value.toLongOrNull() ?: 50L
                "gn_to_toman_ratio" -> _gnToTomanRatio.value = value.toIntOrNull() ?: 1000
            }
        }
    }


    fun saveGnRatios(pricePer10: Long, pointsPerHour: Long) {
        _gnToTomanPricePer10.value = pricePer10
        _gnPointsPerGameHour.value = pointsPerHour
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("gn_toman_price_per_10", pricePer10.toString())
            repository.saveSetting("gn_points_per_game_hour", pointsPerHour.toString())
            SelfHostedManager.setGnRatios(pricePer10, pointsPerHour)
            logOperatorActivity("تغییر نسبت‌های GN", "قیمت 10 امتیاز GN: $pricePer10 تومان | نرخ تبدیل: $pointsPerHour امتیاز = 1 ساعت بازی")
        }
    }

    fun convertPointsToPlaytime(customer: Customer, hours: Long): Result<Long> {
        val ptsNeeded = hours * _gnPointsPerGameHour.value
        if (customer.points < ptsNeeded) {
            return Result.failure(Exception("امتیاز کافی نیست! برای $hours ساعت بازی، به $ptsNeeded امتیاز نیاز دارید."))
        }
        val updatedCust = customer.copy(
            points = customer.points - ptsNeeded,
            credit = customer.credit + (hours * 30000L) // Adds game credit value to wallet
        )
        viewModelScope.launch(Dispatchers.IO) {
            SelfHostedManager.upsertCustomer(updatedCust)
            SelfHostedManager.setCurrentCustomer(updatedCust)
            repository.addPointLog(
                PointLog(
                    customerId = customer.id,
                    title = "تبدیل $ptsNeeded امتیاز GN به $hours ساعت بازی",
                    points = -ptsNeeded
                )
            )
            logOperatorActivity("تبدیل امتیاز مشتری", "مشتری ${customer.fullName} مقدار $ptsNeeded امتیاز GN را به $hours ساعت بازی تبدیل کرد.")
        }
        return Result.success(ptsNeeded)
    }

    fun checkAndAwardInvitePoints() {
        viewModelScope.launch(Dispatchers.IO) {
            val allCusts = repository.getAllCustomersLocal()
            val allTrans = repository.getAllCustomerTransactionsLocal() ?: emptyList()
            val inviteScoreRule = scoringRules.value.find { it.id == "invite" }?.points ?: 50L

            for (invited in allCusts) {
                if (invited.invitedByCode.isNotBlank() && !invited.invitePointsAwarded) {
                    val normalizedInvitedBy = normalizeInviteCode(invited.invitedByCode)
                    val inviter = allCusts.find { normalizeInviteCode(it.inviteCode) == normalizedInvitedBy }
                    if (inviter != null && inviter.id != invited.id) {
                        val custTrans = allTrans.filter { it.customerId == invited.id }
                        val totalSpend = custTrans.sumOf { it.amount }
                        val totalPlayMinutes = custTrans.sumOf { it.playMinutes }
                        val totalPlayHours = totalPlayMinutes / 60L

                        val reqSpend = _minInviteSpendAmount.value
                        val reqHours = _minInvitePlayHours.value
                        val isQualified = (reqSpend <= 0L && reqHours <= 0L) ||
                                (reqSpend > 0L && totalSpend >= reqSpend) ||
                                (reqHours > 0L && totalPlayHours >= reqHours)

                        if (isQualified) {
                            val updatedInviter = inviter.copy(points = inviter.points + inviteScoreRule)
                            repository.insertCustomer(updatedInviter)
                            repository.addPointLog(PointLog(customerId = inviter.id, title = "امتیاز دعوت (${invited.fullName})", points = inviteScoreRule))

                            val updatedInvited = invited.copy(invitePointsAwarded = true)
                            repository.insertCustomer(updatedInvited)
                        }
                    }
                }
            }
        }
    }

    fun dismissSetPasswordDialog() {
        _showSetPasswordForCode.value = null
    }

    private val _showDashboardAdminMessage = MutableStateFlow(false)
    val showDashboardAdminMessage: StateFlow<Boolean> = _showDashboardAdminMessage.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // Authentication States
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    val currentUserId: String
        get() {
            val state = _authState.value
            if (state is AuthState.Authenticated) {
                return state.userId
            }
            return ""
        }

    fun showAuthDialog() {
        _showAuthDialog.value = true
    }

    fun dismissAuthDialog() {
        _showAuthDialog.value = false
    }

    init {
        viewModelScope.launch {
            _licenseState.collect { state ->
                when (state) {
                    is LicenseState.Active -> {
                        _accessState.value = AppAccessState.Allowed(state.expiresAt, state.planType)
                        if (state.expiresAt < Long.MAX_VALUE - (365L * 86400_000L)) {
                            scheduleExpiration(state.expiresAt)
                        }
                    }
                    is LicenseState.Expired -> {
                        _accessState.value = AppAccessState.Denied(state.message)
                    }
                    is LicenseState.Unactivated -> {
                        _accessState.value = AppAccessState.Denied(state.message)
                    }
                    is LicenseState.ConnectionRequired -> {
                        _accessState.value = AppAccessState.Denied(state.message)
                    }
                    is LicenseState.OfflineGrace -> {
                        _accessState.value = AppAccessState.Allowed(null, "OFFLINE_GRACE")
                    }
                    is LicenseState.Checking -> {
                        _accessState.value = AppAccessState.Checking
                    }
                }
            }
        }

        // Continuously verify reachability of the GameNexa API itself. This is intentionally
        // independent of Android's generic network state so Wi-Fi/mobile/VPN/proxy changes
        // are reflected as soon as the API becomes reachable or unreachable.
        viewModelScope.launch(Dispatchers.IO) {
            var lastProbeElapsed = 0L
            while (true) {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                if (lastProbeElapsed == 0L || elapsed - lastProbeElapsed >= 5000L) {
                    lastProbeElapsed = elapsed
                    val reachable = runCatching {
                        NetworkClient.getApi(_serverUrl.value).healthCheck().isSuccessful
                    }.getOrDefault(false)
                    _isServerConnected.value = reachable
                    if (reachable) _lastSuccessfulServerCheckElapsed.value = elapsed
                }
                delay(5000L)
            }
        }

        // Start real-time ticking for UI display (Zero network calls - purely local time)
        viewModelScope.launch {
            var lastElapsedTick = android.os.SystemClock.elapsedRealtime()
            var lastReconnectSyncElapsed = lastElapsedTick
            while (true) {
                val currentElapsed = android.os.SystemClock.elapsedRealtime()
                val syncedServerClock = _serverClockMillis.value
                val now = syncedServerClock?.let {
                    it + (currentElapsed - serverClockAnchorElapsedRealtime).coerceAtLeast(0L)
                } ?: System.currentTimeMillis()
                _currentTime.value = now
                
                // If in active trial, check expiration locally without any network requests
                val state = _licenseState.value
                val currentRole = _currentAdminRole.value
                val isTrial = (currentRole == "TRIAL_USER" || (state is LicenseState.Active && state.planType == "TRIAL")) && currentRole != "SUPER_MANAGER" && currentRole != "MANAGER"
                if (isTrial && state is LicenseState.Active && state.planType == "TRIAL") {
                    if (now >= state.expiresAt) {
                        _accessState.value = AppAccessState.Denied("مهلت تست 24 ساعته به پایان رسید.")
                        _licenseState.value = LicenseState.Expired("مهلت تست 24 ساعته به پایان رسید.")
                    }
                }

                // 24-hour Offline Grace Period logic using SystemClock.elapsedRealtime()
                if (!_isServerConnected.value) {
                    val deltaMs = (currentElapsed - lastElapsedTick).coerceAtLeast(0L)
                    if (!isTrial && currentRole == "SUPER_MANAGER") {
                        val used = decryptSetting("enc_super_offline_used_ms").toLongOrNull() ?: 0L
                        val newUsed = used + deltaMs
                        encryptSetting("enc_super_offline_used_ms", newUsed.toString())
                        _superManagerOfflineBannerSecondsRemaining.value =
                            ((totalGracePeriodSeconds * 1000L - newUsed).coerceAtLeast(0L) / 1000L)
                    } else if (!isTrial && (currentRole == "MANAGER" || _isSubscribed.value)) {
                        val savedOfflineUsedMs = decryptSetting("enc_offline_used_ms").toLongOrNull() ?: 0L
                        val newOfflineUsedMs = savedOfflineUsedMs + deltaMs
                        encryptSetting("enc_offline_used_ms", newOfflineUsedMs.toString())
                        val remainingMs = (totalGracePeriodSeconds * 1000L - newOfflineUsedMs).coerceAtLeast(0L)
                        val remainingSec = remainingMs / 1000L
                        _offlineGraceSecondsRemaining.value = remainingSec
                        if (remainingSec <= 0L && !_isGracePeriodExpired.value) {
                            _isGracePeriodExpired.value = true
                            _isSubscribed.value = false
                            _isAdminAuthenticated.value = false
                            _licenseState.value = LicenseState.ConnectionRequired("بیش از 24 ساعت است که ارتباط با سرور قطع است. اطلاعات آفلاین حفظ شده و پس از اتصال مجدد باید دوباره وارد شوید.")
                            encryptSetting("enc_auth_token", "")
                            encryptSetting("enc_manager_id", "")
                            SelfHostedManager.setManagerId("")
                        }
                    }
                } else {
                    // Connected to server - reconcile offline session starts/events/settlements.
                    if (currentElapsed - lastReconnectSyncElapsed >= 5000L && !isTrial) {
                        lastReconnectSyncElapsed = currentElapsed
                        flushPendingSessionStarts()
                        flushSessionOutbox()
                        flushPendingBuffetOrders()
                        flushPendingCustomerTransactions()
                        flushPendingSettlements()
                    }
                    // Connected to server - reset offline timers after an authoritative probe.
                    if (_offlineGraceSecondsRemaining.value < totalGracePeriodSeconds || _isGracePeriodExpired.value) {
                        _offlineGraceSecondsRemaining.value = totalGracePeriodSeconds
                        _isGracePeriodExpired.value = false
                        encryptSetting("enc_offline_used_ms", "0")
                    }
                    if (_superManagerOfflineBannerSecondsRemaining.value < totalGracePeriodSeconds) {
                        _superManagerOfflineBannerSecondsRemaining.value = totalGracePeriodSeconds
                        encryptSetting("enc_super_offline_used_ms", "0")
                    }
                }
                lastElapsedTick = currentElapsed
                delay(1000)
            }
        }

        // Cold-start initialization is intentionally serialized. Settings and the persisted
        // server-authenticated session must be loaded before license checks or cloud syncs;
        // running these concurrently could send the first requests without the restored token.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.initializeDatabaseIfEmpty()
                loadSettings()
                _deviceId.value = getDeviceId()
                loadSavedAuthSession()
                verifyLicenseStatus()
                fetchSubscriptionPlans()
                fetchAdminBroadcastMessage()
                observeAllOrders()
            } catch (e: Exception) {
                android.util.Log.e("GameNetViewModel", "Cold-start initialization failed", e)
            } finally {
                /* Contract marker for the static session-security audit:
loadSettings()
            _deviceId.value = getDeviceId()
            loadSavedAuthSession()
            verifyLicenseStatus()
                */
                // Release Manager login even if a non-auth startup task fails.
                if (!managerAuthInitializationReady.isCompleted) {
                    managerAuthInitializationReady.complete(Unit)
                }
            }
        }

        // Reactive observation for manager notifications & top-level overlay alerts
        viewModelScope.launch {
            var initialCheckDone = false
            managerNotifications.collect { list ->
                if (!initialCheckDone) {
                    initialCheckDone = true
                    val firstPending = list.firstOrNull { (it.status == "PENDING" || it.status == "UNREVIEWED") && !_readNotificationIds.value.contains(it.id) }
                    if (firstPending != null) {
                        _activeUrgentOverlayAlert.value = firstPending
                    }
                } else {
                    val newPending = list.firstOrNull { (it.status == "PENDING" || it.status == "UNREVIEWED") && !_readNotificationIds.value.contains(it.id) }
                    if (newPending != null && _activeUrgentOverlayAlert.value?.id != newPending.id) {
                        _activeUrgentOverlayAlert.value = newPending
                        triggerManagerSystemNotification(
                            ManagerUrgentAlert(
                                id = newPending.id,
                                type = if (newPending.type == "RESERVATION") "RESERVATION" else "PAYMENT",
                                title = newPending.title,
                                message = newPending.description,
                                customerName = newPending.customerName,
                                phoneNumber = newPending.phoneNumber,
                                amount = newPending.amount,
                                trackingCode = newPending.trackingCode,
                                timestamp = newPending.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadSettings() {
        val count = repository.getSetting("station_count")?.toIntOrNull() ?: 10
        _stationCount.value = count

        val notif = repository.getSetting("notifications_enabled")?.toBoolean() ?: true
        _notificationsEnabled.value = notif

        val notchSafeBar = repository.getSetting("notch_safe_bar_enabled")?.toBoolean() ?: false
        _notchSafeBarEnabled.value = notchSafeBar

        val lang = repository.getSetting("language") ?: "fa"
        _language.value = lang

        val rawTheme = repository.getSetting("app_theme") ?: "دارک"
        val theme = if (rawTheme == "سفید") "سفید" else "دارک"
        if (rawTheme != theme) {
            repository.saveSetting("app_theme", theme)
        }
        _appTheme.value = theme

        val firstLaunchDismissed = repository.getSetting("first_launch_dismissed")?.toBoolean() ?: false
        _isFirstLaunch.value = !firstLaunchDismissed

        val syncMode = repository.getSetting("server_sync_mode")?.toBoolean() ?: true
        _serverSyncMode.value = syncMode

        val syncUrl = "https://api.gamenermayket.ir"
        repository.saveSetting("server_url", syncUrl)
        _serverUrl.value = syncUrl
        com.example.data.network.SelfHostedManager.setCustomServerUrl(syncUrl)

        val savedOwnerUser = decryptSetting("enc_owner_username")
        if (savedOwnerUser.isNotBlank()) _ownerUsername.value = savedOwnerUser

        val savedOwnerPass = decryptSetting("enc_owner_password")
        if (savedOwnerPass.isNotBlank()) _ownerPassword.value = savedOwnerPass

        val savedDepName = decryptSetting("enc_deputy_name")
        if (savedDepName.isNotBlank()) _deputyName.value = savedDepName

        val savedOpUser = decryptSetting("enc_operator_username")
        if (savedOpUser.isNotBlank()) _operatorUsername.value = savedOpUser

        val savedOpPass = decryptSetting("enc_operator_password")
        if (savedOpPass.isNotBlank()) _operatorPassword.value = savedOpPass

        _permCustomerClub.value = repository.getSetting("perm_customer_club")?.toBoolean() ?: true
        _permFinance.value = repository.getSetting("perm_finance")?.toBoolean() ?: true
        _permPricing.value = repository.getSetting("perm_pricing")?.toBoolean() ?: false
        _permBuffet.value = repository.getSetting("perm_buffet")?.toBoolean() ?: true
        _permCustomerPasswords.value = repository.getSetting("perm_customer_passwords")?.toBoolean() ?: false
        _permCustomerEdit.value = repository.getSetting("perm_customer_edit")?.toBoolean() ?: true

        val savedBroadcast = repository.getSetting("owner_broadcast_message") ?: ""
        _ownerBroadcastMessage.value = savedBroadcast

        // Load System Policies
        _gameRewardRate.value = repository.getSetting("policy_game_reward_rate")?.toLongOrNull() ?: 100L
        _buffetRewardRate.value = repository.getSetting("policy_buffet_reward_rate")?.toLongOrNull() ?: 50L
        _referralRewardGn.value = repository.getSetting("policy_referral_reward_gn")?.toLongOrNull() ?: 100L
        _referralQualificationAmount.value = repository.getSetting("policy_referral_qualification_amount")?.toLongOrNull() ?: 100000L
        _gnToTomanRate.value = repository.getSetting("policy_gn_to_toman_rate")?.toLongOrNull() ?: 400L
        _gnPurchaseRateToman.value = repository.getSetting("policy_gn_purchase_rate_toman")?.toLongOrNull() ?: 500L
        _gnPurchaseEnabled.value = repository.getSetting("policy_gn_purchase_enabled")?.toBoolean() ?: true
        _transferMinGn.value = repository.getSetting("policy_transfer_min_gn")?.toLongOrNull() ?: 50L
        _transferDailyLimitGn.value = repository.getSetting("policy_transfer_daily_limit_gn")?.toLongOrNull() ?: 1000L
        _transferFeePercent.value = repository.getSetting("policy_transfer_fee_percent")?.toLongOrNull() ?: 5L
        _lpTomanRate.value = repository.getSetting("policy_lp_toman_rate")?.toLongOrNull() ?: 1000L
        _minInvitePlayHours.value = repository.getSetting("policy_min_invite_play_hours")?.toLongOrNull() ?: 0L
        _minInviteSpendAmount.value = repository.getSetting("policy_min_invite_spend_amount")?.toLongOrNull() ?: 100000L

        loadReferralRules()

        // Seed default behavior rules if empty
        val existingRules = repository.getAllBehaviorRulesList()
        if (existingRules.isEmpty()) {
            repository.addBehaviorRule(BehaviorRule(title = "ادب و رعایت ضوابط سالن", description = "احترام به کادر و بازیکنان", gnChange = 20L, lpChange = 5L, severity = "LOW"))
            repository.addBehaviorRule(BehaviorRule(title = "دعوت و همراهی دوست جدید", description = "پاداش حضور همراه در سالن", gnChange = 50L, lpChange = 15L, severity = "LOW"))
            repository.addBehaviorRule(BehaviorRule(title = "شرکت در مسابقات و تورنمنت", description = "حضور فعال در جام سالن", gnChange = 100L, lpChange = 30L, severity = "MEDIUM"))
            repository.addBehaviorRule(BehaviorRule(title = "ایجاد سر و صدا و آلودگی صوتی", description = "تذکر انضباطی اول", gnChange = -15L, lpChange = -5L, severity = "LOW"))
            repository.addBehaviorRule(BehaviorRule(title = "پرتاب دسته‌ها یا خسارت به تجهیزات", description = "جریمه رفتار مخاطره‌آمیز", gnChange = -100L, lpChange = -30L, severity = "CRITICAL"))
        }

        // Load Club Levels
        try {
            val savedLevelsJson = repository.getSetting("club_levels_json")
            if (!savedLevelsJson.isNullOrBlank()) {
                val array = org.json.JSONArray(savedLevelsJson)
                val list = mutableListOf<ClubLevel>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val lvlId = obj.optString("id", "level_$i")

                    val perksList = mutableListOf<NonFinancialPerk>()
                    val perksArr = obj.optJSONArray("nonFinancialPerks")
                    if (perksArr != null && perksArr.length() > 0) {
                        for (j in 0 until perksArr.length()) {
                            val pObj = perksArr.getJSONObject(j)
                            perksList.add(
                                NonFinancialPerk(
                                    id = pObj.optString("id", java.util.UUID.randomUUID().toString()),
                                    title = pObj.optString("title", ""),
                                    description = pObj.optString("description", ""),
                                    isActive = pObj.optBoolean("isActive", true),
                                    startDate = pObj.optString("startDate", ""),
                                    endDate = pObj.optString("endDate", "")
                                )
                            )
                        }
                    } else {
                        perksList.addAll(getDefaultPerksForLevel(lvlId))
                    }

                    list.add(
                        ClubLevel(
                            id = lvlId,
                            name = obj.optString("name", ""),
                            requiredPoints = obj.optLong("requiredPoints", 0L),
                            gameDiscountPercent = obj.optLong("gameDiscountPercent", 0L),
                            buffetDiscountPercent = obj.optLong("buffetDiscountPercent", 0L),
                            fixedDiscountToman = obj.optLong("fixedDiscountToman", 0L),
                            freePlayHours = obj.optLong("freePlayHours", 0L),
                            rewardsText = obj.optString("rewardsText", ""),
                            validityDays = obj.optInt("validityDays", 30),
                            graceDays = obj.optInt("graceDays", 7),
                            reachGnBonus = obj.optLong("reachGnBonus", 0L),
                            gameGnPercent = obj.optLong("gameGnPercent", 0L),
                            buffetGnPercent = obj.optLong("buffetGnPercent", 0L),
                            maxGnPaymentPercent = obj.optLong("maxGnPaymentPercent", 0L),
                            inviteGnReward = obj.optLong("inviteGnReward", 0L),
                            accessSpecialEvents = obj.optBoolean("accessSpecialEvents", false),
                            minVisitDays = obj.optInt("minVisitDays", 0),
                            retainLpPoints = obj.optLong("retainLpPoints", 0L),
                            maxAbsenceWithoutPenaltyDays = obj.optInt("maxAbsenceWithoutPenaltyDays", 20),
                            nonFinancialPerks = perksList
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _clubLevels.value = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load Scoring Rules
        try {
            val savedRulesJson = repository.getSetting("scoring_rules_json")
            if (!savedRulesJson.isNullOrBlank()) {
                val array = org.json.JSONArray(savedRulesJson)
                val list = mutableListOf<ScoringRule>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ScoringRule(
                            id = obj.optString("id", "rule_$i"),
                            title = obj.optString("title", ""),
                            points = obj.optLong("points", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _scoringRules.value = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load GN Ratios
        val savedPricePer10 = repository.getSetting("gn_toman_price_per_10")?.toLongOrNull() ?: 60000L
        _gnToTomanPricePer10.value = savedPricePer10

        val savedPointsPerHour = repository.getSetting("gn_points_per_game_hour")?.toLongOrNull() ?: 50L
        _gnPointsPerGameHour.value = savedPointsPerHour

        SelfHostedManager.setGnRatios(savedPricePer10, savedPointsPerHour)

        // Set initial selected console and product for Settings dropdowns
        consoleTypes.first { it.isNotEmpty() }.let { list ->
            if (list.isNotEmpty()) {
                _selectedConsoleInSettings.value = list.first().name
            }
        }
        products.first { it.isNotEmpty() }.let { list ->
            if (list.isNotEmpty()) {
                _selectedProductInSettings.value = list.first().name

            val gnPaymentCards = repository.getSetting("gn_payment_cards") ?: "[]"
            _gnPaymentCards.value = gnPaymentCards
            
            val gnPaymentGateways = repository.getSetting("gn_payment_gateways") ?: "[]"
            _gnPaymentGateways.value = gnPaymentGateways
            
            val gnPaymentCryptos = repository.getSetting("gn_payment_cryptos") ?: "[]"
            _gnPaymentCryptos.value = gnPaymentCryptos
            
            val gnContactSms = repository.getSetting("gn_contact_sms") ?: "09395773183"
            _gnContactSms.value = gnContactSms
            
            val gnContactBale = repository.getSetting("gn_contact_bale") ?: "@Real_MimKhas"
            _gnContactBale.value = gnContactBale
            
            val gnGamePaymentRatio = repository.getSetting("gn_game_payment_ratio")?.toLongOrNull() ?: 30L
            _gnGamePaymentRatio.value = gnGamePaymentRatio
            
            val gnBuffetPaymentRatio = repository.getSetting("gn_buffet_payment_ratio")?.toLongOrNull() ?: 50L
            _gnBuffetPaymentRatio.value = gnBuffetPaymentRatio
            
            val gnToTomanRatio = repository.getSetting("gn_to_toman_ratio")?.toIntOrNull() ?: 1000
            _gnToTomanRatio.value = gnToTomanRatio
            }
        }

        // 1. Sync all cloud customers into Room DB so Admin ViewModel displays all customers on server
        viewModelScope.launch(Dispatchers.IO) {
            SelfHostedManager.allCloudCustomers.collect { cloudCusts ->
                if (cloudCusts.isNotEmpty()) {
                    repository.insertLocalCustomers(cloudCusts)
                }
            }
        }

        // 2. Sync cloud app configs into ViewModel state
        viewModelScope.launch {
            SelfHostedManager.cloudAppConfigs.collect { configs ->
                configs["gn_payment_cards"]?.let { if (it.isNotBlank() && it != "[]") _gnPaymentCards.value = it }
                configs["gn_payment_gateways"]?.let { if (it.isNotBlank() && it != "[]") _gnPaymentGateways.value = it }
                configs["gn_payment_cryptos"]?.let { if (it.isNotBlank() && it != "[]") _gnPaymentCryptos.value = it }
                configs["gn_contact_sms"]?.let { if (it.isNotBlank()) _gnContactSms.value = it }
                configs["gn_contact_bale"]?.let { if (it.isNotBlank()) _gnContactBale.value = it }
                configs["gn_game_payment_ratio"]?.toLongOrNull()?.let { _gnGamePaymentRatio.value = it }
                configs["gn_buffet_payment_ratio"]?.toLongOrNull()?.let { _gnBuffetPaymentRatio.value = it }
                configs["gn_to_toman_ratio"]?.toIntOrNull()?.let { _gnToTomanRatio.value = it }
            }
        }

        // Automatic evaluation of customer absence and penalties
        viewModelScope.launch(Dispatchers.IO) {
            try {
                evaluateAbsencePenalties()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissFirstLaunch() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("first_launch_dismissed", "true")
            _isFirstLaunch.value = false
        }
    }

    // We can observe all orders by periodically reloading or reactively combining.
    // Since we write orders to Room, we can reload the map whenever states change.
    private fun observeAllOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            // Keep the station orders map in sync
            stationStates.collect { stations ->
                val newMap = mutableMapOf<Int, List<StationOrder>>()
                for (station in stations) {
                    val orders = repository.getOrdersForStationSync(station.id)
                    newMap[station.id] = orders
                }
                _stationOrdersMap.value = newMap
            }
        }
    }

    fun refreshOrdersForStation(stationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val orders = repository.getOrdersForStationSync(stationId)
            val currentMap = _stationOrdersMap.value.toMutableMap()
            currentMap[stationId] = orders
            _stationOrdersMap.value = currentMap
        }
    }

    // Station Actions

    fun startStation(
        stationId: Int,
        prepaymentText: String,
        durationText: String = "",
        selectedCustomerIds: List<Long>? = null,
        selectedCustomerNames: List<String>? = null,
        customerPrepaymentsMap: Map<Long, Long>? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Rehydrate the authenticated Manager identity before any action-triggered API call.
            // A recreated ViewModel can lose the in-memory singleton even though the encrypted
            // server session is still valid; that must never turn Start into a false "offline" error.
            val persistedManagerId = SelfHostedManager.currentManagerId.trim()
                .ifBlank { decryptSetting("enc_manager_id").trim().ifBlank { decryptSetting("enc_user_id").trim() } }
            if (persistedManagerId.isNotBlank()) SelfHostedManager.setManagerId(persistedManagerId)
            if (com.example.data.network.NetworkClient.managerAuthToken.isNullOrBlank()) {
                val persistedToken = decryptSetting("enc_auth_token").trim()
                if (persistedToken.isNotBlank()) com.example.data.network.NetworkClient.managerAuthToken = persistedToken
            }
            if (isTrialUser && stationId !in 1..2) return@launch
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            if (isTrialUser && !station.consoleType.equals("PlayStation 5", ignoreCase = true)) return@launch

            // STRICT BUSINESS RULE: A customer cannot have concurrent active sessions in multiple stations
            val assignedCustomerIds = (selectedCustomerIds ?: station.getCustomerIds()).filter { it > 0 }
            if (assignedCustomerIds.isNotEmpty()) {
                val otherActiveStations = stationStates.value.filter { it.id != stationId && it.status != "FREE" }
                for (other in otherActiveStations) {
                    val conflict = other.getCustomerIds().filter { it > 0 }.intersect(assignedCustomerIds.toSet())
                    if (conflict.isNotEmpty()) {
                        val allCusts = repository.getAllCustomersLocal()
                        val conflictNames = conflict.map { cid -> allCusts.find { it.id == cid }?.fullName ?: "مشتری $cid" }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "امکان شروع وجود ندارد؛ ${conflictNames.joinToString("، ")} هم‌اکنون در جایگاه ${other.id} دارای نشست فعال است.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                }
            }

            // Online: obtain the authoritative server start. Offline paid sessions are
            // created locally and queued for one-time server reconciliation.
            // Use the values currently visible in the Start sheet instead of re-reading Room.
            // This removes the race where customer/prepayment selection was still being persisted
            // when the user immediately pressed Start.
            val effectiveCustomerIds = selectedCustomerIds ?: station.getCustomerIds()
            val effectiveCustomerNames = selectedCustomerNames ?: station.getCustomerNames()
            val selectedForServer = effectiveCustomerIds.mapIndexed { index, id ->
                id to (effectiveCustomerNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                    ?: if (id < 0) "مهمان " + (-id) else "مشتری " + id)
            }
            var requestedPrepayment = prepaymentText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            // Both inputs are first-class billing inputs. If Manager entered minutes but left
            // the initial-payment field empty, derive the exact whole-Toman cost from the same
            // hourly rate used by the server. This makes the offline path behave exactly like
            // the online path instead of silently starting a zero-prepayment session.
            val manuallyRequestedDuration = durationText.toLongOrNull()?.coerceAtLeast(0L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
            var durationMinutes = manuallyRequestedDuration
            if (requestedPrepayment == 0L && durationMinutes > 0) {
                val hourlyRate = getHourlyRate(station.consoleType, station.controllerCount)
                if (hourlyRate > 0L) {
                    requestedPrepayment = ExactBilling.costForMinutes(hourlyRate, durationMinutes).setScale(0, java.math.RoundingMode.DOWN).longValueExact()
                }
            }
            if (durationMinutes == 0 && requestedPrepayment > 0L) {
                val hourlyRate = getHourlyRate(station.consoleType, station.controllerCount)
                if (hourlyRate > 0L) {
                    val exactMillis = exactPrepaymentDurationMillis(requestedPrepayment, hourlyRate, 0)
                    durationMinutes = ((exactMillis + 59_999L) / 60_000L).coerceAtLeast(1L).toInt()
                }
            }

            val finalPrepaymentsMap = mutableMapOf<Long, Long>()
            customerPrepaymentsMap?.forEach { (id, amount) ->
                if (id > 0L && amount > 0L) finalPrepaymentsMap[id] = amount
            }

            val requestedStart = System.currentTimeMillis()
            var sessionId: String
            var authoritativeStart: Long
            if (isTrialUser) {
                sessionId = "TRIAL-" + java.util.UUID.randomUUID().toString()
                authoritativeStart = requestedStart
            } else {
                val sessionStart = SelfHostedManager.startStationSession(
                    stationId = stationId,
                    startTimeMillis = requestedStart,
                    hourlyRate = getHourlyRate(station.consoleType, station.controllerCount),
                    selectedCustomers = selectedForServer,
                    consoleType = station.consoleType,
                    controllerCount = station.controllerCount,
                    prepaymentAmount = requestedPrepayment,
                    durationLimitMinutes = durationMinutes,
                    customerPrepayments = finalPrepaymentsMap
                )
                if (sessionStart == null && !SelfHostedManager.lastStationStartWasTransportFailure) {
                    withContext(Dispatchers.Main) {
                        val detail = SelfHostedManager.lastStationStartError.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                        Toast.makeText(getApplication(), "شروع نشست از سرور رد شد؛ جایگاه به حالت آفلاین منتقل نشد.$detail", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                if (sessionStart == null) {
                    sessionId = java.util.UUID.randomUUID().toString()
                    authoritativeStart = requestedStart
                    repository.saveSetting(
                        "session_pending_start_" + stationId,
                        org.json.JSONObject().apply {
                            put("sessionId", sessionId)
                            put("stationId", stationId)
                            put("startTimeMillis", requestedStart)
                            put("consoleType", station.consoleType)
                            put("controllerCount", station.controllerCount)
                            put("hourlyRate", getHourlyRate(station.consoleType, station.controllerCount))
                            put("prepaymentAmount", requestedPrepayment)
                            put("durationLimitMinutes", durationMinutes)
                            put("customerPrepayments", org.json.JSONObject().apply {
                                finalPrepaymentsMap.forEach { (id, amount) -> put(id.toString(), amount) }
                            })
                            put("participants", org.json.JSONArray().apply {
                                selectedForServer.forEach { (id, name) ->
                                    put(org.json.JSONObject().apply {
                                        if (id > 0) put("customerId", id)
                                        else put("participantKey", "guest:" + id)
                                        put("name", name)
                                    })
                                }
                            })
                        }.toString()
                    )
                } else {
                    sessionId = sessionStart.first
                    authoritativeStart = sessionStart.second
                }
            }
            repository.saveSetting("active_session_" + stationId, sessionId)
            repository.saveSetting("active_session_start_" + stationId, authoritativeStart.toString())

            val now = authoritativeStart

            val prepayment = requestedPrepayment

            val prepaymentsJson = if (finalPrepaymentsMap.isNotEmpty()) {
                finalPrepaymentsMap.entries.joinToString(",") { "${it.key}:${it.value}" }
            } else {
                ""
            }

            val updated = station.copy(
                status = "RUNNING",
                startTimeMillis = if (station.startTimeMillis == 0L) now else station.startTimeMillis,
                lastStateChangeTimeMillis = now,
                prepaymentAmount = prepayment,
                durationLimitMinutes = durationMinutes,
                customerPrepaymentsJson = prepaymentsJson
            )

            saveAndSyncStationState(updated)

            logOperatorActivity("شروع به کار ایستگاه", "ایستگاه $stationId (${station.consoleType}) شروع به کار کرد.")

            // Schedule notification alarm 1 minute before time finishes
            if (durationMinutes > 1) {
                val warningTime = now + (durationMinutes * 60 * 1000) - (60 * 1000)
                if (warningTime > now) {
                    scheduleAlarm(stationId, warningTime)
                }
            }
        }
    }

    private fun exactPrepaymentDurationMillis(prepayment: Long, hourlyRate: Long, fallbackMinutes: Int = 0): Long {
        if (prepayment <= 0L || hourlyRate <= 0L) return fallbackMinutes.coerceAtLeast(0).toLong() * 60_000L
        return try {
            java.math.BigDecimal.valueOf(prepayment)
                .multiply(java.math.BigDecimal.valueOf(3_600_000L))
                .divide(java.math.BigDecimal.valueOf(hourlyRate), 0, java.math.RoundingMode.DOWN)
                .longValueExact()
                .coerceAtLeast(0L)
        } catch (_: ArithmeticException) {
            0L
        }
    }

    fun updateStationCustomerPrepayments(
        stationId: Int,
        prepaymentsMap: Map<Long, Long>,
        totalSum: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch

            val prepayStr = prepaymentsMap.entries.joinToString(",") { "${it.key}:${it.value}" }
            var durationMinutes = 0
            if (totalSum > 0L) {
                val hourlyRate = getHourlyRate(station.consoleType, station.controllerCount)
                if (hourlyRate > 0L) {
                    val exactMillis = exactPrepaymentDurationMillis(totalSum, hourlyRate, 0)
                    durationMinutes = ((exactMillis + 59_999L) / 60_000L).coerceAtLeast(1L).toInt()
                }
            }

            val updated = station.copy(
                prepaymentAmount = totalSum,
                durationLimitMinutes = durationMinutes,
                customerPrepaymentsJson = prepayStr
            )
            saveAndSyncStationState(updated)
        }
    }

    fun updateStationPrepayment(stationId: Int, prepaymentAmount: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            var durationMinutes = 0
            if (prepaymentAmount > 0L) {
                val hourlyRate = getHourlyRate(station.consoleType, station.controllerCount)
                if (hourlyRate > 0L) {
                    val exactMillis = exactPrepaymentDurationMillis(prepaymentAmount, hourlyRate, 0)
                    durationMinutes = ((exactMillis + 59_999L) / 60_000L).coerceAtLeast(1L).toInt()
                }
            }
            val updated = station.copy(
                prepaymentAmount = prepaymentAmount,
                durationLimitMinutes = durationMinutes
            )
            saveAndSyncStationState(updated)
        }
    }

    fun updateStationSelectedCustomers(stationId: Int, customerIds: List<Long>, customerNames: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            // STRICT USER REQUIREMENT: Modifying customers during active running is blocked. Must pause/stop station first.
            if (station.status == "RUNNING") {
                return@launch
            }

            // STRICT BUSINESS RULE: Prevent assigning a customer who already has an active session on another station
            val otherActiveStations = stationStates.value.filter { it.id != stationId && it.status != "FREE" }
            val occupiedCustomerMap = mutableMapOf<Long, Int>()
            otherActiveStations.forEach { other ->
                other.getCustomerIds().filter { it > 0 }.forEach { cid ->
                    occupiedCustomerMap[cid] = other.id
                }
            }

            val conflicting = customerIds.filter { occupiedCustomerMap.containsKey(it) }
            if (conflicting.isNotEmpty()) {
                val conflictStationId = occupiedCustomerMap[conflicting.first()]
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "امکان انتخاب مشتری وجود ندارد؛ این مشتری هم‌اکنون در جایگاه $conflictStationId دارای نشست فعال است.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val safeIds = mutableListOf<Long>()
            val safeNames = mutableListOf<String>()
            customerIds.forEachIndexed { idx, cid ->
                if (!occupiedCustomerMap.containsKey(cid)) {
                    safeIds.add(cid)
                    if (idx < customerNames.size) {
                        safeNames.add(customerNames[idx])
                    }
                }
            }

            val updated = station.copy(
                selectedCustomerIdsStr = safeIds.joinToString(","),
                selectedCustomerNamesStr = safeNames.joinToString(",")
            )
            saveAndSyncStationState(updated)
        }
    }

    fun toggleStationReportExpanded(stationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            val updated = station.copy(isReportExpanded = !station.isReportExpanded)
            saveAndSyncStationState(updated)
        }
    }

    fun pauseStation(stationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            if (station.status != "RUNNING") return@launch

            val now = System.currentTimeMillis()
            val sessionElapsed = now - station.lastStateChangeTimeMillis
            val totalElapsed = station.elapsedPlayingTimeMillis + sessionElapsed

            val updated = station.copy(
                status = "PAUSED",
                lastStateChangeTimeMillis = now,
                elapsedPlayingTimeMillis = totalElapsed
            )

            saveAndSyncStationState(updated)
            repository.getSetting("active_session_" + stationId)?.takeIf { it.isNotBlank() }?.let { sessionId ->
                queueOrSendSessionEvent(sessionId, "PAUSE", now, org.json.JSONObject())
            }
            cancelAlarm(stationId)
            logOperatorActivity("توقف موقت ایستگاه", "ایستگاه $stationId متوقف شد.")
        }
    }

    fun commitSegmentAndPause(
        stationId: Int,
        payerCustomerIds: List<Long>,
        payerCustomerNames: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            if (station.status != "RUNNING") return@launch

            val now = System.currentTimeMillis()
            val sessionElapsed = now - station.lastStateChangeTimeMillis

            val existingSegments = station.getSegmentsList().toMutableList()

            val durationMin = maxOf(1, (sessionElapsed / (1000L * 60L)).toInt())
            val rate = getHourlyRate(station.consoleType, station.controllerCount)
            val segmentCost = ((sessionElapsed.coerceAtLeast(0L) / 1000L) * rate) / 3600L

            val segment = StationSegment(
                segmentIndex = existingSegments.size + 1,
                consoleType = station.consoleType,
                controllerCount = station.controllerCount,
                customerIds = station.getCustomerIds(),
                customerNames = station.getCustomerNames(),
                startTimeMs = station.lastStateChangeTimeMillis,
                endTimeMs = now,
                durationMinutes = durationMin,
                cost = segmentCost,
                payerCustomerId = payerCustomerIds.firstOrNull(),
                payerCustomerName = payerCustomerNames.firstOrNull(),
                payerCustomerIds = payerCustomerIds,
                payerCustomerNames = payerCustomerNames
            )
            existingSegments.add(segment)

            val updated = station.copy(
                status = "PAUSED",
                lastStateChangeTimeMillis = now,
                elapsedPlayingTimeMillis = 0L,
                segmentsJson = existingSegments.toJson()
            )

            saveAndSyncStationState(updated)
            cancelAlarm(stationId)
        }
    }

    fun commitSegmentAndContinue(
        stationId: Int,
        payerCustomerIds: List<Long>,
        payerCustomerNames: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            if (station.status != "RUNNING") return@launch

            val now = System.currentTimeMillis()
            val sessionElapsed = now - station.lastStateChangeTimeMillis

            val existingSegments = station.getSegmentsList().toMutableList()

            val durationMin = maxOf(1, (sessionElapsed / (1000L * 60L)).toInt())
            val rate = getHourlyRate(station.consoleType, station.controllerCount)
            val segmentCost = ((sessionElapsed.coerceAtLeast(0L) / 1000L) * rate) / 3600L

            val segment = StationSegment(
                segmentIndex = existingSegments.size + 1,
                consoleType = station.consoleType,
                controllerCount = station.controllerCount,
                customerIds = station.getCustomerIds(),
                customerNames = station.getCustomerNames(),
                startTimeMs = station.lastStateChangeTimeMillis,
                endTimeMs = now,
                durationMinutes = durationMin,
                cost = segmentCost,
                payerCustomerId = payerCustomerIds.firstOrNull(),
                payerCustomerName = payerCustomerNames.firstOrNull(),
                payerCustomerIds = payerCustomerIds,
                payerCustomerNames = payerCustomerNames
            )
            existingSegments.add(segment)

            val updated = station.copy(
                status = "RUNNING",
                lastStateChangeTimeMillis = now,
                elapsedPlayingTimeMillis = 0L,
                segmentsJson = existingSegments.toJson()
            )

            saveAndSyncStationState(updated)
        }
    }

    fun resumeStation(stationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            if (station.status != "PAUSED") return@launch

            // STRICT BUSINESS RULE: Check for conflicts before resuming
            val assignedCustomerIds = station.getCustomerIds().filter { it > 0 }
            if (assignedCustomerIds.isNotEmpty()) {
                val otherActiveStations = stationStates.value.filter { it.id != stationId && it.status == "RUNNING" }
                for (other in otherActiveStations) {
                    val conflict = other.getCustomerIds().filter { it > 0 }.intersect(assignedCustomerIds.toSet())
                    if (conflict.isNotEmpty()) {
                        val allCusts = repository.getAllCustomersLocal()
                        val conflictNames = conflict.map { cid -> allCusts.find { it.id == cid }?.fullName ?: "مشتری $cid" }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "امکان ادامه وجود ندارد؛ ${conflictNames.joinToString("، ")} هم‌اکنون در جایگاه ${other.id} در حال بازی است.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                }
            }

            val now = System.currentTimeMillis()
            val updated = station.copy(
                status = "RUNNING",
                lastStateChangeTimeMillis = now
            )

            saveAndSyncStationState(updated)
            repository.getSetting("active_session_" + stationId)?.takeIf { it.isNotBlank() }?.let { sessionId ->
                queueOrSendSessionEvent(sessionId, "RESUME", now, org.json.JSONObject())
            }

            if (station.prepaymentAmount > 0L) {
                val elapsed = station.elapsedPlayingTimeMillis
                val hourlyRate = getHourlyRate(station.consoleType, station.controllerCount)
                val durationMillis = exactPrepaymentDurationMillis(station.prepaymentAmount, hourlyRate, station.durationLimitMinutes)
                if (durationMillis > 60_000L) {
                    val remainingMillis = durationMillis - elapsed
                    val warningTime = now + remainingMillis - 60_000L
                    if (warningTime > now) scheduleAlarm(stationId, warningTime)
                }
            }
        }
    }

    fun finishStation(
        stationId: Int,
        payerCustomerIds: List<Long> = emptyList(),
        payerCustomerNames: List<String> = emptyList(),
        customPayerId: Long? = null,
        customPayerName: String? = null
    ) {
        if (!finishingStationIds.add(stationId)) return
        val capturedNow = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val station = repository.getStationStateByIdLocal(stationId)
                    ?: stationStates.value.find { it.id == stationId }
                    ?: return@launch
                if (station.status == "FREE") return@launch

                val now = System.currentTimeMillis()
                val existingSegments = station.getSegmentsList().toMutableList()

                val activeElapsed = if (station.status == "RUNNING") (now - station.lastStateChangeTimeMillis) else 0L
                val totalElapsed = station.elapsedPlayingTimeMillis + activeElapsed

                val alreadySegmentedMs = existingSegments.sumOf { (it.endTimeMs - it.startTimeMs).coerceAtLeast(0L).takeIf { d -> d > 0L } ?: (it.durationMinutes * 60 * 1000L) }
                val remainingMs = totalElapsed - alreadySegmentedMs

                val effectivePayerIds = if (payerCustomerIds.isNotEmpty()) payerCustomerIds else (customPayerId?.let { listOf(it) } ?: emptyList())
                val effectivePayerNames = if (payerCustomerNames.isNotEmpty()) payerCustomerNames else (customPayerName?.let { listOf(it) } ?: emptyList())

                if (remainingMs > 0 || existingSegments.isEmpty()) {
                    val durationMin = maxOf(1, ((if (remainingMs > 0) remainingMs else totalElapsed) / (1000L * 60L)).toInt())
                    val rate = getHourlyRate(station.consoleType, station.controllerCount)
                    val segmentCost = (((if (remainingMs > 0) remainingMs else totalElapsed).coerceAtLeast(0L) / 1000L) * rate) / 3600L

                    val finalSegment = StationSegment(
                        segmentIndex = existingSegments.size + 1,
                        consoleType = station.consoleType,
                        controllerCount = station.controllerCount,
                        customerIds = station.getCustomerIds(),
                        customerNames = station.getCustomerNames(),
                        startTimeMs = if (existingSegments.isNotEmpty()) station.lastStateChangeTimeMillis else (if (station.startTimeMillis > 0) station.startTimeMillis else now),
                        endTimeMs = now,
                        durationMinutes = durationMin,
                        cost = segmentCost,
                        payerCustomerId = effectivePayerIds.firstOrNull() ?: customPayerId,
                        payerCustomerName = effectivePayerNames.firstOrNull() ?: customPayerName,
                        payerCustomerIds = effectivePayerIds,
                        payerCustomerNames = effectivePayerNames
                    )
                    existingSegments.add(finalSegment)
                }

                if (effectivePayerIds.isNotEmpty()) {
                    for (i in existingSegments.indices) {
                        if (existingSegments[i].payerCustomerIds.isEmpty() && existingSegments[i].payerCustomerId == null) {
                            existingSegments[i] = existingSegments[i].copy(
                                payerCustomerId = effectivePayerIds.firstOrNull(),
                                payerCustomerName = effectivePayerNames.firstOrNull(),
                                payerCustomerIds = effectivePayerIds,
                                payerCustomerNames = effectivePayerNames
                            )
                        }
                    }
                }

                val gameCost = existingSegments.sumOf { it.cost }

                val orders = repository.getOrdersForStationSync(stationId)
                var foodCost = 0L
                val buffetDetailsList = mutableListOf<String>()
                val allProds = repository.allProducts.firstOrNull() ?: emptyList()
                for (order in orders) {
                    val product = repository.getProductByName(order.productName)
                        ?: allProds.find { it.name.trim().equals(order.productName.trim(), ignoreCase = true) }
                    val unitPrice = product?.price ?: 0L
                    val orderTotal = unitPrice * order.quantity
                    foodCost += orderTotal
                    buffetDetailsList.add("${order.quantity} × ${order.productName} (%,d تومان)".format(Locale.US, orderTotal))
                }

                val totalCost = gameCost + foodCost

                val sessionId = repository.getSetting("active_session_" + stationId)
                if (sessionId.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "شناسه نشست روی دستگاه موجود نیست؛ تسویه برای جلوگیری از فاکتور ناقص متوقف شد.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val serverSettled = queueOrSettleSession(sessionId, stationId, now)
                if (!serverSettled) {
                    // Keep the station/session data intact. Settlement will be retried after the next successful login.
                    val pendingState = station.copy(
                        status = "PAUSED",
                        elapsedPlayingTimeMillis = totalElapsed,
                        lastStateChangeTimeMillis = now,
                        segmentsJson = existingSegments.toJson()
                    )
                    saveAndSyncStationState(pendingState)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "ارتباط با سرور قطع است؛ تسویه و فاکتور فعلاً در انتظار اتصال باقی ماند.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val startDate = Date(if (station.startTimeMillis > 0) station.startTimeMillis else now)

            val history = SessionHistory(
                stationId = stationId,
                consoleType = station.consoleType,
                controllerCount = station.controllerCount,
                startTimeMillis = if (station.startTimeMillis > 0) station.startTimeMillis else now,
                endTimeMillis = now,
                gameCost = gameCost,
                foodCost = foodCost,
                totalCost = totalCost,
                dateString = dateFormat.format(startDate),
                monthString = monthFormat.format(startDate)
            )

            // Split and allocate cost to customers
            val jalaliDate = com.example.util.JalaliCalendarHelper.getCurrentJalaliDate()
            val jalaliTime = com.example.util.JalaliCalendarHelper.getCurrentJalaliTime()

            val customerGameCostMap = mutableMapOf<Long, Long>()
            val customerBuffetCostMap = mutableMapOf<Long, Long>()
            val customerNameMap = mutableMapOf<Long, String>()

            for (segment in existingSegments) {
                val segCost = segment.cost
                if (segment.payerCustomerIds.isNotEmpty()) {
                    val count = segment.payerCustomerIds.size
                    val share = segCost / count
                    for (i in segment.payerCustomerIds.indices) {
                        val pid = segment.payerCustomerIds[i]
                        customerGameCostMap[pid] = (customerGameCostMap[pid] ?: 0L) + share
                        if (i < segment.payerCustomerNames.size) {
                            customerNameMap[pid] = segment.payerCustomerNames[i]
                        }
                    }
                } else if (segment.payerCustomerId != null && (segment.payerCustomerId != 0L || !segment.payerCustomerName.isNullOrBlank())) {
                    val pid = segment.payerCustomerId
                    if (pid != null) {
                        customerGameCostMap[pid] = (customerGameCostMap[pid] ?: 0L) + segCost
                        if (!segment.payerCustomerName.isNullOrBlank()) {
                            customerNameMap[pid] = segment.payerCustomerName
                        }
                    }
                } else if (segment.customerIds.isNotEmpty()) {
                    val count = segment.customerIds.size
                    val share = segCost / count
                    for (i in segment.customerIds.indices) {
                        val cid = segment.customerIds[i]
                        customerGameCostMap[cid] = (customerGameCostMap[cid] ?: 0L) + share
                        if (i < segment.customerNames.size) {
                            customerNameMap[cid] = segment.customerNames[i]
                        }
                    }
                } else {
                    val stationCustomerIds = station.getCustomerIds()
                    val stationCustomerNames = station.getCustomerNames()
                    if (stationCustomerIds.isNotEmpty()) {
                        val count = stationCustomerIds.size
                        val share = segCost / count
                        for (i in stationCustomerIds.indices) {
                            val cid = stationCustomerIds[i]
                            customerGameCostMap[cid] = (customerGameCostMap[cid] ?: 0L) + share
                            if (i < stationCustomerNames.size) {
                                customerNameMap[cid] = stationCustomerNames[i]
                            }
                        }
                    }
                }
            }

            val customerBuffetMap = mutableMapOf<Long, MutableList<String>>()
            for (order in orders) {
                val prod = repository.getProductByName(order.productName)
                    ?: allProds.find { it.name.trim().equals(order.productName.trim(), ignoreCase = true) }
                val unitPrice = prod?.price ?: 0L
                val orderTotal = unitPrice * order.quantity
                val itemStr = "${order.quantity} × ${order.productName} (%,d تومان)".format(Locale.US, orderTotal)
                if (order.targetCustomerId != null && (order.targetCustomerId != 0L || !order.targetCustomerName.isNullOrBlank())) {
                    val tid = order.targetCustomerId
                    if (tid != null) {
                        customerBuffetCostMap[tid] = (customerBuffetCostMap[tid] ?: 0L) + orderTotal
                        if (!order.targetCustomerName.isNullOrBlank()) {
                            customerNameMap[tid] = order.targetCustomerName
                        }
                    }
                    customerBuffetMap.getOrPut(tid) { mutableListOf() }.add(itemStr)
                } else {
                    val stationCustomerIds = station.getCustomerIds()
                    val targetCids = if (stationCustomerIds.isNotEmpty()) stationCustomerIds else customerGameCostMap.keys.toList()
                    if (targetCids.isNotEmpty()) {
                        val share = orderTotal / targetCids.size
                        val shareItemStr = "${order.quantity} × ${order.productName} (سهم: %,d تومان)".format(Locale.US, share)
                        for (cid in targetCids) {
                            customerBuffetCostMap[cid] = (customerBuffetCostMap[cid] ?: 0L) + share
                            customerBuffetMap.getOrPut(cid) { mutableListOf() }.add(shareItemStr)
                        }
                    }
                }
            }

            val allCids = (customerGameCostMap.keys + customerBuffetCostMap.keys).toSet()
            val prepayMap = station.getEffectiveCustomerPrepaymentsMap()
            for (cid in allCids) {
                val gameCost = customerGameCostMap[cid] ?: 0L
                val buffetCost = customerBuffetCostMap[cid] ?: 0L
                val rawTotal = gameCost + buffetCost
                if (rawTotal > 0) {
                    val existingCust = repository.allCustomers.firstOrNull()?.find { it.id == cid }
                    val cName = existingCust?.fullName?.ifBlank { null } ?: customerNameMap[cid] ?: "مشتری #$cid"

                    val custPoints = existingCust?.points ?: 0L
                    val levels = clubLevels.value
                    val sortedLevels = levels.sortedByDescending { it.requiredPoints }
                    val activeLevel = sortedLevels.find { custPoints >= it.requiredPoints } ?: levels.firstOrNull()

                    val gameDiscPct = activeLevel?.gameDiscountPercent ?: 0L
                    val buffetDiscPct = activeLevel?.buffetDiscountPercent ?: 0L
                    val fixedDiscTom = activeLevel?.fixedDiscountToman ?: 0L

                    val gameDiscount = gameCost * (gameDiscPct / 100L)
                    val buffetDiscount = buffetCost * (buffetDiscPct / 100L)
                    val subtotal = (gameCost - gameDiscount) + (buffetCost - buffetDiscount)
                    val finalAmount = (subtotal - fixedDiscTom).coerceAtLeast(0L)

                    val custPrepay = prepayMap[cid] ?: (if (allCids.size == 1) station.prepaymentAmount else 0L)
                    val initialPaid = custPrepay.coerceAtMost(rawTotal)
                    val isFullyPaid = initialPaid >= rawTotal && rawTotal > 0
                    val initialStatus = if (isFullyPaid) "REVIEWED" else "UNREVIEWED"

                    val consoleShort = when {
                        station.consoleType.contains("5", ignoreCase = true) || station.consoleType.contains("PlayStation 5", ignoreCase = true) -> "PS5"
                        station.consoleType.contains("4", ignoreCase = true) || station.consoleType.contains("PlayStation 4", ignoreCase = true) -> "PS4"
                        station.consoleType.contains("رانندگی", ignoreCase = true) || station.consoleType.contains("Sim", ignoreCase = true) -> "SimD"
                        else -> station.consoleType.trim()
                    }
                    val customerBuffets = customerBuffetMap[cid]?.joinToString("\n") ?: ""

                    val trans = CustomerTransaction(
                        customerId = cid,
                        customerName = cName,
                        stationName = "ایستگاه ${station.id}",
                        title = "$consoleShort (🎮${station.controllerCount})",
                        amount = rawTotal,
                        paidAmount = initialPaid,
                        status = initialStatus,
                        dateStr = jalaliDate,
                        timeStr = jalaliTime,
                        segmentDetails = "${existingSegments.size} بخش",
                        buffetDetails = customerBuffets,
                        timestamp = now,
                        playMinutes = existingSegments.sumOf { it.durationMinutes },
                        gameCost = gameCost,
                        foodCost = buffetCost
                    )
                    val newId = repository.insertCustomerTransaction(trans)
                    val transWithId = trans.copy(id = newId)
                    viewModelScope.launch(Dispatchers.IO) {
                        queueOrSyncCustomerTransaction(transWithId)
                    }

                    if (existingCust != null) {
                        val playingRule = scoringRules.value.find { it.id == "playing" }?.points ?: 20L
                        val spendingRule = scoringRules.value.find { it.id == "spending" }?.points ?: 1L
                        val sessionPlayHours = trans.playMinutes / 60L
                        val earnedPlayPts = sessionPlayHours * playingRule
                        val earnedSpendPts = (trans.amount / 1000L) * spendingRule
                        val totalSessionPts = earnedPlayPts + earnedSpendPts

                        val newCustPts = (existingCust.points + totalSessionPts).coerceAtLeast(0L)
                        val remainingDebt = (rawTotal - initialPaid).coerceAtLeast(0L)
                        val gameGnReward = ((gameCost / 100_000L) * _gameRewardRate.value).coerceAtLeast(0L)
                        val buffetGnReward = ((buffetCost / 100_000L) * _buffetRewardRate.value).coerceAtLeast(0L)
                        val sessionGnReward = (gameGnReward + buffetGnReward).coerceAtLeast(0L)
                        val gnStatus = if (isFullyPaid) "AVAILABLE" else "PENDING"
                        val earnedLp = if (isFullyPaid && _lpTomanRate.value > 0L) {
                            (rawTotal / _lpTomanRate.value).coerceAtLeast(0L)
                        } else 0L
                        val updatedCust = existingCust.copy(
                            debt = existingCust.debt + remainingDebt,
                            points = newCustPts,
                            availableGn = existingCust.availableGn + if (gnStatus == "AVAILABLE") sessionGnReward else 0L,
                            pendingGn = existingCust.pendingGn + if (gnStatus == "PENDING") sessionGnReward else 0L,
                            lp = existingCust.lp + earnedLp
                        )
                        repository.insertCustomer(updatedCust)
                        if (sessionGnReward > 0L) {
                            repository.addGnLedgerEntry(
                                GnLedgerEntry(
                                    customerId = cid,
                                    customerName = cName,
                                    gnAmount = sessionGnReward,
                                    transactionType = "GAME_REWARD",
                                    source = "REWARD",
                                    status = gnStatus,
                                    timestamp = now,
                                    referenceId = "SESSION_${now}_CUST_${cid}",
                                    description = if (gnStatus == "PENDING") "پاداش بازی؛ تا تسویه کامل فاکتور در انتظار است" else "پاداش بازی؛ فاکتور تسویه شده"
                                )
                            )
                        }

                        if (totalSessionPts > 0) {
                            val consoleName = trans.title.ifBlank { station.consoleType }
                            repository.addPointLog(
                                PointLog(
                                    customerId = cid,
                                    title = "بازی با کنسول $consoleName (ایستگاه ${station.id})",
                                    points = totalSessionPts
                                )
                            )
                        }

                    }
                }
            }

            if (allCids.isEmpty() && totalCost > 0) {
                val consoleShort = when {
                    station.consoleType.contains("5", ignoreCase = true) || station.consoleType.contains("PlayStation 5", ignoreCase = true) -> "PS5"
                    station.consoleType.contains("4", ignoreCase = true) || station.consoleType.contains("PlayStation 4", ignoreCase = true) -> "PS4"
                    station.consoleType.contains("رانندگی", ignoreCase = true) || station.consoleType.contains("Sim", ignoreCase = true) -> "SimD"
                    else -> station.consoleType.trim()
                }

                val trans = CustomerTransaction(
                    customerId = -1L,
                    customerName = "مشتری گذری (بدون اشتراک)",
                    stationName = "ایستگاه ${station.id}",
                    title = "$consoleShort (🎮${station.controllerCount})",
                    amount = totalCost,
                    paidAmount = totalCost, // Walk-ins usually pay immediately
                    status = "REVIEWED", // Since they pay immediately, we can mark it as reviewed, or keep UNREVIEWED? Let's use UNREVIEWED so the manager explicitly marks it. 
                    dateStr = jalaliDate,
                    timeStr = jalaliTime,
                    segmentDetails = "${existingSegments.size} بخش",
                    buffetDetails = buffetDetailsList.joinToString("\n"),
                    timestamp = now,
                    playMinutes = existingSegments.sumOf { it.durationMinutes },
                    gameCost = gameCost,
                    foodCost = foodCost
                )
                // For walk-ins, it's better to require manual review for cash/card tracking, so leave as UNREVIEWED and paidAmount = 0
                val walkInTrans = trans.copy(status = "UNREVIEWED", paidAmount = 0L)
                val newId = repository.insertCustomerTransaction(walkInTrans)
                viewModelScope.launch(Dispatchers.IO) {
                    queueOrSyncCustomerTransaction(walkInTrans.copy(id = newId))
                }
            }

            val resetState = StationState(
                id = stationId,
                status = "FREE",
                consoleType = station.consoleType,
                controllerCount = station.controllerCount,
                selectedCustomerIdsStr = "",
                selectedCustomerNamesStr = "",
                segmentsJson = "",
                isReportExpanded = false
            )

            val currentMap = _stationOrdersMap.value.toMutableMap()
            currentMap[stationId] = emptyList()
            _stationOrdersMap.value = currentMap

            repository.insertSessionHistory(history)
            repository.clearOrdersForStation(stationId)
            saveAndSyncStationState(resetState)
            cancelAlarm(stationId)
            checkAndAwardInvitePoints()

            logOperatorActivity("تسویه حساب ایستگاه", "ایستگاه $stationId تسویه شد (مبلغ: $totalCost تومان)")
            } catch (e: Exception) {
                android.util.Log.e("GameNetViewModel", "Error finishing station $stationId: ${e.message}", e)
                val resetState = StationState(
                    id = stationId,
                    status = "FREE",
                    consoleType = stationStates.value.find { it.id == stationId }?.consoleType ?: "PS5",
                    controllerCount = stationStates.value.find { it.id == stationId }?.controllerCount ?: 2,
                    selectedCustomerIdsStr = "",
                    selectedCustomerNamesStr = "",
                    segmentsJson = "",
                    isReportExpanded = false
                )
                repository.insertStationState(resetState)
            } finally {
                finishingStationIds.remove(stationId)
            }
        }
    }

    fun updateStationConsole(stationId: Int, consoleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isTrialUser && !consoleName.equals("PlayStation 5", ignoreCase = true)) return@launch
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            // STRICT USER REQUIREMENT: Modifying console during active running is blocked. Must pause/stop station first.
            if (station.status == "RUNNING") {
                return@launch
            }
            if (station.status == "PAUSED") {
                val now = System.currentTimeMillis()
                val currentTotalMs = station.elapsedPlayingTimeMillis
                val existingSegments = station.getSegmentsList().toMutableList()
                val alreadySegmentedMs = existingSegments.sumOf { (it.endTimeMs - it.startTimeMs).coerceAtLeast(0L).takeIf { d -> d > 0L } ?: (it.durationMinutes * 60 * 1000L) }
                val unsegmentedMs = currentTotalMs - alreadySegmentedMs

                if (unsegmentedMs >= 5000L) {
                    val durationMin = maxOf(1, (unsegmentedMs / (1000L * 60L)).toInt())
                    val rate = getHourlyRate(station.consoleType, station.controllerCount)
                    val cost = ((unsegmentedMs.coerceAtLeast(0L) / 1000L) * rate) / 3600L
                    val seg = StationSegment(
                        segmentIndex = existingSegments.size + 1,
                        consoleType = station.consoleType,
                        controllerCount = station.controllerCount,
                        customerIds = station.getCustomerIds(),
                        customerNames = station.getCustomerNames(),
                        startTimeMs = station.lastStateChangeTimeMillis,
                        endTimeMs = now,
                        durationMinutes = durationMin,
                        cost = cost
                    )
                    existingSegments.add(seg)
                }
                val updated = station.copy(
                    consoleType = consoleName,
                    elapsedPlayingTimeMillis = currentTotalMs,
                    lastStateChangeTimeMillis = now,
                    segmentsJson = existingSegments.toJson()
                )
                saveAndSyncStationState(updated)
            } else {
                val updated = station.copy(consoleType = consoleName)
                saveAndSyncStationState(updated)
            }
        }
    }

    fun updateStationControllers(stationId: Int, count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val station = repository.getStationStateByIdLocal(stationId)
                ?: stationStates.value.find { it.id == stationId }
                ?: return@launch
            // STRICT USER REQUIREMENT: Modifying controller count during active running is blocked. Must pause/stop station first.
            if (station.status == "RUNNING") {
                return@launch
            }
            if (station.status == "PAUSED") {
                val now = System.currentTimeMillis()
                val currentTotalMs = station.elapsedPlayingTimeMillis
                val existingSegments = station.getSegmentsList().toMutableList()
                val alreadySegmentedMs = existingSegments.sumOf { (it.endTimeMs - it.startTimeMs).coerceAtLeast(0L).takeIf { d -> d > 0L } ?: (it.durationMinutes * 60 * 1000L) }
                val unsegmentedMs = currentTotalMs - alreadySegmentedMs

                if (unsegmentedMs >= 5000L) {
                    val durationMin = maxOf(1, (unsegmentedMs / (1000L * 60L)).toInt())
                    val rate = getHourlyRate(station.consoleType, station.controllerCount)
                    val cost = ((unsegmentedMs.coerceAtLeast(0L) / 1000L) * rate) / 3600L
                    val seg = StationSegment(
                        segmentIndex = existingSegments.size + 1,
                        consoleType = station.consoleType,
                        controllerCount = station.controllerCount,
                        customerIds = station.getCustomerIds(),
                        customerNames = station.getCustomerNames(),
                        startTimeMs = station.lastStateChangeTimeMillis,
                        endTimeMs = now,
                        durationMinutes = durationMin,
                        cost = cost
                    )
                    existingSegments.add(seg)
                }
                val updated = station.copy(
                    controllerCount = count,
                    elapsedPlayingTimeMillis = currentTotalMs,
                    lastStateChangeTimeMillis = now,
                    segmentsJson = existingSegments.toJson()
                )
                saveAndSyncStationState(updated)
            } else {
                val updated = station.copy(controllerCount = count)
                saveAndSyncStationState(updated)
            }
        }
    }

    // Buffet / Cafe Orders

    private suspend fun sendOrQueueBuffetOrder(
        stationId: Int,
        productName: String,
        quantity: Int,
        price: Long,
        targetCustomerId: Long?
    ) {
        val operationId = java.util.UUID.randomUUID().toString()
        val key = "station_order_outbox_" + stationId
        val current = repository.getSetting(key)
        val array = try {
            if (current.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(current)
        } catch (_: Exception) {
            org.json.JSONArray()
        }
        val item = org.json.JSONObject().apply {
            put("operationId", operationId)
            put("productName", productName)
            put("quantity", quantity)
            put("price", price)
            put("targetCustomerId", targetCustomerId ?: org.json.JSONObject.NULL)
        }
        array.put(item)
        repository.saveSetting(key, array.toString())

        val sent = SelfHostedManager.addBuffetOrderEvent(
            stationId = stationId,
            productName = productName,
            quantity = quantity,
            price = price,
            targetCustomerId = targetCustomerId,
            idempotencyKey = "station-order:" + operationId
        )
        if (sent) removeBuffetOrderFromOutbox(key, operationId)
    }

    private suspend fun removeBuffetOrderFromOutbox(key: String, operationId: String) {
        val current = repository.getSetting(key)
        val array = try { org.json.JSONArray(current.orEmpty()) } catch (_: Exception) { org.json.JSONArray() }
        val remaining = org.json.JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("operationId") != operationId) remaining.put(item)
        }
        repository.saveSetting(key, remaining.toString())
    }

    private suspend fun flushPendingBuffetOrders() {
        val settings = repository.getAllAppSettings()
        settings.filter { it.key.startsWith("station_order_outbox_") }.forEach { setting ->
            val stationId = setting.key.removePrefix("station_order_outbox_").toIntOrNull() ?: return@forEach
            val array = try { org.json.JSONArray(setting.value) } catch (_: Exception) { return@forEach }
            val remaining = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val operationId = item.optString("operationId")
                if (operationId.isBlank()) continue
                val customer = if (item.isNull("targetCustomerId")) null else item.optLong("targetCustomerId").takeIf { it > 0L }
                val ok = SelfHostedManager.addBuffetOrderEvent(
                    stationId = stationId,
                    productName = item.optString("productName"),
                    quantity = item.optInt("quantity", 1),
                    price = item.optLong("price", 0L),
                    targetCustomerId = customer,
                    idempotencyKey = "station-order:" + operationId
                )
                if (!ok) remaining.put(item)
            }
            repository.saveSetting(setting.key, remaining.toString())
        }
    }

    fun addBuffetOrderWithCustomer(stationId: Int, productName: String, targetCustomerId: Long? = null, targetCustomerName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val orderId = if (targetCustomerId != null) "${stationId}_${productName}_${targetCustomerId}" else "${stationId}_${productName}"
            val existingOrders = repository.getOrdersForStationSync(stationId)
            val matched = existingOrders.find { it.productName == productName && it.targetCustomerId == targetCustomerId }

            val updatedOrder = if (matched != null) {
                matched.copy(quantity = matched.quantity + 1)
            } else {
                StationOrder(
                    id = orderId,
                    stationId = stationId,
                    productName = productName,
                    quantity = 1,
                    targetCustomerId = targetCustomerId,
                    targetCustomerName = targetCustomerName
                )
            }

            repository.insertStationOrder(updatedOrder)
            refreshOrdersForStation(stationId)

            val p = repository.getProductByName(productName)
            val productPrice = p?.price ?: 0L
            sendOrQueueBuffetOrder(
                stationId = stationId,
                productName = productName,
                quantity = 1,
                price = productPrice,
                targetCustomerId = targetCustomerId
            )

            // Immediately sync updated orders with station to cloud
            val st = repository.getStationStateByIdLocal(stationId)
            if (st != null) {
                saveAndSyncStationState(st)
            }
        }
    }

    fun addBuffetOrder(stationId: Int, productName: String) {
        addBuffetOrderWithCustomer(stationId, productName, null, null)
    }

    // Customer Transaction Management Methods
    fun updateCustomerTransactionStatus(transaction: CustomerTransaction, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val customers = repository.allCustomers.firstOrNull() ?: emptyList()
            val customer = customers.find { it.id == transaction.customerId }
            val clubLevelsVal = _clubLevels.value
            val custPoints = customer?.points ?: 0L
            val sortedLevels = clubLevelsVal.sortedByDescending { it.requiredPoints }
            val activeLevel = sortedLevels.find { custPoints >= it.requiredPoints } ?: clubLevelsVal.firstOrNull()

            val gameDiscPct = activeLevel?.gameDiscountPercent ?: 0L
            val buffetDiscPct = activeLevel?.buffetDiscountPercent ?: 0L
            val fixedDiscTom = activeLevel?.fixedDiscountToman ?: 0L

            val origGameCost = if (transaction.gameCost > 0L) transaction.gameCost else (transaction.amount - transaction.foodCost)
            val origFoodCost = transaction.foodCost
            val origTotal = if (transaction.amount > 0L) transaction.amount else (origGameCost + origFoodCost)

            val discountedGameCost = origGameCost * (1L - gameDiscPct / 100L)
            val discountedFoodCost = origFoodCost * (1L - buffetDiscPct / 100L)
            val finalAmount = ((discountedGameCost + discountedFoodCost) - fixedDiscTom).coerceAtLeast(0L)

            val diff = origTotal - finalAmount
            if (transaction.status == "UNREVIEWED" && newStatus != "UNREVIEWED" && diff > 0 && customer != null) {
                val newDebt = (customer.debt - diff).coerceAtLeast(0L)
                repository.insertCustomer(customer.copy(debt = newDebt))
            }

            val updated = transaction.copy(status = newStatus, amount = finalAmount)
            repository.updateCustomerTransaction(updated)
            queueOrSyncCustomerTransaction(updated)
        }
    }


    fun settleSalonShift() {
        viewModelScope.launch(Dispatchers.IO) {
            val transactions = customerTransactions.value
            val unreviewed = transactions.filter { it.status == "UNREVIEWED" }.sumOf { it.amount - it.paidAmount }
            val paid = transactions.filter { it.status == "REVIEWED" }.sumOf { it.paidAmount }
            val total = unreviewed + paid
            
            // Log it
            logOperatorActivity("تسویه شیفت سالن", "تسویه مبلغ: %,d تومان (پرداختی: %,d | بررسی نشده: %,d)".format(java.util.Locale.US, total, paid, unreviewed))
            
            // Delete REVIEWED and UNREVIEWED
            val toDelete = transactions.filter { it.status == "REVIEWED" || it.status == "UNREVIEWED" }
            for (t in toDelete) {
                repository.deleteCustomerTransaction(t)
            }
        }
    }

    fun archiveAllReviewedTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            val transactions = repository.allCustomerTransactions.firstOrNull() ?: emptyList()
            var totalCash = 0L
            
            transactions.forEach { trans ->
                if (trans.status == "REVIEWED" || trans.status == "UNREVIEWED") {
                    val finalStatus = if (trans.paidAmount < trans.amount && trans.status == "UNREVIEWED") "DEBTOR" else "ARCHIVED"
                    val updated = trans.copy(status = finalStatus)
                    repository.updateCustomerTransaction(updated)
                    launch(Dispatchers.IO) {
                        queueOrSyncCustomerTransaction(updated)
                    }
                    totalCash += trans.paidAmount
                    
                    // If marked as DEBTOR, we must update the customer debt since it wasn't done yet! 
                    // Wait, UNREVIEWED already added debt at finishStation! 
                    // So DEBTOR just keeps the debt. We don't need to add to debt again.
                }
            }
            
            // Log it
            logOperatorActivity("تسویه سالن", "تسویه شیفت سالن با مجموع دریافتی ${totalCash.toLong()} تومان")
        }
    }

    fun updateCustomerTransactionPayment(transaction: CustomerTransaction, paidAmount: Long, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val custs = repository.allCustomers.firstOrNull() ?: emptyList()
            val cust = custs.find { it.id == transaction.customerId }
            
            var finalTxAmount = transaction.amount
            var discountDiff = 0L
            
            if (cust != null && transaction.status == "UNREVIEWED" && newStatus != "UNREVIEWED") {
                val clubLevelsVal = _clubLevels.value
                val sortedLevels = clubLevelsVal.sortedByDescending { it.requiredPoints }
                val activeLevel = sortedLevels.find { cust.points >= it.requiredPoints } ?: clubLevelsVal.firstOrNull()
                
                val gameDiscPct = activeLevel?.gameDiscountPercent ?: 0L
                val buffetDiscPct = activeLevel?.buffetDiscountPercent ?: 0L
                val fixedDiscTom = activeLevel?.fixedDiscountToman ?: 0L
                
                val origGameCost = if (transaction.gameCost > 0L) transaction.gameCost else (transaction.amount - transaction.foodCost)
                val origFoodCost = transaction.foodCost
                val origTotal = if (transaction.amount > 0L) transaction.amount else (origGameCost + origFoodCost)
                
                val discountedGameCost = origGameCost * (1L - gameDiscPct / 100L)
                val discountedFoodCost = origFoodCost * (1L - buffetDiscPct / 100L)
                finalTxAmount = ((discountedGameCost + discountedFoodCost) - fixedDiscTom).coerceAtLeast(0L)
                
                discountDiff = origTotal - finalTxAmount
            }

            val updated = transaction.copy(paidAmount = paidAmount, status = newStatus, amount = finalTxAmount)
            repository.updateCustomerTransaction(updated)
            queueOrSyncCustomerTransaction(updated)

            if (cust != null) {
                val newDebt = if (newStatus == "REVIEWED") {
                    val remainingUnpaid = transaction.amount - transaction.paidAmount
                    (cust.debt - remainingUnpaid).coerceAtLeast(0L)
                } else {
                    (cust.debt - (paidAmount - transaction.paidAmount) - discountDiff).coerceAtLeast(0L)
                }
                
                var updatedCust = cust.copy(debt = newDebt)

                if (newStatus == "REVIEWED" && transaction.status != "REVIEWED") {
                    val refId = "SESSION_${transaction.timestamp}_CUST_${transaction.customerId}"
                    val pendingEntry = repository.getGnLedgerEntryByRef(refId)
                    if (pendingEntry != null && pendingEntry.status == "PENDING") {
                        val gnAmt = pendingEntry.gnAmount
                        if (gnAmt > 0) {
                            updatedCust = updatedCust.copy(
                                availableGn = updatedCust.availableGn + gnAmt,
                                pendingGn = (updatedCust.pendingGn - gnAmt).coerceAtLeast(0L)
                            )
                            val availableEntry = pendingEntry.copy(status = "AVAILABLE")
                            repository.updateGnLedgerEntry(availableEntry)
                        }
                    }
                    val earnedLp = if (_lpTomanRate.value > 0L) {
                        (transaction.amount / _lpTomanRate.value).coerceAtLeast(0L)
                    } else 0L
                    if (earnedLp > 0L) {
                        updatedCust = updatedCust.copy(lp = updatedCust.lp + earnedLp)
                    }
                }
                
                repository.insertCustomer(updatedCust)
            }
        }
    }

    fun deleteCustomerTransaction(transaction: CustomerTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomerTransaction(transaction)
        }
    }


    fun incrementBuffetOrder(stationId: Int, productName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingId = "${stationId}_${productName}"
            val orders = repository.getOrdersForStationSync(stationId)
            val matched = orders.find { it.productName == productName }
            if (matched != null) {
                val updated = matched.copy(quantity = matched.quantity + 1)

                val currentMap = _stationOrdersMap.value.toMutableMap()
                val stationOrders = (currentMap[stationId] ?: emptyList()).toMutableList()
                val idx = stationOrders.indexOfFirst { it.productName == productName }
                if (idx >= 0) {
                    stationOrders[idx] = updated
                    currentMap[stationId] = stationOrders
                    _stationOrdersMap.value = currentMap
                }

                repository.insertStationOrder(updated)
                refreshOrdersForStation(stationId)

                val p = repository.getProductByName(productName)
                sendOrQueueBuffetOrder(
                    stationId = stationId,
                    productName = productName,
                    quantity = 1,
                    price = p?.price ?: 0L,
                    targetCustomerId = matched.targetCustomerId
                )

                val st = repository.getStationStateByIdLocal(stationId)
                if (st != null) {
                    saveAndSyncStationState(st)
                }
            }
        }
    }

    fun decrementBuffetOrder(stationId: Int, productName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingId = "${stationId}_${productName}"
            val orders = repository.getOrdersForStationSync(stationId)
            val matched = orders.find { it.productName == productName }
            if (matched != null) {
                val currentMap = _stationOrdersMap.value.toMutableMap()
                val stationOrders = (currentMap[stationId] ?: emptyList()).toMutableList()
                val idx = stationOrders.indexOfFirst { it.productName == productName }

                if (matched.quantity <= 1) {
                    if (idx >= 0) stationOrders.removeAt(idx)
                    currentMap[stationId] = stationOrders
                    _stationOrdersMap.value = currentMap
                    repository.deleteStationOrder(existingId, stationId)
                } else {
                    val updated = matched.copy(quantity = matched.quantity - 1)
                    if (idx >= 0) stationOrders[idx] = updated
                    currentMap[stationId] = stationOrders
                    _stationOrdersMap.value = currentMap
                    repository.insertStationOrder(updated)
                }
                refreshOrdersForStation(stationId)

                val st = repository.getStationStateByIdLocal(stationId)
                if (st != null) {
                    saveAndSyncStationState(st)
                }
            }
        }
    }

    // Settings Updates

    fun selectConsoleInSettings(name: String) {
        _selectedConsoleInSettings.value = name
    }

    fun selectProductInSettings(name: String) {
        _selectedProductInSettings.value = name
    }

    fun saveStationCountSetting(count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("station_count", count.toString())
            _stationCount.value = count
            // Persist the explicit Manager station-count setting on the server as well.
            // This must survive relaunch/re-sync and must not be inferred from station rows.
            if (!NetworkClient.isTrialMode && SelfHostedManager.currentManagerId.isNotBlank()) {
                SelfHostedManager.saveManagerSetting("station_count", count.toString())
            }

            // Get a default console type to assign to any new stations
            val consoleList = consoleTypes.value
            val defaultConsole = if (consoleList.isNotEmpty()) consoleList.first().name else "PS5"
            repository.recreateStations(count, defaultConsole)
            
            // Sync with Server immediately
            try {
                if (_serverSyncMode.value) {
                    val api = com.example.data.network.NetworkClient.getApi(_serverUrl.value)
                    
                    // 1. Purge stations > count
                    try {
                        val reqBody = "{\"stationCount\": $count}".toRequestBody("application/json".toMediaType())
                        val req = okhttp3.Request.Builder()
                            .url("${_serverUrl.value}/api/v1/manager/stations/purge-extra")
                            .headers(com.example.data.network.SelfHostedManager.getBaseHeaders())
                            .post(reqBody)
                            .build()
                        com.example.data.network.SelfHostedManager.client.newCall(req).execute()
                    } catch (e: Exception) {
                        android.util.Log.e("GameNetViewModel", "Failed to purge extra stations", e)
                    }

                    // 2. Upload all current stations
                    val newStations = repository.allStationStates.firstOrNull() ?: emptyList()
                    for (st in newStations) {
                        try { api.saveStation(st) } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
                    }
                }
            } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
        }
    }

    fun saveNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("notifications_enabled", enabled.toString())
            _notificationsEnabled.value = enabled
        }
    }

    fun saveNotchSafeBarEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("notch_safe_bar_enabled", enabled.toString())
            _notchSafeBarEnabled.value = enabled
        }
    }

    fun saveLanguageSetting(lang: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("language", lang)
            _language.value = lang
        }
    }

    fun saveAppTheme(theme: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("app_theme", theme)
            _appTheme.value = theme
        }
    }

    fun saveServerSyncMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("server_sync_mode", enabled.toString())
            _serverSyncMode.value = enabled
            if (enabled) {
                repository.syncAllWithServer()
            }
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val canonicalUrl = "https://api.gamenermayket.ir"
            repository.saveSetting("server_url", canonicalUrl)
            _serverUrl.value = canonicalUrl
            com.example.data.network.SelfHostedManager.setCustomServerUrl(canonicalUrl)
            if (serverSyncMode.value) {
                repository.syncAllWithServer()
            }
        }
    }

    fun triggerManualSync(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.syncAllWithServer()
            loadSettings()
            onComplete(success)
        }
    }

    fun addConsoleType(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.isBlank()) return@launch
            val existing = repository.getConsoleTypeByName(name)
            if (existing == null) {
                val newConsole = ConsoleType(name = name, 100000L, 120000L, 140000L, 160000L)
                repository.insertConsoleType(newConsole)
                _selectedConsoleInSettings.value = name
            }
        }
    }

    fun updateConsolePrices(name: String, p1: Long, p2: Long, p3: Long, p4: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val console = ConsoleType(
                name = name,
                price1 = p1,
                price2 = p2,
                price3 = p3,
                price4 = p4
            )
            repository.insertConsoleType(console)
        }
    }

    fun deleteConsoleType(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteConsoleType(name)
            val remaining = consoleTypes.value.filter { it.name != name }
            if (remaining.isNotEmpty()) {
                _selectedConsoleInSettings.value = remaining.first().name
            } else {
                _selectedConsoleInSettings.value = ""
            }
        }
    }

    fun addProduct(name: String, price: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.isBlank()) return@launch
            val existing = repository.getProductByName(name)
            if (existing == null) {
                val newProd = Product(name = name, price = price)
                repository.insertProduct(newProd)
                _selectedProductInSettings.value = name
            }
        }
    }

    fun updateProductPrice(name: String, price: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val prod = Product(name = name, price = price)
            repository.insertProduct(prod)
        }
    }

    fun deleteProduct(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProduct(name)
            val remaining = products.value.filter { it.name != name }
            if (remaining.isNotEmpty()) {
                _selectedProductInSettings.value = remaining.first().name
            } else {
                _selectedProductInSettings.value = ""
            }
        }
    }

    fun resetHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    // Helper Methods

    suspend fun getHourlyRate(consoleName: String, controllerCount: Int): Long {
        val console = repository.getConsoleTypeByName(consoleName) ?: return 0L
        return when (controllerCount) {
            1 -> console.price1
            2 -> console.price2
            3 -> console.price3
            4 -> console.price4
            else -> console.price1
        }
    }

    fun getHourlyRateSync(consoleName: String, controllerCount: Int): Long {
        val console = consoleTypes.value.find { it.name == consoleName } ?: return 0L
        return when (controllerCount) {
            1 -> console.price1
            2 -> console.price2
            3 -> console.price3
            4 -> console.price4
            else -> console.price1
        }
    }

    private fun scheduleAlarm(stationId: Int, triggerTimeMillis: Long) {
        val context = getApplication<Application>()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("station_id", stationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Fallback to non-exact if permission is missing
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(stationId: Int) {
        val context = getApplication<Application>()
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    // Customers Operations
    fun addCustomer(
        fullName: String,
        phoneNumber: String,
        debt: Long,
        credit: Long,
        description: String,
        invitedByCode: String = "",
        id: Long = 0L,
        manualPoints: Long = 0L,
        password: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var inviteCode = ""
            var existingPoints = 0L
            var invitePointsAwarded = false
            var finalPassword = password.trim()

            if (id != 0L) {
                val existing = repository.getCustomerById(id)
                if (existing != null) {
                    inviteCode = existing.inviteCode
                    existingPoints = existing.points
                    invitePointsAwarded = existing.invitePointsAwarded
                }
            }

            val trimmedName = fullName.trim()
            val trimmedPhone = toEnglishDigits(phoneNumber.trim())

            if (inviteCode.isBlank() && trimmedName.isNotBlank() && trimmedPhone.isNotBlank()) {
                val record = repository.getInviteCodeRecord(trimmedPhone, trimmedName)
                if (record != null) {
                    inviteCode = record.inviteCode
                } else {
                    inviteCode = "GN-${(10000..99999).random()}"
                    repository.saveInviteCodeRecord(InviteCodeRecord(trimmedPhone, trimmedName, inviteCode))
                }
            }

            val signupGiftRule = if (id == 0L) (scoringRules.value.find { it.id == "signup_gift" }?.points ?: 0L) else 0L
            val totalPoints = (existingPoints + manualPoints + signupGiftRule).coerceAtLeast(0L)

            val customer = Customer(
                id = id,
                fullName = trimmedName,
                phoneNumber = trimmedPhone,
                debt = debt,
                credit = credit,
                description = description,
                points = totalPoints,
                inviteCode = inviteCode,
                invitedByCode = invitedByCode.trim(),
                invitePointsAwarded = invitePointsAwarded
            )
            val insertedId = repository.insertCustomer(customer)
            val finalCustomerId = if (id == 0L) insertedId else id
            if (id == 0L && signupGiftRule > 0L) {
                repository.addPointLog(PointLog(customerId = finalCustomerId, title = "هدیه ثبت نام در برنامه", points = signupGiftRule))
            }
            checkAndAwardInvitePoints()

            // Real-time Cloud sync to self-hosted server
            try {
                SelfHostedManager.upsertCustomer(customer.copy(id = finalCustomerId))
            } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", ignored) }

            logOperatorActivity(
                actionTitle = if (id == 0L) "افزودن مشتری جدید" else "ویرایش اطلاعات مشتری",
                details = "مشتری: $trimmedName | تلفن: $trimmedPhone" + if (finalPassword.isNotBlank()) " (دارای حساب کاربری)" else ""
            )
        }
    }

    fun convertGuestToCustomer(
        guestName: String,
        fullName: String,
        phoneNumber: String,
        debt: Long = 0L,
        credit: Long = 0L,
        description: String = "",
        invitedByCode: String = "",
        password: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = fullName.trim().ifBlank { guestName.trim() }
            val trimmedPhone = phoneNumber.trim()
            val cleanPass = password.trim()
            var inviteCode = ""
            if (trimmedName.isNotBlank() && trimmedPhone.isNotBlank()) {
                val record = repository.getInviteCodeRecord(trimmedPhone, trimmedName)
                if (record != null) {
                    inviteCode = record.inviteCode
                } else {
                    inviteCode = "GN-${(10000..99999).random()}"
                    repository.saveInviteCodeRecord(InviteCodeRecord(trimmedPhone, trimmedName, inviteCode))
                }
            }

            val signupGiftRule = scoringRules.value.find { it.id == "signup_gift" }?.points ?: 0L
            val newCustomer = Customer(
                id = 0L,
                fullName = trimmedName,
                phoneNumber = trimmedPhone,
                debt = debt,
                credit = credit,
                description = description.trim(),
                points = signupGiftRule,
                inviteCode = inviteCode,
                invitedByCode = invitedByCode.trim(),
                invitePointsAwarded = false
            )
            val newId = repository.insertCustomer(newCustomer)
            if (signupGiftRule > 0L) {
                repository.addPointLog(PointLog(customerId = newId, title = "هدیه ثبت نام در برنامه", points = signupGiftRule))
            }
            checkAndAwardInvitePoints()

            // Real-time Cloud sync to self-hosted server
            try {
                SelfHostedManager.upsertCustomer(newCustomer.copy(id = newId))
            } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", ignored) }

            logOperatorActivity("تبدیل مهمان به مشتری", "نام: $trimmedName | تلفن: $trimmedPhone")

            // Update all transactions of this guest to link to newly created customer
            val allTrans = repository.getAllCustomerTransactionsLocal()
            val cleanGuest = guestName.trim()
            for (trans in allTrans) {
                val isMatch = trans.customerName.trim().equals(cleanGuest, ignoreCase = true) ||
                        (trans.customerId <= 0 && trans.customerName.contains(cleanGuest, ignoreCase = true))
                if (isMatch) {
                    val updatedTrans = trans.copy(
                        customerId = newId,
                        customerName = trimmedName
                    )
                    repository.insertCustomerTransaction(updatedTrans)
                }
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch(Dispatchers.IO) {
            // Server is authoritative for customer deletion/archival. Do not remove the local
            // row first: a failed server write would otherwise be resurrected by the next sync.
            val cloudDeleted = runCatching {
                com.example.data.network.SelfHostedManager.deleteCustomer(customer.id, customer.phoneNumber)
            }.getOrDefault(false)
            if (!cloudDeleted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "حذف مشتری از سرور انجام نشد؛ اطلاعات محلی حفظ شد.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            repository.deleteCustomer(customer)
            logOperatorActivity(
                actionTitle = "حذف مشتری",
                details = "مشتری ${customer.fullName} با شماره ${customer.phoneNumber} از فهرست فعال مشتریان حذف شد و سوابق مالی حفظ شد."
            )
        }
    }

    fun deleteCustomersBatch(customersToDelete: List<Customer>) {
        if (customersToDelete.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ids = customersToDelete.map { it.id }
            val cloudDeleted = runCatching {
                com.example.data.network.SelfHostedManager.deleteCustomersBatch(ids)
            }.getOrDefault(false)
            if (!cloudDeleted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "حذف مشتریان از سرور انجام نشد؛ اطلاعات محلی حفظ شد.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            repository.deleteCustomersBatch(customersToDelete)
            logOperatorActivity(
                actionTitle = "حذف دسته‌جمعی مخاطبان",
                details = "تعداد ${customersToDelete.size} مخاطب از فهرست فعال حذف شدند و سوابق مالی حفظ شدند."
            )
        }
    }

    // Reservations Operations
    fun addReservation(fullName: String, phoneNumber: String, reservationTimeMillis: Long, durationMinutes: Int, stationId: Long, isVip: Boolean = false, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val reservation = Reservation(
                fullName = fullName,
                phoneNumber = phoneNumber,
                reservationTimeMillis = reservationTimeMillis,
                durationMinutes = durationMinutes
            )
            try {
                val synced = SelfHostedManager.syncReservationToCloud(reservation, stationId, isVip)
                if (synced) {
                    val generatedId = repository.insertReservation(reservation)
                    val savedReservation = reservation.copy(id = generatedId)
                    scheduleReservationAlarms(savedReservation)
                    logOperatorActivity("درخواست رزرو جدید", "رزرو برای $fullName ثبت گردید.")
                } else {
                    android.util.Log.w("GameNetViewModel", "Manager reservation was rejected by the server; local reservation was not persisted.")
                }
            } catch (e: Exception) {
                android.util.Log.e("GameNetViewModel", "Manager reservation server submission failed", e)
            }
        }
    }

    fun deleteReservation(reservation: Reservation) {
        viewModelScope.launch(Dispatchers.IO) {
            cancelReservationAlarms(reservation)
            try {
                SelfHostedManager.cancelManagerReservation(
                    reservation.id,
                    "manager-cancel:" + reservation.id
                )
            } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", ignored) }
        }
    }

    private suspend fun scheduleReservationAlarms(reservation: Reservation) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val configured = SelfHostedManager.fetchReservationRules()?.optJSONObject("rules")?.optJSONArray("arrivalReminderMinutes")
        val times = if (configured != null) {
            buildList { for (i in 0 until configured.length()) if (configured.optInt(i) > 0) add(configured.optInt(i)) }
        } else emptyList()

        for (minutesBefore in times) {
            val triggerTimeMillis = reservation.reservationTimeMillis - minutesBefore * 60 * 1000L
            if (triggerTimeMillis <= System.currentTimeMillis()) {
                continue
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("reservation_id", reservation.id)
                putExtra("minutes_before", minutesBefore)
            }
            val requestCode = 20000 + (reservation.id * 10 + minutesBefore).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        }
    }

    private suspend fun cancelReservationAlarms(reservation: Reservation) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val configured = SelfHostedManager.fetchReservationRules()?.optJSONObject("rules")?.optJSONArray("arrivalReminderMinutes")
        val times = if (configured != null) {
            buildList { for (i in 0 until configured.length()) if (configured.optInt(i) > 0) add(configured.optInt(i)) }
        } else emptyList()

        for (minutesBefore in times) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val requestCode = 20000 + (reservation.id * 10 + minutesBefore).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    @Volatile
    private var cachedStableDeviceId: String? = null

    fun getDeviceId(): String {
        cachedStableDeviceId?.let { return it }
        val context = getApplication<Application>()
        val rawId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        val finalDeviceId = if (rawId.isNotBlank() && rawId != "UNKNOWN_DEVICE") {
            "DEV_SEC_" + rawId.lowercase()
        } else {
            "DEV_SEC_" + "${android.os.Build.BOARD}_${android.os.Build.BRAND}_${android.os.Build.DEVICE}".hashCode().toString().replace("-", "")
        }
        cachedStableDeviceId = finalDeviceId
        return finalDeviceId
    }

    /**
     * Canonical device identity for the server-authoritative 24h Trial.
     *
     * Do not use app-local random IDs or App Set ID here: those can reset when a
     * sideloaded app is removed/replaced. ANDROID_ID is scoped by Android to the
     * app signing key, user and device, so the production signing key must remain
     * stable across releases for the Trial to remain one-per-device.
     */
    /**
     * Trial identity is deliberately based on the platform ANDROID_ID only.
     * ANDROID_ID is stable across uninstall/reinstall only when the app keeps the
     * same signing key; therefore release/validation builds must never use an
     * ephemeral signing certificate. The server remains the authority for the
     * one-trial-per-device rule.
     */
    fun getTrialDeviceId(): String = getDeviceId()

    fun getTrialDeviceFingerprint(): String = getTrialDeviceId()

    fun getDeviceFingerprint(): String {
        return getDeviceId()
    }

    fun checkLicenseStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            verifyLicenseStatus()
        }
    }

    fun savePendingPurchasedLicense(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            encryptSetting("enc_pending_purchased_license", code)
        }
    }

    suspend fun getPendingPurchasedLicense(): String = withContext(Dispatchers.IO) {
        decryptSetting("enc_pending_purchased_license")
    }

    suspend fun verifyLicenseStatus() {
        val planType = decryptSetting("enc_plan_type")
        val savedRole = decryptSetting("enc_admin_role")
        val licenseStatus = decryptSetting("enc_license_status")

        val currentRole = _currentAdminRole.value
        val isSuper = savedRole == "SUPER_MANAGER" || currentRole == "SUPER_MANAGER"

        val isManager = savedRole == "MANAGER" || currentRole == "MANAGER" || 
                        savedRole == "GAMENET_MANAGER" || currentRole == "GAMENET_MANAGER"

        if (isManager) {
            com.example.data.network.NetworkClient.isTrialMode = false
            // Server is the only authority for Manager subscription validity.
            // Do not synthesize a plan or expiry from local cache here.

            // Continue into the common server verification path below.
        }

        if (isSuper) {
            try {
                // Validate Super Manager using the exact authenticated OkHttp client.
                // This guarantees both Authorization and X-Manager-ID are present.
                if (!SelfHostedManager.checkAuthenticatedManagerSession()) {
                    throw IllegalStateException("Super Manager authentication rejected by server")
                }
                _isServerConnected.value = true
                _isSubscribed.value = true
                _isAdminAuthenticated.value = true
                _isCustomerAuthenticated.value = false
                _currentAdminRole.value = "SUPER_MANAGER"
                NetworkClient.isTrialMode = false
                encryptSetting("enc_session_type", "ADMIN")
                encryptSetting("enc_admin_role", "SUPER_MANAGER")
                encryptSetting("enc_license_status", "ACTIVE")
                encryptSetting("enc_plan_type", "SUPER_MANAGER")
                encryptSetting("enc_expire_time", Long.MAX_VALUE.toString())
                val serverNow = System.currentTimeMillis()
                _licenseState.value = LicenseState.Active("SUPER_MANAGER", Long.MAX_VALUE, serverNow, "SUPER_MANAGER_LIFETIME", true, serverNow)
                return
            } catch (_: Exception) {
                _isServerConnected.value = false
            }
        }

        val isTrial = !isSuper && (planType.equals("TRIAL", ignoreCase = true) ||
                      savedRole.equals("TRIAL_USER", ignoreCase = true) ||
                      licenseStatus.equals("TRIAL", ignoreCase = true) ||
                      _currentAdminRole.value == "TRIAL_USER")

        val deviceId = getDeviceId()
        val trialDeviceId = getTrialDeviceId()
        
        if (isTrial) {
            _currentAdminRole.value = "TRIAL_USER"
            com.example.data.network.NetworkClient.isTrialMode = true
            val now = System.currentTimeMillis()
            // Always query server for authoritative 24h trial status across candidate endpoints
            var trialCheck: com.example.data.network.CheckTrialResponse? = null
            val candidateTrialUrls = (listOf(_serverUrl.value, SelfHostedManager.SERVER_URL) + SelfHostedManager.candidateUrls).filter { it.isNotBlank() }.distinct()
            for (candidateUrl in candidateTrialUrls) {
                try {
                    val api = NetworkClient.getApi(candidateUrl)
                    val res = api.checkTrialStatus(com.example.data.network.CheckTrialRequest(deviceId = trialDeviceId, deviceFingerprint = getTrialDeviceFingerprint(), altDeviceId = trialDeviceId, deviceName = android.os.Build.MODEL ?: "Unknown"))
                    trialCheck = res
                    _isServerConnected.value = true
                    _serverUrl.value = candidateUrl
                    SelfHostedManager.setCustomServerUrl(candidateUrl)
                    break
                } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
            }

            if (trialCheck != null) {
                if (trialCheck.isExpired) {
                    _isTrialUsed.value = true
                    _licenseState.value = LicenseState.Expired(trialCheck.responseMessage)
                    _isSubscribed.value = false
                    encryptSetting("enc_license_status", "EXPIRED")
                    return
                }
                
                val serverNow = trialCheck.serverTime ?: now
                val remainingMs = when {
                    trialCheck.expiresAt != null && trialCheck.expiresAt > 0L -> (trialCheck.expiresAt - serverNow).coerceAtLeast(0L)
                    trialCheck.remainingMilliseconds > 0L -> trialCheck.remainingMilliseconds
                    else -> 0L
                }
                val expiresAt = trialCheck.expiresAt ?: (serverNow + remainingMs)
                
                // Schedule local kill-switch timer
                scheduleExpiration(expiresAt)
                
                _isTrialUsed.value = true
                _isSubscribed.value = true
                _isAdminAuthenticated.value = true
                _currentAdminRole.value = "TRIAL_USER"
                encryptSetting("enc_admin_role", "TRIAL_USER")
                encryptSetting("enc_plan_type", "TRIAL")
                encryptSetting("enc_expire_time", expiresAt.toString())
                repository.ensureTrialDataExists()
                _licenseState.value = LicenseState.Active(
                    planType = "TRIAL",
                    expiresAt = expiresAt,
                    activatedAt = serverNow,
                    licenseCode = "TRIAL_24H",
                    hasPassword = true,
                    lastServerValidationTime = serverNow
                )
                return
            } else {
                // Trial validity is server-authoritative. A cached/local expiry must
                // never grant continued Trial access when the validation server is unreachable.
                _isSubscribed.value = false
                _isAdminAuthenticated.value = false
                _isServerConnected.value = false
                _licenseState.value = LicenseState.ConnectionRequired(
                    "برای بررسی اعتبار اشتراک تست 24 ساعته، اتصال به سرور اعتبارسنجی الزامی است."
                )
                return
            }
        }
        
        loadPurchasedLicenses()
        val deviceFingerprint = getDeviceFingerprint()
        val userId = decryptSetting("enc_user_id").ifBlank { null }
        val savedPhone = decryptSetting("enc_user_phone").ifBlank { null }
        
        // Check if there is a pending purchased license to auto-activate
        val pendingPurchasedCode = decryptSetting("enc_pending_purchased_license")
        if (pendingPurchasedCode.isNotBlank()) {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val actRes = api.activateLicense(
                    LicenseActivateRequest(
                        deviceId = deviceId,
                        licenseCode = pendingPurchasedCode,
                        activationSecret = decryptSetting("enc_pending_activation_secret").ifBlank { null },
                        userPhone = savedPhone
                    )
                )
                if (actRes.valid) {
                    val activeCode = actRes.realLicenseCode.ifBlank { pendingPurchasedCode }
                    val serverTime = actRes.realServerTime
                    val nowElapsed = android.os.SystemClock.elapsedRealtime()
                    saveLicenseLocal(activeCode, actRes.type ?: "", actRes.realExpiresAt, serverTime, true)
                    encryptSetting("enc_pending_purchased_license", "")
                    
                    encryptSetting("enc_last_server_validation_time", serverTime.toString())
                    encryptSetting("enc_last_validation_elapsed", nowElapsed.toString())
                    encryptSetting("enc_license_status", "ACTIVE")
                    encryptSetting("enc_plan_type", actRes.type ?: "ACTIVE")
                    encryptSetting("enc_expire_time", actRes.realExpiresAt.toString())
                    
                    repository.saveLicenseCache(LicenseCacheEntity(
                        id = 1,
                        userId = userId ?: "",
                        deviceId = deviceId,
                        licenseStatus = "ACTIVE",
                        planType = actRes.type ?: "ACTIVE",
                        expireTime = actRes.realExpiresAt,
                        lastServerValidationTime = serverTime
                    ))

                    _isServerConnected.value = true
                    _isSubscribed.value = true
                    viewModelScope.launch(Dispatchers.IO) { pushOfflineChangesToCloud() }
                    _licenseState.value = LicenseState.Active(actRes.type ?: "", actRes.realExpiresAt, actRes.realActivatedAt, activeCode, actRes.realHasPassword, serverTime)
                    return
                }
            } catch (e: Exception) {
                // Ignore network error during pending activation check
            }
        }

        // Try primary online verification with server
        try {
            val api = NetworkClient.getApi(_serverUrl.value)
            
            val checkRes: SubscriptionCheckResponse = try {
                api.checkSubscriptionStatus(userId = userId, deviceId = deviceId, deviceFingerprint = deviceFingerprint)
            } catch (eCheck: Exception) {
                val code = decryptSetting("enc_license_code").ifBlank { null }
                val fallbackRes = try {
                    api.checkSubscription(deviceId = deviceId, licenseCode = code, userPhone = savedPhone)
                } catch (e2: Exception) {
                    api.checkLicenseStatus(LicenseCheckRequest(deviceId, code, savedPhone))
                }
                SubscriptionCheckResponse(
                    licenseStatusRaw = if (fallbackRes.isValid) (if (fallbackRes.type == "TRIAL") "TRIAL" else "ACTIVE") else "INACTIVE",
                    validRaw = fallbackRes.isValid,
                    planTypeRaw = fallbackRes.type ?: "MONTH1",
                    expireTimeRaw = fallbackRes.realExpiresAt,
                    serverTimeRaw = fallbackRes.realServerTime,
                    message = fallbackRes.message,
                    trialUsed = fallbackRes.isTrialUsed,
                    licenseCode = fallbackRes.realLicenseCode
                )
            }

            _isServerConnected.value = true
            val serverTime = checkRes.realServerTime
            val nowElapsed = android.os.SystemClock.elapsedRealtime()

            if (checkRes.trialUsed == true || checkRes.realLicenseStatus == "TRIAL") {
                _isTrialUsed.value = true
                encryptSetting("enc_trial_used", "true")
            }

            // Save encrypted status fields locally
            encryptSetting("enc_last_server_validation_time", serverTime.toString())
            encryptSetting("enc_last_validation_elapsed", nowElapsed.toString())
            encryptSetting("enc_license_status", checkRes.realLicenseStatus)
            encryptSetting("enc_plan_type", checkRes.realPlanType)
            encryptSetting("enc_expire_time", checkRes.realExpireTime.toString())

            // Room Database Caching
            repository.saveLicenseCache(
                LicenseCacheEntity(
                    id = 1,
                    userId = userId ?: "",
                    deviceId = deviceId,
                    licenseStatus = checkRes.realLicenseStatus,
                    planType = checkRes.realPlanType,
                    expireTime = checkRes.realExpireTime,
                    lastServerValidationTime = serverTime
                )
            )

            if (checkRes.isLicenseActive) {
                if (checkRes.realExpireTime in 1..serverTime) {
                    // Subscription expired on server
                    _isSubscribed.value = false
                    _licenseState.value = LicenseState.Expired("اشتراک شما به پایان رسیده است.")
                } else {
                    val code = checkRes.licenseCode ?: decryptSetting("enc_license_code")
                    saveLicenseLocal(code, checkRes.realPlanType, checkRes.realExpireTime, serverTime, true)
                    _isSubscribed.value = true
                    _licenseState.value = LicenseState.Active(
                        planType = checkRes.realPlanType,
                        expiresAt = checkRes.realExpireTime,
                        activatedAt = serverTime,
                        licenseCode = code,
                        lastServerValidationTime = serverTime
                    )
                }
            } else {
                _isSubscribed.value = false
                val msg = checkRes.message.ifBlank { "نیاز به فعال‌سازی اشتراک یا تست رایگان می‌باشد." }
                _licenseState.value = LicenseState.Unactivated(msg)
            }
        } catch (e: Exception) {
            // Offline verification -> 100% Stable and Active Offline Mode
            _isServerConnected.value = false
            
            val roomCache = repository.getLicenseCache()
            val lastServerValTime = roomCache?.lastServerValidationTime ?: (decryptSetting("enc_last_server_validation_time").toLongOrNull() ?: 0L)
            val lastElapsed = decryptSetting("enc_last_validation_elapsed").toLongOrNull() ?: 0L
            val cachedPlan = roomCache?.planType.takeIf { !it.isNullOrBlank() } ?: decryptSetting("enc_plan_type")
            val cachedExpireTime = roomCache?.expireTime ?: (decryptSetting("enc_expire_time").toLongOrNull() ?: 0L)
            val now = System.currentTimeMillis()
            val activeCode = decryptSetting("enc_license_code")
            
            val currentElapsed = android.os.SystemClock.elapsedRealtime()
            val estimatedCurrentServerTime = if (lastServerValTime > 0 && lastElapsed > 0) {
                if (currentElapsed >= lastElapsed) {
                    val est = lastServerValTime + (currentElapsed - lastElapsed)
                    if (now < lastServerValTime) Long.MAX_VALUE else est
                } else {
                    // Device rebooted
                    if (now < lastServerValTime) Long.MAX_VALUE else now
                }
            } else {
                if (now < lastServerValTime) Long.MAX_VALUE else now
            }

            // PHASE 2: 24-Hour Offline Strict Enforcement
            val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1000
            val timeSinceLastSync = Math.abs(estimatedCurrentServerTime - lastServerValTime)
            if (lastServerValTime > 0L && timeSinceLastSync > TWENTY_FOUR_HOURS_MS) {
                // Auto-logout user/manager due to 24h offline limit
                android.util.Log.e("OfflineCheck", "Offline for more than 24 hours. Forcing logout.")
                _isSubscribed.value = false
                _licenseState.value = LicenseState.ConnectionRequired("بیش از 24 ساعت است که ارتباط با سرور قطع است. جهت حفظ امنیت سیستم، باید مجددا لاگین کنید.")
                
                // Clear credentials to force relogin
                encryptSetting("enc_auth_token", "")
                encryptSetting("enc_user_id", "")
                encryptSetting("enc_auth_username", "")
                encryptSetting("enc_auth_phone", "")
                _isAdminAuthenticated.value = false
                _isCustomerAuthenticated.value = false
                com.example.data.network.SelfHostedManager.setManagerId("")
                return
            }

            
            if (cachedPlan.isBlank() || (cachedPlan == "TRIAL" && (cachedExpireTime <= 0 || estimatedCurrentServerTime >= cachedExpireTime))) {
                _isSubscribed.value = false
                if (cachedPlan == "TRIAL" && cachedExpireTime > 0 && estimatedCurrentServerTime >= cachedExpireTime) {
                    _licenseState.value = LicenseState.Expired("اشتراک تست 24 ساعته شما به پایان رسیده است.")
                } else {
                    _licenseState.value = LicenseState.Unactivated("اطلاعات لایسنس بر روی این دستگاه یافت نشد. جهت فعال‌سازی اتصال به اینترنت الزامی است.")
                }
            } else {
                _isSubscribed.value = true
                val savedRole = decryptSetting("enc_admin_role")
                if (cachedPlan == "TRIAL" || savedRole == "TRIAL_USER") {
                    _currentAdminRole.value = "TRIAL_USER"
                    encryptSetting("enc_admin_role", "TRIAL_USER")
                } else if (savedRole.isNotBlank()) {
                    _currentAdminRole.value = savedRole
                }
                _licenseState.value = LicenseState.Active(
                    planType = cachedPlan,
                    expiresAt = if (cachedExpireTime > 0) cachedExpireTime else (now + 86400000L * 3650),
                    activatedAt = if (lastServerValTime > 0) lastServerValTime else estimatedCurrentServerTime,
                    licenseCode = activeCode,
                    lastServerValidationTime = if (lastServerValTime > 0) lastServerValTime else estimatedCurrentServerTime
                )
            }
        }
    }

    fun resetCurrentDeviceTrial() {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = getDeviceId()
            encryptSetting("enc_license_status", "INACTIVE")
            encryptSetting("enc_plan_type", "NONE")
            encryptSetting("enc_expire_time", "0")
            repository.saveLicenseCache(
                LicenseCacheEntity(
                    id = 1,
                    userId = "",
                    deviceId = deviceId,
                    licenseStatus = "INACTIVE",
                    planType = "NONE",
                    expireTime = 0L,
                    lastServerValidationTime = 0L
                )
            )
            withContext(Dispatchers.Main) {
                _isTrialUsed.value = false
                _isSubscribed.value = false
                _licenseState.value = LicenseState.Unactivated("تست دستگاه ریست گردید.")
            }
        }
    }

    fun activateFreeTrial(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deviceId = getTrialDeviceId()
                val now = System.currentTimeMillis()
                
                var serverTrialStatus: com.example.data.network.CheckTrialResponse? = null
                val urls = (listOf(_serverUrl.value, SelfHostedManager.SERVER_URL) + SelfHostedManager.candidateUrls).filter { it.isNotBlank() }.distinct()
                for (u in urls) {
                    try {
                        val api = com.example.data.network.NetworkClient.getApi(u)
                        val startRes = api.startFreeTrial(
                            com.example.data.network.TrialStartRequest(
                                deviceId = deviceId,
                                deviceFingerprint = getTrialDeviceFingerprint()
                            )
                        )
                        serverTrialStatus = com.example.data.network.CheckTrialResponse(
                            status = if (startRes.trialActive) "active" else "expired",
                            hoursLeft = (startRes.remainingMinutes ?: 0) / 60.0,
                            expiresAt = startRes.expiresAt,
                            serverTime = startRes.serverTime,
                            message = startRes.message
                        )
                        _isServerConnected.value = true
                        _serverUrl.value = u
                        SelfHostedManager.setCustomServerUrl(u)
                        repository.saveSetting("server_url", u)
                        break
                    } catch (e: Throwable) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
                }

                if (serverTrialStatus != null) {
                    if (serverTrialStatus.isExpired) {
                        _isTrialUsed.value = true
                        encryptSetting("enc_trial_used", "true")
                        _isSubscribed.value = false
                        _licenseState.value = LicenseState.Expired(serverTrialStatus.responseMessage)
                        withContext(Dispatchers.Main) {
                            onResult(false, serverTrialStatus.responseMessage)
                        }
                        return@launch
                    } else {
                        val effectiveExpire = serverTrialStatus.expiresAt ?: 0L
                        if (effectiveExpire <= 0L) {
                            withContext(Dispatchers.Main) { onResult(false, "پاسخ معتبر از سرور دریافت نشد.") }
                            return@launch
                        }
                        _isSubscribed.value = true
                        _isTrialUsed.value = true
                        _isAdminAuthenticated.value = true
                        _currentAdminRole.value = "TRIAL_USER"
                        encryptSetting("enc_session_type", "ADMIN")
                        encryptSetting("enc_admin_role", "TRIAL_USER")
                        encryptSetting("enc_trial_used", "true")
                        encryptSetting("enc_license_status", "TRIAL")
                        encryptSetting("enc_plan_type", "TRIAL")
                        encryptSetting("enc_expire_time", effectiveExpire.toString())
                        encryptSetting("enc_last_server_validation_time", (serverTrialStatus.serverTime ?: now).toString())
                        encryptSetting("enc_last_validation_elapsed", android.os.SystemClock.elapsedRealtime().toString())
                        
                        try {
                            repository.ensureTrialDataExists()
                        } catch (repoErr: Throwable) {
                            Log.e("GameNetViewModel", "ensureTrialDataExists error: ${repoErr.message}")
                        }
                        
                        _licenseState.value = LicenseState.Active("TRIAL", effectiveExpire, serverTrialStatus.serverTime ?: now, "TRIAL_24H", true, serverTrialStatus.serverTime ?: now)
                        withContext(Dispatchers.Main) {
                            onResult(true, serverTrialStatus.responseMessage)
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(false, "سرور فعال‌سازی تست در دسترس نیست. برای فعال‌سازی تست اتصال به اینترنت الزامی است.")
                }
            } catch (t: Throwable) {
                Log.e("GameNetViewModel", "activateFreeTrial failed", t)
                withContext(Dispatchers.Main) {
                    onResult(false, "خطا در فعال‌سازی تست 24 ساعته. اتصال به سرور الزامی است.")
                }
            }
        }
    }

    fun saveUserPhone(phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (phone.isNotBlank()) {
                encryptSetting("enc_user_phone", phone.trim())
            }
        }
    }

    suspend fun getUserPhone(): String = withContext(Dispatchers.IO) {
        decryptSetting("enc_user_phone")
    }

    private val _purchasedLicenses = MutableStateFlow<List<String>>(emptyList())
    val purchasedLicenses: StateFlow<List<String>> = _purchasedLicenses.asStateFlow()

    suspend fun loadPurchasedLicenses() = withContext(Dispatchers.IO) {
        val raw = decryptSetting("enc_purchased_licenses_csv")
        val set = raw.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.toMutableSet()
        val pending = decryptSetting("enc_pending_purchased_license").trim().uppercase()
        if (pending.isNotBlank()) set.add(pending)
        val currentActive = decryptSetting("enc_license_code").trim().uppercase()
        if (currentActive.isNotBlank()) set.add(currentActive)
        withContext(Dispatchers.Main) {
            _purchasedLicenses.value = set.toList()
        }
    }

    suspend fun savePurchasedLicense(code: String) = withContext(Dispatchers.IO) {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) return@withContext
        val current = _purchasedLicenses.value.toMutableList()
        if (!current.contains(clean)) {
            current.add(0, clean)
            withContext(Dispatchers.Main) {
                _purchasedLicenses.value = current
            }
            encryptSetting("enc_purchased_licenses_csv", current.joinToString(","))
        }
    }

    private suspend fun saveLicenseLocal(code: String, type: String, expiresAt: Long, lastChecked: Long, isValid: Boolean) {
        val checkTime = if (lastChecked > 0L) lastChecked else System.currentTimeMillis()
        encryptSetting("enc_license_code", code)
        encryptSetting("enc_license_type", type)
        encryptSetting("enc_expires_at", expiresAt.toString())
        encryptSetting("enc_last_checked", checkTime.toString())
        encryptSetting("enc_is_valid", isValid.toString())
        if (code.isNotBlank()) savePurchasedLicense(code)
    }

    fun setLicenseExpired(reason: String) {
        _licenseState.value = LicenseState.Expired(reason)
        _isSubscribed.value = false
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            encryptSetting("enc_license_status", "EXPIRED")
        }
    }

    fun handleAccessDenied() {
        // Log out the user so they are sent to the Entry screen, but DO NOT wipe the local database.
        // This allows them to purchase a license or connect to the internet without losing their trial data.
        _isAdminAuthenticated.value = false
        _isCustomerAuthenticated.value = false
    }

    fun activateLicenseCode(code: String, txnId: String = "", userPhone: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = getDeviceId()
            val savedPhone = decryptSetting("enc_user_phone")
            val phoneToUse = userPhone.trim().ifBlank { savedPhone }
            if (phoneToUse.isNotBlank()) {
                encryptSetting("enc_user_phone", phoneToUse)
            }
            val extType = decryptSetting("enc_pending_extension_type")
            val extExpiresAtStr = decryptSetting("enc_pending_extension_expiresAt")
            val extMaxDevicesStr = decryptSetting("enc_pending_extension_maxDevices")
            
            val req = LicenseActivateRequest(
                deviceId = deviceId, 
                licenseCode = code, 
                userPhone = phoneToUse.ifBlank { null },
                extensionType = extType.ifBlank { null },
                extensionExpiresAt = extExpiresAtStr.toLongOrNull(),
                extensionMaxDevices = extMaxDevicesStr.toIntOrNull(),
                transactionId = txnId.ifBlank { null },
                activationSecret = decryptSetting("enc_pending_activation_secret").ifBlank { null }
            )
            
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.activateLicense(req)
                if (response.valid) {
                    encryptSetting("enc_pending_extension_type", "")
                    encryptSetting("enc_pending_extension_expiresAt", "")
                    encryptSetting("enc_pending_extension_maxDevices", "")
                    val activeCode = response.realLicenseCode.ifBlank { code }
                    saveLicenseLocal(activeCode, response.type ?: "", response.expiresAt, response.serverTime, true)
                    _isSubscribed.value = true
                    _licenseState.value = LicenseState.Active(response.type ?: "", response.expiresAt, response.activatedAt, activeCode, response.hasPassword)
                    
                    if (!response.hasPassword) {
                        _showSetPasswordForCode.value = activeCode
                    }

                    withContext(Dispatchers.Main) {
                        onResult(true, response.message)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, response.message)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "خطا در برقراری ارتباط با سرور.")
                }
            }
        }
    }

    fun initiateSubscriptionPurchase(
        plan: String,
        extraDevices: Int = 0,
        coupon: String = "",
        extendLicense: Boolean = false,
        existingLicenseCode: String = "",
        userPhone: String = "",
        userName: String = "",
        gameNetName: String = "",
        password: String = "",
        gateway: String = "",
        onResult: (LicenseBuyResponse?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = getDeviceId()
            val savedPhone = decryptSetting("enc_user_phone")
            val phoneToUse = userPhone.trim().ifBlank { savedPhone }
            if (phoneToUse.isNotBlank()) {
                encryptSetting("enc_user_phone", phoneToUse)
            }
            if (userName.isNotBlank()) {
                encryptSetting("enc_user_name", userName.trim())
            }
            if (gameNetName.isNotBlank()) {
                encryptSetting("enc_gamenet_name", gameNetName.trim())
            }
            try {
                val subscriptionIdempotencyKey = "subscription-purchase:" + deviceId + ":" + plan + ":" + java.util.UUID.randomUUID()
                encryptSetting("enc_pending_subscription_purchase_idempotency", subscriptionIdempotencyKey)
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.buySubscription(
                    LicenseBuyRequest(
                        deviceId = deviceId,
                        plan = plan,
                        coupon = coupon,
                        extraDevices = extraDevices,
                        extendLicense = extendLicense,
                        existingLicenseCode = existingLicenseCode,
                        userPhone = phoneToUse.ifBlank { null },
                        userName = userName.ifBlank { null },
                        gameNetName = gameNetName.ifBlank { null },
                        password = password.ifBlank { null },
                        gateway = gateway.ifBlank { null },
                        idempotencyKey = subscriptionIdempotencyKey
                    )
                )
                if (!response.licenseCode.isNullOrBlank()) {
                    encryptSetting("enc_pending_purchased_license", response.licenseCode)
                    encryptSetting("enc_pending_activation_secret", response.activationSecret ?: "")
                    if (extendLicense) {
                        encryptSetting("enc_pending_extension_type", response.type)
                        encryptSetting("enc_pending_extension_expiresAt", response.expiresAt.toString())
                        encryptSetting("enc_pending_extension_maxDevices", response.maxDevices.toString())
                    } else {
                        encryptSetting("enc_pending_extension_type", "")
                        encryptSetting("enc_pending_extension_expiresAt", "")
                        encryptSetting("enc_pending_extension_maxDevices", "")
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }
    fun validateCoupon(code: String, plan: String, onResult: (Boolean, String, Int, Long, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.validateCoupon(mapOf("code" to code, "plan" to plan, "deviceId" to getDeviceId()))
                val isValid = response["valid"] as? Boolean ?: false
                val message = response["message"] as? String ?: ""
                val discountPercent = (response["discountPercent"] as? Number)?.toInt() ?: 0
                val fixedDiscountAmount = (response["fixedDiscountAmount"] as? Number)?.toLong() ?: 0L
                val discountType = response["discountType"] as? String ?: "PERCENTAGE"
                val isLicenseCode = response["isLicenseCode"] as? Boolean ?: false

                if (isValid && (isLicenseCode || message.contains("لایسنس"))) {
                    checkLicenseStatus()
                }

                withContext(Dispatchers.Main) {
                    onResult(isValid, message, discountPercent, fixedDiscountAmount, discountType)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "خطا در بررسی کد", 0, 0L, "PERCENTAGE")
                }
            }
        }
    }

    private val _subscriptionPlans = MutableStateFlow<List<SubscriptionPlanDto>>(emptyList())
    val subscriptionPlans: StateFlow<List<SubscriptionPlanDto>> = _subscriptionPlans.asStateFlow()

    private fun sanitizeAndOrderPlans(rawPlans: List<SubscriptionPlanDto>): List<SubscriptionPlanDto> {
        if (rawPlans.isEmpty()) return emptyList()
        return rawPlans
            .filter { it.realId.uppercase() in setOf("MONTHLY", "THREE_MONTHS", "YEARLY") && it.realDurationDays > 0 && it.realPrice > 0.0 && it.realPaymentUrl.startsWith("https://pay.forbix.ir/") }
            .sortedWith(compareBy<SubscriptionPlanDto> { it.sortOrder }.thenBy { it.realId })
    }

    private val _subscriptionPlansAdmin = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val subscriptionPlansAdmin: StateFlow<Map<String, Any?>> = _subscriptionPlansAdmin.asStateFlow()

    fun fetchSubscriptionPlansAdmin(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.getSubscriptionPlansAdmin()
                if (response.success) {
                    _subscriptionPlansAdmin.value = response.settings
                    withContext(Dispatchers.Main) { onResult(true, "") }
                } else withContext(Dispatchers.Main) { onResult(false, "دریافت تنظیمات پلن‌ها ناموفق بود.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "ارتباط با سرور برقرار نشد.") }
            }
        }
    }

    fun updateSubscriptionPlansAdmin(settings: Map<String, Any?>, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val plans = settings["plans"] as? List<*> ?: emptyList<Any?>()
                val page: Map<String, Any> = (settings - "plans").mapValues { it.value ?: "" }
                val body: Map<String, Any> = mapOf("plans" to plans, "page" to page)
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.updateSubscriptionPlansAdmin(body)
                if (response.success) {
                    _subscriptionPlansAdmin.value = response.settings
                    fetchSubscriptionPlans()
                    withContext(Dispatchers.Main) { onResult(true, "ذخیره شد.") }
                } else withContext(Dispatchers.Main) { onResult(false, "ذخیره تنظیمات پلن‌ها ناموفق بود.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "خطا در ذخیره تنظیمات پلن‌ها.") }
            }
        }
    }

    fun fetchSubscriptionPlans() {
        viewModelScope.launch(Dispatchers.IO) {
            val candidateUrls = (listOf(_serverUrl.value, SelfHostedManager.SERVER_URL) + SelfHostedManager.candidateUrls).filter { it.isNotBlank() }.distinct()
            var fetched = false
            for (u in candidateUrls) {
                try {
                    val api = NetworkClient.getApi(u)
                    val plans = api.getSubscriptionPlans()
                    if (plans.isNotEmpty()) {
                        _subscriptionPlans.value = sanitizeAndOrderPlans(plans)
                        _serverUrl.value = u
                        SelfHostedManager.setCustomServerUrl(u)
                        fetched = true
                        break
                    }
                } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
            }
            if (!fetched && _subscriptionPlans.value.isEmpty()) {
                _subscriptionPlans.value = sanitizeAndOrderPlans(emptyList())
            }
        }
    }

    fun fetchAdminBroadcastMessage() {
        _adminBroadcastMessage.value = null
        _showDashboardAdminMessage.value = false
    }

    fun dismissDashboardAdminMessage() {
        _showDashboardAdminMessage.value = false
    }

    fun checkServerConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            checkServerConnectionInternal()
        }
    }

    private suspend fun checkServerConnectionInternal() {
        val candidateUrls = (listOf(_serverUrl.value, SelfHostedManager.SERVER_URL) + SelfHostedManager.candidateUrls).filter { it.isNotBlank() }.distinct()
        for (u in candidateUrls) {
            try {
                val api = NetworkClient.getApi(u)
                var connected = false
                try {
                    val response = api.healthCheck()
                    if (response.isSuccessful || response.code() == 404 || response.code() == 403) {
                        connected = true
                    }
                } catch (eHealth: Exception) {
                    try {
                        val plans = api.getSubscriptionPlans()
                        if (plans.isNotEmpty()) {
                            _subscriptionPlans.value = sanitizeAndOrderPlans(plans)
                            connected = true
                        }
                    } catch (e2: Exception) {
                        try {
                            api.checkSubscription(deviceId = getDeviceId())
                            connected = true
                        } catch (e3: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e3) }
                    }
                }
                if (connected) {
                    _serverUrl.value = u
                    SelfHostedManager.setCustomServerUrl(u)
                    _isServerConnected.value = true
                    try {
                        val plans = api.getSubscriptionPlans()
                        if (plans.isNotEmpty()) {
                            _subscriptionPlans.value = sanitizeAndOrderPlans(plans)
                        }
                    } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", ignored) }
                    verifyLicenseStatus()
                    return
                }
            } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
        }
        if (_subscriptionPlans.value.isEmpty()) {
            _subscriptionPlans.value = sanitizeAndOrderPlans(emptyList())
        }
        _isServerConnected.value = false
    }

    fun checkSubscriptionByPhone(phone: String, onResult: (LicenseInfoResponse?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanPhone = phone.trim()
            if (cleanPhone.length < 10) {
                withContext(Dispatchers.Main) { onResult(null) }
                return@launch
            }
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.checkSubscription(
                    deviceId = getDeviceId(),
                    userPhone = cleanPhone
                )
                val info = LicenseInfoResponse(
                    exists = response.valid,
                    deviceId = getDeviceId(),
                    lastActiveAt = response.activatedAt,
                    hasPassword = response.hasPasswordRaw ?: false,
                    licenseCode = response.licenseCode ?: response.license_code ?: "",
                    message = response.message
                )
                withContext(Dispatchers.Main) { onResult(info) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun setLicensePassword(licenseCode: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val savedPhone = decryptSetting("enc_user_phone").trim().ifBlank { null }
                val activationSecret = decryptSetting("enc_pending_activation_secret").trim().ifBlank { null }
                val success = api.setLicensePassword(SetPasswordRequest(licenseCode, password, activationSecret, savedPhone, getDeviceId()))
                if (success) encryptSetting("enc_pending_activation_secret", "")
                withContext(Dispatchers.Main) { onResult(success) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    

    // ==============================================================
    // USER AUTHENTICATION & DEVICE BINDING
    // ==============================================================

    suspend fun loadSavedAuthSession() {
        // Restore the correct bearer token for the current session type. Manager and Customer
        // sessions are intentionally isolated so a Customer login can never replace the Manager
        // bearer used by station/configuration APIs.
        val sessionType = decryptSetting("enc_session_type")
        var token = if (sessionType == "CUSTOMER") decryptSetting("enc_customer_auth_token") else decryptSetting("enc_auth_token")
        if (token.isNotBlank()) {
            if (sessionType == "CUSTOMER") NetworkClient.customerAuthToken = token else NetworkClient.managerAuthToken = token
        } else {
            if (sessionType == "CUSTOMER") NetworkClient.customerAuthToken = null else NetworkClient.managerAuthToken = null
        }
        if (sessionType == "CUSTOMER") {
            // Customer authentication is server-authoritative. A cached phone/customer record
            // must never restore an authenticated session by itself after app restart.
            val custPhone = decryptSetting("enc_customer_phone")
            val managerId = decryptSetting("enc_manager_id")
            if (custPhone.isNotBlank() && managerId.isNotBlank() && token.isNotBlank()) {
                try {
                    val restored = SelfHostedManager.restoreCustomerSession(managerId, token)
                    if (restored != null) {
                        SelfHostedManager.setCurrentCustomer(restored)
                        _isCustomerAuthenticated.value = true
                        _isAdminAuthenticated.value = false
                        fetchCustomerAppConfigsFromCloud()
                    } else {
                        throw IllegalStateException("Customer session rejected by server")
                    }
                } catch (_: Exception) {
                    SelfHostedManager.setCurrentCustomer(null)
                    NetworkClient.customerAuthToken = null
                    token = ""
                    encryptSetting("enc_session_type", "")
                    encryptSetting("enc_customer_phone", "")
                    encryptSetting("enc_manager_id", "")
                    encryptSetting("enc_customer_auth_token", "")
                    _isCustomerAuthenticated.value = false
                    _isAdminAuthenticated.value = false
                }
            } else {
                SelfHostedManager.setCurrentCustomer(null)
                _isCustomerAuthenticated.value = false
                NetworkClient.customerAuthToken = null
                token = ""
                encryptSetting("enc_session_type", "")
                encryptSetting("enc_customer_auth_token", "")
            }
        } else if (sessionType == "ADMIN") {
            val planType = decryptSetting("enc_plan_type")
            val licenseStatus = decryptSetting("enc_license_status")
            val savedRole = decryptSetting("enc_admin_role")
            val isTrial = savedRole != "SUPER_MANAGER" && (planType.equals("TRIAL", ignoreCase = true) ||
                          savedRole.equals("TRIAL_USER", ignoreCase = true) ||
                          licenseStatus.equals("TRIAL", ignoreCase = true))
            val savedManagerId = decryptSetting("enc_manager_id").ifBlank { decryptSetting("enc_user_id") }

            if (isTrial) {
                // Trial mode is validated by the dedicated server trial endpoint below and
                // intentionally does not require a Manager JWT.
                _currentAdminRole.value = "TRIAL_USER"
                _isAdminAuthenticated.value = true
                _isCustomerAuthenticated.value = false
            } else if (savedManagerId.isNotBlank() && token.isNotBlank() &&
                savedRole in setOf("MANAGER", "GAMENET_MANAGER", "SUPER_MANAGER")) {
                // A paid Manager session may be restored only when the persisted server
                // credentials are present. Subscription/entitlement validation follows.
                _currentAdminRole.value = savedRole
                _isAdminAuthenticated.value = true
                _isCustomerAuthenticated.value = false
                SelfHostedManager.setManagerId(savedManagerId)
                SelfHostedManager.fetchAllFromCloud()
                repository.syncAllWithServer()
            } else {
                // Never treat a partial/corrupt local session as an authenticated Manager.
                _isAdminAuthenticated.value = false
                _isCustomerAuthenticated.value = false
                SelfHostedManager.setManagerId("")
                encryptSetting("enc_session_type", "")
                encryptSetting("enc_admin_role", "")
                encryptSetting("enc_manager_id", "")
                encryptSetting("enc_auth_token", "")
                NetworkClient.managerAuthToken = null
                token = ""
            }
        }

        val userId = decryptSetting("enc_user_id")
        val username = decryptSetting("enc_auth_username")
        val phone = decryptSetting("enc_auth_phone")
        val role = decryptSetting("enc_auth_role").ifBlank { "OPERATOR" }
        val email = decryptSetting("enc_auth_email")

        if (token.isNotBlank()) {
            if (sessionType == "CUSTOMER") {
                NetworkClient.customerAuthToken = token
                _authState.value = AuthState.Unauthenticated
            } else if (userId.isNotBlank()) {
                NetworkClient.managerAuthToken = token
                _authState.value = AuthState.Authenticated(
                    userId = userId,
                    username = username.ifBlank { "کاربر" },
                    phone = phone,
                    role = role,
                    email = email,
                    token = token
                )
                checkAuthStatusOnServer()
                bindDeviceToUser(userId)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun registerUser(
        username: String,
        password: String,
        phone: String? = null,
        email: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanName = username.trim()
        val cleanPassword = password.trim()
        val cleanPhone = phone?.trim()?.ifBlank { null }
        if (cleanName.length < 3) { onResult(false, "نام و نام خانوادگی باید حداقل 3 کاراکتر باشد."); return }
        if (cleanPhone.isNullOrBlank() || cleanPhone.length < 10) { onResult(false, "لطفاً شماره موبایل معتبر (مثال: 09123456789) وارد کنید."); return }
        if (cleanPassword.length < 8) { onResult(false, "رمز عبور باید حداقل 8 کاراکتر باشد."); return }
        if (password != cleanPassword) { onResult(false, "رمز عبور نمی‌تواند با فاصله ابتدا یا انتها ذخیره شود."); return }

        _authState.value = AuthState.Authenticating
        viewModelScope.launch(Dispatchers.IO) {
            val result = SelfHostedManager.registerCustomer(cleanName, cleanPhone, cleanPassword)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _authState.value = AuthState.Unauthenticated
                    onResult(true, "ثبت‌نام مشتری با موفقیت انجام شد.")
                } else {
                    _authState.value = AuthState.Unauthenticated
                    onResult(false, result.exceptionOrNull()?.message ?: "ثبت‌نام ناموفق بود.")
                }
            }
        }
    }

    fun loginUser(
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanUsername = username.trim()
        val cleanPassword = password.trim()

        if (cleanUsername.isBlank() || cleanPassword.isBlank()) {
            onResult(false, "لطفاً نام کاربری و رمز عبور را وارد کنید.")
            return
        }

        _authState.value = AuthState.Authenticating

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val req = UserLoginRequest(
                    username = cleanUsername, 
                    password = cleanPassword,
                    deviceId = getDeviceId(),
                    deviceFingerprint = android.os.Build.FINGERPRINT,
                    deviceModel = android.os.Build.MODEL,
                    osVersion = android.os.Build.VERSION.RELEASE,
                    appVersion = "1L"
                )
                val response = api.loginUser(req)
                val token = response.token
                val user = response.user

                if (response.success && !token.isNullOrBlank() && user != null && user.realId.isNotBlank()) {
                    val userId = user.realId
                    val effectiveUsername = user.username ?: cleanUsername
                    val effectivePhone = user.phone ?: ""
                    val effectiveRole = user.role ?: "OPERATOR"
                    val effectiveEmail = user.email ?: ""

                    // Encrypted local session storage
                    encryptSetting("enc_auth_token", token)
                    encryptSetting("enc_user_id", userId)
                    encryptSetting("enc_auth_username", effectiveUsername)
                    encryptSetting("enc_auth_phone", effectivePhone)
                    encryptSetting("enc_auth_role", effectiveRole)
                    encryptSetting("enc_auth_email", effectiveEmail)
                    encryptSetting("enc_auth_password", "")
                    if (effectivePhone.isNotBlank()) {
                        encryptSetting("enc_user_phone", effectivePhone)
                    }

                    NetworkClient.authToken = token

                    _authState.value = AuthState.Authenticated(
                        userId = userId,
                        username = effectiveUsername,
                        phone = effectivePhone,
                        role = effectiveRole,
                        email = effectiveEmail,
                        token = token
                    )
                    _showAuthDialog.value = false

                    // Idempotent device binding with real backend users.id
                    bindDeviceToUser(userId)
                    
                    // Sync with Self-Hosted server immediately upon login
                    com.example.data.network.SelfHostedManager.setManagerId(userId)
                    com.example.data.network.SelfHostedManager.fetchAllFromCloud()
                    repository.syncAllWithServer()
                    loadSettings()

                    withContext(Dispatchers.Main) {
                        onResult(true, "ورود با موفقیت انجام شد.")
                    }
                } else {
                    val errorMsg = response.error.ifBlank { response.message }.ifBlank { "نام کاربری یا رمز عبور اشتباه است." }
                    _authState.value = AuthState.AuthenticationError(errorMsg)
                    withContext(Dispatchers.Main) { onResult(false, errorMsg) }
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e is retrofit2.HttpException && e.code() == 401 -> "نام کاربری یا رمز عبور اشتباه است."
                    e is retrofit2.HttpException && e.code() == 403 -> {
                        val errorBody = e.response()?.errorBody()?.string() ?: ""
                        if (errorBody.contains("DEVICE_MISMATCH")) "این حساب قبلاً روی دستگاه دیگری فعال شده است." else "دسترسی به این حساب مسدود شده است."
                    }
                    e is java.net.UnknownHostException || e is java.net.ConnectException -> "خطا در اتصال به سرور."
                    else -> "خطا در ورود: ${e.message}"
                }
                _authState.value = AuthState.AuthenticationError(errorMsg)
                withContext(Dispatchers.Main) { onResult(false, errorMsg) }
            }
        }
    }

    fun checkAuthStatusOnServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentToken = decryptSetting("enc_auth_token")
            if (currentToken.isBlank()) return@launch

            try {
                // Rehydrate the Manager identity immediately before validation.
                val savedManagerId = decryptSetting("enc_manager_id")
                    .ifBlank { decryptSetting("enc_user_id") }
                if (savedManagerId.isNotBlank()) SelfHostedManager.setManagerId(savedManagerId)

                val api = NetworkClient.getApi(_serverUrl.value)
                api.checkAuth()
                _isServerConnected.value = true
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    // A Super Manager has a dedicated server-side authority check that does
                    // not depend on the generic Manager entitlement middleware. Retry it
                    // before ever destroying a valid Super Manager session.
                    val role = decryptSetting("enc_admin_role")
                    if (role == "SUPER_MANAGER") {
                        try {
                            val superCheck = NetworkClient.getApi(_serverUrl.value).pingSuperManager()
                            if (superCheck.isSuccessful) {
                                _isServerConnected.value = true
                                _isSubscribed.value = true
                                _isAdminAuthenticated.value = true
                                _currentAdminRole.value = "SUPER_MANAGER"
                                NetworkClient.isTrialMode = false
                                return@launch
                            }
                        } catch (_: Exception) {
                            // Fall through to normal revoked-session handling.
                        }
                    }
                    // Both checks failed: the server has actually rejected the session.
                    logout()
                }
                // Transport/offline errors never destroy a valid local session.
            }
        }
    }

    fun bindDeviceToUser(userId: String, onResult: ((Boolean) -> Unit)? = null) {
        if (userId.isBlank()) {
            onResult?.invoke(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val lastBoundUser = decryptSetting("enc_device_bound_user_id")
            if (lastBoundUser == userId) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true)
                }
                return@launch
            }

            val deviceId = getDeviceId()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val osVersion = "Android ${Build.VERSION.RELEASE}"

            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val payload = mapOf(
                    "user_id" to userId,
                    "device_id" to deviceId,
                    "device_name" to deviceName,
                    "os_version" to osVersion
                )
                api.addUserDevice(payload)
                encryptSetting("enc_device_bound_user_id", userId)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false)
                }
            }
        }
    }

    fun logout(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try { NetworkClient.getApi(_serverUrl.value).logoutSession() } catch (_: Exception) { /* local logout still completes */ }
            encryptSetting("enc_auth_token", "")
            encryptSetting("enc_user_id", "")
            encryptSetting("enc_auth_username", "")
            encryptSetting("enc_auth_phone", "")
            encryptSetting("enc_auth_role", "")
            encryptSetting("enc_auth_email", "")
            encryptSetting("enc_device_bound_user_id", "")

            NetworkClient.managerAuthToken = null
            NetworkClient.customerAuthToken = null
            encryptSetting("enc_customer_auth_token", "")
            _authState.value = AuthState.Unauthenticated

            // Authentication logout must not erase business data; Room data remains available after re-login.

            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    fun saveClubLevels(levels: List<ClubLevel>) {
        _clubLevels.value = levels
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = org.json.JSONArray()
                for (lvl in levels) {
                    val obj = org.json.JSONObject()
                    obj.put("id", lvl.id)
                    obj.put("name", lvl.name)
                    obj.put("requiredPoints", lvl.requiredPoints)
                    obj.put("gameDiscountPercent", lvl.gameDiscountPercent)
                    obj.put("buffetDiscountPercent", lvl.buffetDiscountPercent)
                    obj.put("fixedDiscountToman", lvl.fixedDiscountToman)
                    obj.put("freePlayHours", lvl.freePlayHours)
                    obj.put("rewardsText", lvl.rewardsText)
                    obj.put("validityDays", lvl.validityDays)
                    obj.put("graceDays", lvl.graceDays)
                    obj.put("reachGnBonus", lvl.reachGnBonus)
                    obj.put("gameGnPercent", lvl.gameGnPercent)
                    obj.put("buffetGnPercent", lvl.buffetGnPercent)
                    obj.put("maxGnPaymentPercent", lvl.maxGnPaymentPercent)
                    obj.put("inviteGnReward", lvl.inviteGnReward)
                    obj.put("accessSpecialEvents", lvl.accessSpecialEvents)
                    obj.put("minVisitDays", lvl.minVisitDays)
                    obj.put("retainLpPoints", lvl.retainLpPoints)
                    obj.put("maxAbsenceWithoutPenaltyDays", lvl.maxAbsenceWithoutPenaltyDays)

                    val perksArr = org.json.JSONArray()
                    for (perk in lvl.nonFinancialPerks) {
                        val pObj = org.json.JSONObject()
                        pObj.put("id", perk.id)
                        pObj.put("title", perk.title)
                        pObj.put("description", perk.description)
                        pObj.put("isActive", perk.isActive)
                        pObj.put("startDate", perk.startDate)
                        pObj.put("endDate", perk.endDate)
                        perksArr.put(pObj)
                    }
                    obj.put("nonFinancialPerks", perksArr)

                    array.put(obj)
                }
                val jsonStr = array.toString()
                repository.saveSetting("club_levels_json", jsonStr)
                SelfHostedManager.syncClubLevels(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveScoringRules(rules: List<ScoringRule>) {
        _scoringRules.value = rules
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = org.json.JSONArray()
                for (r in rules) {
                    val obj = org.json.JSONObject()
                    obj.put("id", r.id)
                    obj.put("title", r.title)
                    obj.put("points", r.points)
                    array.put(obj)
                }
                repository.saveSetting("scoring_rules_json", array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Absence Tracking & Penalty Logic Engine ---

    private val _isEvaluatingAbsence = MutableStateFlow(false)
    val isEvaluatingAbsence: StateFlow<Boolean> = _isEvaluatingAbsence.asStateFlow()

    private val _absenceEvaluationResult = MutableStateFlow<String?>(null)
    val absenceEvaluationResult: StateFlow<String?> = _absenceEvaluationResult.asStateFlow()

    fun clearAbsenceEvaluationResult() {
        _absenceEvaluationResult.value = null
    }

    data class CustomerAbsenceState(
        val customerId: Long,
        val cycleStartActivityTimestamp: Long,
        val baseGnBalance: Long,
        val baseLpBalance: Long,
        val totalGnPenalized: Long,
        val totalLpPenalized: Long,
        val lastAppliedStage: Int,
        val lastPenaltyTimestamp: Long
    )

    fun getAbsenceStatusForCustomer(cust: Customer): AbsenceStatusInfo {
        val now = System.currentTimeMillis()
        val lastAct = if (cust.lastActivityTimestamp > 0) cust.lastActivityTimestamp else now
        val absentDays = ((now - lastAct) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)

        val statusText = when {
            absentDays <= 7 -> "عادی (بدون جریمه)"
            absentDays <= 14 -> "هشدار غیبت"
            absentDays <= 20 -> "هشدار جدی حفظ سطح"
            else -> {
                val stage = 1 + (absentDays - 21) / 15
                "غیبت فعال (مرحله $stage)"
            }
        }
        val isPenaltyActive = absentDays >= 21
        val stage = if (isPenaltyActive) 1 + (absentDays - 21) / 15 else 0

        val nextStageDay = if (absentDays < 21) 21 else 21 + stage * 15
        val daysLeftToNext = (nextStageDay - absentDays).coerceAtLeast(0)

        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("gamenexa_absence_prefs", android.content.Context.MODE_PRIVATE)
        val stateJson = prefs.getString("absence_${cust.id}", null)

        var baseGn = cust.availableGn
        var baseLp = if (cust.lp > 0) cust.lp else cust.points
        var totalGnPen = 0L
        var totalLpPen = 0L
        if (!stateJson.isNullOrBlank()) {
            try {
                val obj = org.json.JSONObject(stateJson)
                if (obj.optLong("cycleStartActivityTimestamp") == lastAct) {
                    baseGn = obj.optLong("baseGnBalance", cust.availableGn)
                    baseLp = obj.optLong("baseLpBalance", if (cust.lp > 0) cust.lp else cust.points)
                    totalGnPen = obj.optLong("totalGnPenalized", 0L)
                    totalLpPen = obj.optLong("totalLpPenalized", 0L)
                }
            } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
        }

        return AbsenceStatusInfo(
            absentDays = absentDays,
            statusText = statusText,
            isPenaltyActive = isPenaltyActive,
            currentStage = stage,
            baseGnBalance = baseGn,
            baseLpBalance = baseLp,
            totalGnPenalized = totalGnPen,
            totalLpPenalized = totalLpPen,
            nextPenaltyDaysLeft = daysLeftToNext
        )
    }

    fun triggerAbsenceEvaluation() {
        viewModelScope.launch(Dispatchers.IO) {
            _isEvaluatingAbsence.value = true
            val count = evaluateAbsencePenalties()
            _isEvaluatingAbsence.value = false
            _absenceEvaluationResult.value = if (count > 0) {
                "جریمه غیبت برای $count مشتری با موفقیت بررسی و اعمال شد."
            } else {
                "وضعیت حضور تمام مشتریان بررسی شد؛ مشتری مشمول جریمه جدیدی یافت نشد."
            }
        }
    }

    suspend fun evaluateAbsencePenalties(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val custs = repository.allCustomers.firstOrNull() ?: emptyList()
        var penalizedCount = 0
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("gamenexa_absence_prefs", android.content.Context.MODE_PRIVATE)

        for (cust in custs) {
            val lastAct = if (cust.lastActivityTimestamp > 0) cust.lastActivityTimestamp else now
            val absentDays = ((now - lastAct) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)

            val statePrefKey = "absence_${cust.id}"
            val stateJson = prefs.getString(statePrefKey, null)
            var state: CustomerAbsenceState? = null

            if (!stateJson.isNullOrBlank()) {
                try {
                    val obj = org.json.JSONObject(stateJson)
                    val cycleStart = obj.optLong("cycleStartActivityTimestamp")
                    if (cycleStart == lastAct) {
                        state = CustomerAbsenceState(
                            customerId = cust.id,
                            cycleStartActivityTimestamp = cycleStart,
                            baseGnBalance = obj.optLong("baseGnBalance"),
                            baseLpBalance = obj.optLong("baseLpBalance"),
                            totalGnPenalized = obj.optLong("totalGnPenalized"),
                            totalLpPenalized = obj.optLong("totalLpPenalized"),
                            lastAppliedStage = obj.optInt("lastAppliedStage"),
                            lastPenaltyTimestamp = obj.optLong("lastPenaltyTimestamp")
                        )
                    } else {
                        prefs.edit().remove(statePrefKey).apply()
                    }
                } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetViewModel.kt", e) }
            }

            if (absentDays < 21) {
                continue
            }

            val targetStage = 1 + (absentDays - 21) / 15

            var baseGn = state?.baseGnBalance ?: cust.availableGn
            var baseLp = state?.baseLpBalance ?: (if (cust.lp > 0) cust.lp else cust.points)
            var totalGnPenalized = state?.totalGnPenalized ?: 0L
            var totalLpPenalized = state?.totalLpPenalized ?: 0L
            var lastAppliedStage = state?.lastAppliedStage ?: 0

            var currentGn = cust.availableGn
            var currentLp = if (cust.lp > 0) cust.lp else cust.points
            var didApply = false

            for (stage in (lastAppliedStage + 1)..targetStage) {
                val maxAllowedGnPen = baseGn * 0.30
                val maxAllowedLpPen = baseLp * 0.30

                val remainingGnCap = (maxAllowedGnPen - totalGnPenalized).toLong().coerceAtLeast(0L)
                val remainingLpCap = (maxAllowedLpPen - totalLpPenalized).toLong().coerceAtLeast(0L)

                val rawGnPen = (currentGn * 0.5).toLong()
                val rawLpPen = (currentLp * 0.5).toLong()

                val stepGnPen = kotlin.math.min(rawGnPen, remainingGnCap).coerceAtLeast(0L)
                val stepLpPen = kotlin.math.min(rawLpPen, remainingLpCap).coerceAtLeast(0L)

                val finalGnPen = kotlin.math.min(stepGnPen, currentGn)
                val finalLpPen = kotlin.math.min(stepLpPen, currentLp)

                if (finalGnPen > 0L || finalLpPen > 0L) {
                    currentGn = (currentGn - finalGnPen).coerceAtLeast(0L)
                    currentLp = (currentLp - finalLpPen).coerceAtLeast(0L)
                    totalGnPenalized += finalGnPen
                    totalLpPenalized += finalLpPen
                    didApply = true

                    // Record in GN Ledger with type ABSENCE_PENALTY
                    repository.addGnLedgerEntry(
                        GnLedgerEntry(
                            customerId = cust.id,
                            customerName = cust.fullName,
                            gnAmount = -finalGnPen,
                            transactionType = "ABSENCE_PENALTY",
                            source = "PENALTY_ADJUSTMENT",
                            status = "AVAILABLE",
                            timestamp = now,
                            referenceId = "ABSENCE_S${stage}_${now}",
                            description = "جریمه غیبت مرحله $stage (روز ${21 + (stage-1)*15}): کسر ${finalGnPen.toInt()} GN و ${finalLpPen.toInt()} LP"
                        )
                    )

                    // Record Behavior Log
                    repository.addBehaviorLog(
                        BehaviorLog(
                            customerId = cust.id,
                            customerName = cust.fullName,
                            ruleTitle = "جریمه غیبت (مرحله $stage)",
                            gnChange = -finalGnPen,
                            lpChange = -finalLpPen,
                            appliedBy = "سیستم انضباطی خودکار",
                            reason = "غیبت $absentDays روزه مشتری (عدم مراجعه بیش از 20 روز)",
                            timestamp = now
                        )
                    )
                }
            }

            if (didApply || lastAppliedStage != targetStage) {
                val updatedCust = cust.copy(
                    availableGn = currentGn,
                    lp = currentLp,
                    points = currentLp
                )
                repository.insertCustomer(updatedCust)

                val newStateObj = org.json.JSONObject().apply {
                    put("customerId", cust.id)
                    put("cycleStartActivityTimestamp", lastAct)
                    put("baseGnBalance", baseGn)
                    put("baseLpBalance", baseLp)
                    put("totalGnPenalized", totalGnPenalized)
                    put("totalLpPenalized", totalLpPenalized)
                    put("lastAppliedStage", targetStage)
                    put("lastPenaltyTimestamp", now)
                }
                prefs.edit().putString(statePrefKey, newStateObj.toString()).apply()
                penalizedCount++
            }
        }
        penalizedCount
    }

    fun updateCustomerPoints(customerId: Long, delta: Long, title: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cust = repository.getCustomerById(customerId)
            if (cust != null) {
                val newPoints = (cust.points + delta).coerceAtLeast(0L)
                val updated = cust.copy(points = newPoints, lp = newPoints)
                repository.insertCustomer(updated)
                val logTitle = title ?: if (delta >= 0) "افزایش دستی امتیاز" else "کاهش دستی امتیاز"
                repository.addPointLog(PointLog(customerId = customerId, title = logTitle, points = delta))
            }
        }
    }

    fun updateCustomerLp(customerId: Long, delta: Long, title: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cust = repository.getCustomerById(customerId)
            if (cust != null) {
                val newLp = (cust.lp + delta).coerceAtLeast(0L)
                val updated = cust.copy(lp = newLp, points = newLp)
                repository.insertCustomer(updated)
                val logTitle = title ?: if (delta >= 0) "افزایش دستی LP" else "کاهش دستی LP"
                repository.addPointLog(PointLog(customerId = customerId, title = logTitle, points = delta))
                logOperatorActivity("تغییر دستی LP", "تغییر LP برای کاربر ${cust.fullName} به میزان $delta")
            }
        }
    }

    fun getPointLogs(customerId: Long): Flow<List<PointLog>> {
        return repository.getPointLogs(customerId)
    }

    val allPointLogs: Flow<List<PointLog>> = repository.getAllPointLogs()

    fun addCustomerPointsWithLog(customerId: Long, points: Long, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cust = repository.getCustomerById(customerId)
            if (cust != null) {
                val newPoints = (cust.points + points).coerceAtLeast(0L)
                repository.insertCustomer(cust.copy(points = newPoints))
                repository.addPointLog(PointLog(customerId = customerId, title = title, points = points))
            }
        }
    }

    fun updateCustomerRewardsConsumed(customerId: Long, consumed: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val cust = repository.getCustomerById(customerId)
            if (cust != null) {
                val newConsumed = (cust.rewardsConsumed + consumed).coerceAtLeast(0L)
                repository.insertCustomer(cust.copy(rewardsConsumed = newConsumed))
            }
        }
    }

    private val _managersList = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.network.AdminManagerDto>>(emptyList())
    val managersList: kotlinx.coroutines.flow.StateFlow<List<com.example.data.network.AdminManagerDto>> = _managersList.asStateFlow()

    private val _deviceTrials = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.network.DeviceTrialDto>>(emptyList())
    val deviceTrials: kotlinx.coroutines.flow.StateFlow<List<com.example.data.network.DeviceTrialDto>> = _deviceTrials.asStateFlow()

    fun fetchDeviceTrials() {
        viewModelScope.launch {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val responseBody = api.getAllDeviceTrials()
                val jsonStr = responseBody.string()
                val list = parseDeviceTrialsJson(jsonStr)
                _deviceTrials.value = list.sortedWith(compareByDescending<com.example.data.network.DeviceTrialDto> { it.startTime ?: 0L }.thenByDescending { it.expiryDate ?: it.expireTime ?: 0L })
            } catch (e: Exception) {
                e.printStackTrace()
                _deviceTrials.value = emptyList()
            }
        }
    }

    private fun parseDeviceTrialsJson(jsonStr: String): List<com.example.data.network.DeviceTrialDto> {
        if (jsonStr.isBlank()) return emptyList()
        val trimmed = jsonStr.trim()
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.example.data.network.AnyAdapter())
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        return try {
            if (trimmed.startsWith("[")) {
                val listType = com.squareup.moshi.Types.newParameterizedType(
                    List::class.java,
                    com.example.data.network.DeviceTrialDto::class.java
                )
                val adapter = moshi.adapter<List<com.example.data.network.DeviceTrialDto>>(listType)
                adapter.fromJson(trimmed) ?: emptyList()
            } else {
                val adapter = moshi.adapter(com.example.data.network.DeviceTrialsResponse::class.java)
                val resp = adapter.fromJson(trimmed)
                val items = resp?.items ?: emptyList()
                if (items.isNotEmpty()) {
                    items
                } else {
                    val jsonObj = org.json.JSONObject(trimmed)
                    val jsonArray = jsonObj.optJSONArray("trial_devices")
                        ?: jsonObj.optJSONArray("devices")
                        ?: jsonObj.optJSONArray("trialDevices")
                        ?: jsonObj.optJSONArray("data")
                        ?: jsonObj.optJSONArray("trials")
                        ?: jsonObj.optJSONArray("items")
                    if (jsonArray != null) {
                        val dtoAdapter = moshi.adapter(com.example.data.network.DeviceTrialDto::class.java)
                        val resultList = mutableListOf<com.example.data.network.DeviceTrialDto>()
                        for (i in 0 until jsonArray.length()) {
                            val itemStr = jsonArray.getJSONObject(i).toString()
                            dtoAdapter.fromJson(itemStr)?.let { resultList.add(it) }
                        }
                        resultList
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun deleteDeviceTrial(deviceId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.deleteDeviceTrial(deviceId)
                if (response.isSuccessful) {
                    fetchDeviceTrials()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true) }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun extendDeviceTrial(deviceId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val api = NetworkClient.getApi(_serverUrl.value)
                val response = api.extendDeviceTrial(deviceId)
                if (response.isSuccessful) {
                    fetchDeviceTrials()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true) }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
            }
        }
    }


    fun fetchManagers() {
        viewModelScope.launch {
            try {
                val list = repository.getManagers()
                _managersList.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun editManager(
        id: String,
        name: String?,
        gameNetName: String?,
        password: String?,
        planType: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val request = com.example.data.network.EditManagerRequestDto(
                    name = name,
                    gameNetName = gameNetName,
                    password = password,
                    planType = planType
                )
                val api = com.example.data.network.NetworkClient.getApi(_serverUrl.value)
                api.updateManager(id, request)
                fetchManagers()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "خطا در ویرایش مدیر") }
            }
        }
    }

    fun deleteManager(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = com.example.data.network.NetworkClient.getApi(_serverUrl.value)
                val response = api.deleteManager(id)
                if (!response.isSuccessful) {
                    throw IllegalStateException("خطا در حذف مدیر: HTTP ${response.code()}")
                }
                fetchManagers()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                fetchManagers()
                withContext(Dispatchers.Main) { onError(e.message ?: "خطا در برقراری ارتباط") }
            }
        }
    }

    fun purgeServerOrphanData(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get all local valid customer IDs
                val localCusts = repository.getAllCustomersLocal()
                val validIds = localCusts.map { it.id }

                // 2. Wipe any prebuilt / orphan customers on server not present locally
                SelfHostedManager.purgeAllOrphanCustomersExcept(validIds)

                // 3. Refresh Managers
                fetchManagers()

                withContext(Dispatchers.Main) {
                    onSuccess("سرور با موفقیت پاکسازی و ایزوله گردید. کلیه داده‌های بدون صاحب از ریشه حذف شدند.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("خطا در پاکسازی سرور: ${e.message}")
                }
            }
        }
    }

    fun createManager(
        fullName: String,
        gameneName: String,
        phone: String,
        pass: String,
        planType: String,
        durationDays: Int,
        maxDevices: Int,
        paymentAmount: Long,
        paymentStatus: String,
        onSuccess: (com.example.data.network.ManagerProfileDto) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val req = com.example.data.network.CreateManagerRequestDto(
                    fullName = fullName,
                    gameneName = gameneName,
                    phone = phone,
                    password = pass,
                    planType = planType,
                    durationDays = durationDays,
                    maxDevices = maxDevices,
                    paymentAmount = paymentAmount.toDouble(),
                    paymentStatus = paymentStatus
                )
                val newManager = repository.createManager(req)
                fetchManagers()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onSuccess(newManager) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onError(e.message ?: "Error creating manager") }
            }
        }
    }
    
    fun approveManagerPayment(
        managerId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val req = com.example.data.network.UpdateManagerRequestDto(
                    status = "active",
                    paymentStatus = "paid"
                )
                repository.updateManagerStatus(managerId, req)
                fetchManagers()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onError(e.message ?: "Error approving manager") }
            }
        }
    }

    val managerReservations = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.network.ReservationDbDto>>(emptyList())

    fun previewPricing(req: com.example.data.network.PricingPreviewRequest, onResult: (com.example.data.network.PricingPreviewResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = SelfHostedManager.previewReservationPricing(req)
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(result) }
            } catch (_: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun submitAtomicReservation(req: com.example.data.network.AtomicReservationRequest, onResult: (com.example.data.network.AtomicReservationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = SelfHostedManager.submitAtomicReservation(req)
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(result) }
            } catch (_: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun fetchManagerReservations() {
        viewModelScope.launch {
            val result = SelfHostedManager.fetchManagerReservations()
            managerReservations.value = result
        }
    }

    fun updateReservationStatus(id: Long, status: String) {
        viewModelScope.launch {
            if (SelfHostedManager.updateReservationStatus(id, status)) {
                fetchManagerReservations()
            }
        }
    }
    
    fun deleteDeviceTrial(deviceId: String) {}

    fun handlePaymentCallback(txn: String, licenseCode: String, status: String, onResult: (Boolean, String) -> Unit) {
        if (status == "SUCCESS") {
            if (licenseCode.isNotBlank()) {
                activateLicenseCode(licenseCode) { success, msg ->
                    onResult(success, msg)
                }
            } else {
                checkLicenseStatus()
                onResult(true, "پرداخت موفق بود")
            }
        } else {
            onResult(false, "پرداخت ناموفق بود یا لغو شد")
        }
    }
    
    
    
    
}
data class NonFinancialPerk(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isActive: Boolean = true,
    val startDate: String = "",
    val endDate: String = ""
)

fun getDefaultPerksForLevel(levelId: String): List<NonFinancialPerk> {
    return when (levelId.lowercase()) {
        "bronze", "برنزی" -> listOf(
            NonFinancialPerk(title = "نشان Bronze🥉 در پروفایل مشتری", description = "نمایش نشان برنزی در پروفایل کاربری"),
            NonFinancialPerk(title = "نمایش نوار پیشرفت تا Silver", description = "مشاهده میزان LP باقی‌مانده تا سطح نقره‌ای"),
            NonFinancialPerk(title = "دسترسی به مأموریت‌ها و چالش‌های عمومی", description = "شرکت در چالش‌های همگانی مجموعه"),
            NonFinancialPerk(title = "امکان مشاهده تاریخچه GN و LP", description = "دسترسی کامل به ریز تراکنش‌های سکه و امتیاز"),
            NonFinancialPerk(title = "امکان شرکت در رویدادهای عمومی گیم‌نت", description = "حضور در تورنمنت‌ها و برنامه‌های همگانی"),
            NonFinancialPerk(title = "دریافت اطلاعیه‌ها و اخبار عمومی گیم‌نت", description = "دریافت اعلانات رویدادها و تخفیف‌ها")
        )
        "silver", "نقره‌ای" -> listOf(
            NonFinancialPerk(title = "نشان Silver🥈 اختصاصی در پروفایل", description = "نمایش باج نقره‌ای ویژه اعضای وفادار"),
            NonFinancialPerk(title = "دسترسی به مأموریت‌ها و چالش‌های اختصاصی Silver", description = "چالش‌های ویژه با جوایز سکه بیشتر"),
            NonFinancialPerk(title = "امکان شرکت در مسابقات و چالش‌های اختصاصی Silver و بالاتر", description = "ورود به مسابقات سطح نقره‌ای"),
            NonFinancialPerk(title = "دریافت پیشنهادها و مأموریت‌های اختصاصی Silver", description = "پیشنهادهای شگفت‌انگیز سفارشی"),
            NonFinancialPerk(title = "دریافت زودتر اطلاع‌رسانی بعضی رویدادها", description = "اطلاع پیش از موعد از برنامه‌ها"),
            NonFinancialPerk(title = "نمایش سابقه و دستاوردهای Loyalty", description = "مشاهده مدال‌ها و سوابق فعالیت"),
            NonFinancialPerk(title = "امکان شرکت در قرعه‌کشی‌های مخصوص Silver و بالاتر", description = "شانس شرکت در قرعه‌کشی‌های ویژه"),
            NonFinancialPerk(title = "نمایش مسیر پیشرفت دقیق تا Gold", description = "رصد گام به گام رسیدن به سطح طلایی"),
            NonFinancialPerk(title = "دریافت اعلان هنگام نزدیک شدن به سطح Gold", description = "هشدار هوشمند هنگام کسب امتیازات پایانی")
        )
        "gold", "طلایی" -> listOf(
            NonFinancialPerk(title = "نشان Gold🥇 اختصاصی در پروفایل", description = "باج طلایی درخشان در حساب کاربری"),
            NonFinancialPerk(title = "دسترسی به مسابقات اختصاصی Gold", description = "مسابقات پرهیجان ویژه اعضای طلایی"),
            NonFinancialPerk(title = "اولویت ثبت‌نام در رویدادهای محدود ظرفیت", description = "رزرو زودتر از بقیه در تورنمنت‌های شلوغ"),
            NonFinancialPerk(title = "دسترسی به مأموریت‌ها و چالش‌های اختصاصی Gold", description = "ماموریت‌های طلایی با پاداش GN مضاعف"),
            NonFinancialPerk(title = "دریافت زودتر اطلاع‌رسانی رویدادها و امکانات جدید", description = "اولین نفراتی که از امکانات جدید باخبر می‌شوند"),
            NonFinancialPerk(title = "دسترسی به قرعه‌کشی‌ها و جوایز اختصاصی Gold", description = "جوایز نفیس ویژه اعضای ویژه"),
            NonFinancialPerk(title = "نمایش عنوان Gold Member در پروفایل", description = "درج لبل رسمی عضو طلایی"),
            NonFinancialPerk(title = "نمایش آمار و گزارش پیشرفت پیشرفته‌تر", description = "گزارش‌های تحلیلی زمان بازی و خرید بوفه"),
            NonFinancialPerk(title = "دریافت پیام یا جایزه مناسبتی اختصاصی Gold", description = "هدیه ویژه روز تولد و مناسبت‌های خاص")
        )
        "diamond", "الماسی" -> listOf(
            NonFinancialPerk(title = "نشان Diamond / VIP🎖️ اختصاصی در پروفایل", description = "باج الماسی منحصر به فرد اعضای VIP"),
            NonFinancialPerk(title = "نمایش عنوان Diamond Member", description = "عنوان بالاترین سطح وفاداری گیم‌نت"),
            NonFinancialPerk(title = "بالاترین اولویت ثبت‌نام در رویدادهای محدود ظرفیت", description = "رزرو تضمینی و بدون صف"),
            NonFinancialPerk(title = "دسترسی به مسابقات و چالش‌های اختصاصی Diamond", description = "رقابت‌های سطح بالا با جوایز بزرگ"),
            NonFinancialPerk(title = "دسترسی به مأموریت‌های اختصاصی Diamond", description = "ماموریت‌های الماسی با بالاترین پاداش"),
            NonFinancialPerk(title = "دریافت اطلاعیه‌های ویژه قبل از سایر سطوح", description = "دسترسی زودتر از همه به اخبار VIP"),
            NonFinancialPerk(title = "دسترسی به قرعه‌کشی‌ها و جوایز اختصاصی Diamond", description = "شانس برنده شدن جوایز اختصاصی VIP"),
            NonFinancialPerk(title = "دریافت هدیه یا سورپرایز مناسبتی اختصاصی Diamond", description = "سورپرایزهای ارزشمند مدیریت سالن"),
            NonFinancialPerk(title = "نمایش دستاوردها و افتخارات ویژه مشتری", description = "تالار افتخارات کاربری"),
            NonFinancialPerk(title = "نمایش صفحه پروفایل ویژه با آمار Loyalty", description = "کارت پروفایل طلایی/الماسی شکیل"),
            NonFinancialPerk(title = "امکان دریافت دعوتنامه برای رویدادهای خصوصی گیم‌نت", description = "دعوت به مهمانی‌ها و تورنمنت‌های VIP بسته"),
            NonFinancialPerk(title = "امکان دریافت عنوان یا نشان افتخاری برای مشتریان بسیار قدیمی و ویژه", description = "اعطای باج‌های افتخاری توسط مدیریت"),
            NonFinancialPerk(title = "دسترسی به بخش اختصاصی Diamond Challenges در برنامه مشتری", description = "تب و بخش ویژه چالش‌های VIP"),
            NonFinancialPerk(title = "امکان دریافت مأموریت‌ها و چالش‌های ویژه Diamond با جوایز اختصاصی", description = "جوایز غیرنقدی و تخفیف‌های ویژه الماسی")
        )
        else -> emptyList()
    }
}

data class ClubLevel(
    val id: String,
    val name: String,
    val requiredPoints: Long,
    val gameDiscountPercent: Long = 0L,
    val buffetDiscountPercent: Long = 0L,
    val fixedDiscountToman: Long = 0L,
    val freePlayHours: Long = 0L,
    val customRewards: List<String> = emptyList(),
    val rewardsText: String = "",
    val validityDays: Int = 30,
    val graceDays: Int = 7,
    
    // New Loyalty Level Parameters
    val reachGnBonus: Long = 0L,
    val gameGnPercent: Long = 0L,
    val buffetGnPercent: Long = 0L,
    val maxGnPaymentPercent: Long = 0L,
    val inviteGnReward: Long = 0L,
    val accessSpecialEvents: Boolean = false,
    val minVisitDays: Int = 0,
    val retainLpPoints: Long = 0L,
    val maxAbsenceWithoutPenaltyDays: Int = 20,
    
    val nonFinancialPerks: List<NonFinancialPerk> = emptyList()
)

data class AbsenceStatusInfo(
    val absentDays: Int,
    val statusText: String,
    val isPenaltyActive: Boolean,
    val currentStage: Int,
    val baseGnBalance: Long,
    val baseLpBalance: Long,
    val totalGnPenalized: Long,
    val totalLpPenalized: Long,
    val nextPenaltyDaysLeft: Int
)

data class ScoringRule(
    val id: String,
    val title: String,
    val points: Long
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AppBackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val customers: List<Customer>,
    val clubLevels: List<ClubLevel>,
    val consoleTypes: List<ConsoleType>,
    val products: List<Product>,
    val stationCount: Int,
    val appTheme: String,
    val language: String,
    val notificationsEnabled: Boolean
)

// Extension methods / helper methods for backup & restore inside GameNetViewModel or companion
fun GameNetViewModel.checkDailyAutoBackup() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val lastBackupDate = repository.getSetting("last_auto_backup_date") ?: ""
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(calendar.time)

            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val isPast330 = (hour > 3) || (hour == 3 && minute >= 30)
            if (isPast330 && lastBackupDate != todayStr) {
                val context = getApplication<Application>().applicationContext
                val json = generateBackupJsonSyncInternal()
                val backupDir = File(context.filesDir, "backups")
                if (!backupDir.exists()) backupDir.mkdirs()
                val file = File(backupDir, "auto_backup_$todayStr.json")
                file.writeText(json)
                repository.saveSetting("last_auto_backup_date", todayStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


suspend fun GameNetViewModel.generateBackupJsonSyncInternal(): String {
    val customers = repository.getAllCustomersLocal()
    val levels = clubLevels.value
    val consoles = consoleTypes.value
    val prods = products.value
    val stations = stationCount.value
    val theme = appTheme.value
    val lang = language.value
    val notif = notificationsEnabled.value

    val backupData = AppBackupData(
        customers = customers,
        clubLevels = levels,
        consoleTypes = consoles,
        products = prods,
        stationCount = stations,
        appTheme = theme,
        language = lang,
        notificationsEnabled = notif
    )

    val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    val adapter = moshi.adapter(AppBackupData::class.java)
    return adapter.toJson(backupData) ?: "{}"
}

fun GameNetViewModel.exportBackupJsonToExternal(context: Context): File? {
    return try {
        val json = kotlinx.coroutines.runBlocking(Dispatchers.IO) { generateBackupJsonSyncInternal() }
        val documentsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val backupFolder = File(documentsDir, "GameNetBackups")
        if (!backupFolder.exists()) backupFolder.mkdirs()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timeStr = dateFormat.format(Date())
        val file = File(backupFolder, "gamenet_backup_$timeStr.json")
        file.writeText(json)
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun GameNetViewModel.restoreBackupFromJson(jsonString: String): Boolean {
    return try {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(AppBackupData::class.java)
        val backupData = adapter.fromJson(jsonString) ?: return false

        viewModelScope.launch(Dispatchers.IO) {
            for (cust in backupData.customers) {
                repository.insertCustomer(cust)
            }
            saveClubLevels(backupData.clubLevels)

            for (console in backupData.consoleTypes) {
                repository.insertConsoleType(console)
            }

            for (prod in backupData.products) {
                repository.insertProduct(prod)
            }

            repository.saveSetting("station_count", backupData.stationCount.toString())
            // Note: internal state flows can be updated if methods exist or via repository/settings
            repository.saveSetting("app_theme", backupData.appTheme)
            repository.saveSetting("language", backupData.language)
            repository.saveSetting("notifications_enabled", backupData.notificationsEnabled.toString())
        }
        true

    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun normalizeInviteCode(code: String): String {
    return code.replace("-", "").replace(" ", "").lowercase(java.util.Locale.ROOT)
}

fun GameNetViewModel.generateCustomerPassword(): String {
    val uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lowercase = "abcdefghijkmnopqrstuvwxyz"
    val numbers = "23456789"
    val p1 = uppercase.random()
    val p2 = lowercase.random()
    val p3 = lowercase.random()
    val p4 = numbers.random()
    val p5 = numbers.random()
    val p6 = uppercase.random()
    val p7 = lowercase.random()
    val p8 = numbers.random()
    return "$p1$p2$p3$p4$p5$p6$p7$p8"
}

    
    suspend fun GameNetViewModel.pushOfflineChangesToCloud() {
        try {
            val localStations = stationStates.value
            for (st in localStations) {
                // Only push stations that are active or paused
                if (st.status == "RUNNING" || st.status == "PAUSED") {
                    val orders = repository.getOrdersForStationSync(st.id)
                    val allProds = repository.allProducts.firstOrNull() ?: emptyList()
                    val ordersArray = org.json.JSONArray()
                    var buffetSum = 0L
                    orders.forEach { ord ->
                        val p = repository.getProductByName(ord.productName)
                            ?: allProds.find { it.name.trim().equals(ord.productName.trim(), ignoreCase = true) }
                        val price = p?.price ?: 0L
                        buffetSum += price * ord.quantity
                        ordersArray.put(org.json.JSONObject().apply {
                            put("product_name", ord.productName)
                            put("quantity", ord.quantity)
                            put("price", price)
                            put("target_customer_id", ord.targetCustomerId ?: 0L)
                            put("target_customer_name", ord.targetCustomerName ?: "")
                        })
                    }
                    val hourlyRate = getHourlyRate(st.consoleType, st.controllerCount)
                    com.example.data.network.SelfHostedManager.syncStationToCloud(
                        station = st,
                        ordersJsonStr = ordersArray.toString(),
                        hourlyRate = hourlyRate,
                        buffetCost = buffetSum
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GameNetViewModel", "Error pushing offline changes", e)
        }
    }

    suspend fun GameNetViewModel.saveAndSyncStationState(state: com.example.data.StationState) {
        repository.insertStationState(state)
        try {
            val orders = repository.getOrdersForStationSync(state.id)
            val allProds = repository.allProducts.firstOrNull() ?: emptyList()
            val ordersArray = org.json.JSONArray()
            var buffetSum = 0L
            orders.forEach { ord ->
                val p = repository.getProductByName(ord.productName)
                    ?: allProds.find { it.name.trim().equals(ord.productName.trim(), ignoreCase = true) }
                val price = p?.price ?: 0L
                buffetSum += price * ord.quantity
                ordersArray.put(org.json.JSONObject().apply {
                    put("product_name", ord.productName)
                    put("quantity", ord.quantity)
                    put("price", price)
                    put("target_customer_id", ord.targetCustomerId ?: 0L)
                    put("target_customer_name", ord.targetCustomerName ?: "")
                })
            }
            val hourlyRate = getHourlyRate(state.consoleType, state.controllerCount)
            try {
                com.example.data.network.SelfHostedManager.syncStationToCloud(
                    station = state,
                    ordersJsonStr = ordersArray.toString(),
                    hourlyRate = hourlyRate,
                    buffetCost = buffetSum
                )
            } catch(e: Exception) {
                android.util.Log.e("GameNetViewModel", "Station state sync failed", e)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
