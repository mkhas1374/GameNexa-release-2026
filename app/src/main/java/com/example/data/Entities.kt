package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi

private val segmentMoshi = Moshi.Builder()
    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
    .build()

data class StationSegment(
    val segmentIndex: Int,
    val consoleType: String,
    val controllerCount: Int,
    val customerIds: List<Long> = emptyList(),
    val customerNames: List<String> = emptyList(),
    val startTimeMs: Long,
    val endTimeMs: Long,
    // Kept for UI/backward compatibility; financial duration is durationSeconds.
    val durationMinutes: Int,
    val cost: Long,
    val durationSeconds: Long = 0L,
    val payerCustomerId: Long? = null,
    val payerCustomerName: String? = null,
    val payerCustomerIds: List<Long> = emptyList(),
    val payerCustomerNames: List<String> = emptyList()
)

@Entity(tableName = "station_states")
data class StationState(
    @PrimaryKey val id: Int, // Station number (1 to N)
    val status: String = "FREE", // "FREE", "RUNNING", "PAUSED"
    val controllerCount: Int = 1, // 1, 2, 3, 4
    val consoleType: String = "",
    val startTimeMillis: Long = 0L,
    val lastStateChangeTimeMillis: Long = 0L,
    val elapsedPlayingTimeMillis: Long = 0L,
    val prepaymentAmount: Long = 0L,
    val durationLimitMinutes: Int = 0,
    val selectedCustomerIdsStr: String = "",
    val selectedCustomerNamesStr: String = "",
    val segmentsJson: String = "",
    val isReportExpanded: Boolean = false,
    val customerPrepaymentsJson: String = "",
    val splitMode: String = "ALL", // "ALL", "SINGLE", "CUSTOM"
    val payerCustomerIdsStr: String = "",
    val payerCustomerNamesStr: String = ""
) {
    fun getCustomerIds(): List<Long> =
        if (selectedCustomerIdsStr.isBlank()) emptyList() else selectedCustomerIdsStr.split(",").mapNotNull { it.trim().toLongOrNull() }

    fun getCustomerNames(): List<String> =
        if (selectedCustomerNamesStr.isBlank()) emptyList() else selectedCustomerNamesStr.split(",")

    fun getStationCustomers(allCustomers: List<Customer>): List<Customer> {
        val ids = getCustomerIds()
        val names = getCustomerNames()
        if (ids.isEmpty()) return emptyList()
        return ids.mapIndexed { index, id ->
            val registered = allCustomers.find { it.id == id }
            if (registered != null) {
                registered
            } else {
                val name = names.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (id < 0) "مهمان ${-id}" else "مشتری $id"
                Customer(id = id, fullName = name, phoneNumber = "")
            }
        }
    }

    fun getPayerCustomerIds(): List<Long> =
        if (payerCustomerIdsStr.isBlank()) emptyList() else payerCustomerIdsStr.split(",").mapNotNull { it.trim().toLongOrNull() }

    fun getPayerCustomerNames(): List<String> =
        if (payerCustomerNamesStr.isBlank()) emptyList() else payerCustomerNamesStr.split(",")

    fun getEffectivePayerIds(): List<Long> {
        val payers = getPayerCustomerIds()
        return if (payers.isNotEmpty() && splitMode != "ALL") payers else getCustomerIds()
    }

    fun getCustomerPrepaymentsMap(): Map<Long, Long> {
        if (customerPrepaymentsJson.isBlank()) return emptyMap()
        return try {
            customerPrepaymentsJson.split(",").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val id = parts[0].trim().toLongOrNull()
                    val amt = parts[1].trim().toLongOrNull()
                    if (id != null && amt != null) id to amt else null
                } else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getEffectiveCustomerPrepaymentsMap(): Map<Long, Long> {
        val map = getCustomerPrepaymentsMap()
        if (map.isNotEmpty()) return map
        val custIds = getCustomerIds()
        if (custIds.isNotEmpty() && prepaymentAmount > 0L) {
            val share = prepaymentAmount / custIds.size
            return custIds.associateWith { share }
        }
        return emptyMap()
    }

    fun getSegmentsList(): List<StationSegment> {
        if (segmentsJson.isBlank()) return emptyList()
        return try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, StationSegment::class.java)
            val adapter = segmentMoshi.adapter<List<StationSegment>>(type)
            adapter.fromJson(segmentsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun List<StationSegment>.toJson(): String {
    return try {
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, StationSegment::class.java)
        val adapter = segmentMoshi.adapter<List<StationSegment>>(type)
        adapter.toJson(this)
    } catch (e: Exception) {
        ""
    }
}

@Entity(tableName = "console_types")
data class ConsoleType(
    @PrimaryKey val name: String,
    val price1: Long = 0L, // 1 Controller price per hour
    val price2: Long = 0L, // 2 Controller price per hour
    val price3: Long = 0L, // 3 Controller price per hour
    val price4: Long = 0L  // 4 Controller price per hour
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val name: String,
    val price: Long = 0L
)

@Entity(tableName = "station_orders")
data class StationOrder(
    @PrimaryKey val id: String, // format: "${stationId}_${productName}"
    val stationId: Int,
    val productName: String,
    val quantity: Int,
    val targetCustomerId: Long? = null,
    val targetCustomerName: String? = null
)

@Entity(tableName = "session_history")
data class SessionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val stationId: Int,
    val consoleType: String,
    val controllerCount: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val gameCost: Long,
    val foodCost: Long,
    val totalCost: Long,
    val dateString: String, // e.g., "YYYY-MM-DD"
    val monthString: String // e.g., "YYYY-MM"
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fullName: String,
    val phoneNumber: String,
    val password: String = "",
    val debt: Long = 0L,
    val credit: Long = 0L,
    val description: String = "",
    val points: Long = 0L,
    val availableGn: Long = 0L,
    val pendingGn: Long = 0L,
    val lp: Long = 0L,
    val tier: String = "BRONZE", // BRONZE, SILVER, GOLD, DIAMOND
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val totalQualifiedSpend: Long = 0L,
    val totalVisitsCount: Int = 0,
    val lastTierReviewTimestamp: Long = System.currentTimeMillis(),
    val inviteCode: String = "",
    val invitedByCode: String = "",
    val invitePointsAwarded: Boolean = false,
    val rewardsConsumed: Long = 0L
)

@Entity(tableName = "gn_ledger")
data class GnLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerId: Long,
    val customerName: String = "",
    val gnAmount: Long,
    val transactionType: String, // GAME_REWARD, BUFFET_REWARD, REFERRAL, BEHAVIOR_REWARD, PENALTY, SPENT, PURCHASED, TRANSFERRED, DECAY, ADMIN_ADJUSTMENT
    val source: String = "EARNED", // EARNED, PURCHASED, TRANSFERRED, REWARD, PENALTY_ADJUSTMENT
    val status: String = "AVAILABLE", // AVAILABLE, PENDING, REVERSED
    val timestamp: Long = System.currentTimeMillis(),
    val referenceId: String = "",
    val description: String = ""
)

@Entity(tableName = "behavior_rules")
data class BehaviorRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val gnChange: Long = 0L,
    val lpChange: Long = 0L,
    val severity: String = "LOW", // LOW, MEDIUM, HIGH, CRITICAL
    val cooldownMinutes: Int = 0,
    val dailyLimit: Int = 0,
    val weeklyLimit: Int = 0,
    val monthlyLimit: Int = 0,
    val requiresAdminApproval: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(tableName = "behavior_logs")
data class BehaviorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerId: Long,
    val customerName: String = "",
    val ruleTitle: String,
    val gnChange: Long = 0L,
    val lpChange: Long = 0L,
    val appliedBy: String = "مدیر سالن",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "referral_progress_records")
data class ReferralProgressRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val referrerCustomerId: Long,
    val referredCustomerId: Long,
    val referredCustomerName: String = "",
    val rewardGn: Long = 100L,
    val currentGamingSpend: Long = 0L,
    val requiredGamingSpend: Long = 100000L,
    val status: String = "PENDING", // PENDING, COMPLETED, REVERSED
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null
)

data class ReferralRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val type: String = "GAMING_SPEND", // GAMING_SPEND, BUFFET_SPEND, FIRST_VISIT
    val requiredAmount: Long = 100000L,
    val rewardGn: Long = 100L,
    val isEnabled: Boolean = true
)

@Entity(tableName = "operator_audit_logs")
data class OperatorAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val operatorName: String = "مدیر اجرایی",
    val actionTitle: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "invite_code_records")
data class InviteCodeRecord(
    @PrimaryKey val phoneNumber: String,
    val fullName: String,
    val inviteCode: String
)

@Entity(tableName = "customer_transactions")
data class CustomerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerId: Long,
    val customerName: String = "",
    val stationName: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val paidAmount: Long = 0L,
    val status: String = "UNREVIEWED", // "UNREVIEWED", "REVIEWED", "DEBTOR"
    val dateStr: String = "",
    val timeStr: String = "",
    val segmentDetails: String = "",
    val buffetDetails: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val playMinutes: Int = 0,
    val gameCost: Long = 0L,
    val foodCost: Long = 0L
) {
    val conciseTitle: String
        get() {
            var s = title
            s = s.replace("PlayStation 5", "PS5", ignoreCase = true)
                .replace("PlayStation5", "PS5", ignoreCase = true)
                .replace("PlayStation 4", "PS4", ignoreCase = true)
                .replace("PlayStation4", "PS4", ignoreCase = true)
                .replace("بازی", "", ignoreCase = true)
                .replace("دسته", "", ignoreCase = true)
                .replace("ایستگاه شماره", "ایستگاه", ignoreCase = true)
            return s.replace(Regex("\\s+"), " ").trim()
        }

    val conciseStation: String
        get() {
            var s = stationName
            s = s.replace("ایستگاه شماره", "ایستگاه", ignoreCase = true)
            return s.replace(Regex("\\s+"), " ").trim()
        }
}

@Entity(tableName = "reservations")
data class Reservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val managerId: String = "",
    val stationId: Int = 0,
    val fullName: String,
    val phoneNumber: String,
    val reservationTimeMillis: Long,
    val durationMinutes: Int,
    val status: String = "PENDING", // "PENDING", "CONFIRMED", "REJECTED"
    val notified30: Boolean = false,
    val notified15: Boolean = false,
    val notified5: Boolean = false,
    
    // NEW RESERVATION ENGINE FIELDS
    val reservationType: String = "NORMAL_RESERVATION",
    val endTimeMillis: Long = 0L,
    val controllerCount: Int = 1,
    val basePrice: Long = 0L,
    val finalPrice: Long = 0L,
    val discountAmount: Long = 0L,
    val paymentStatus: String = "PENDING",
    val paymentDeadline: Long = 0L,
    val snapshotJson: String = "{}",
    val cancellationSnapshot: String = "{}",
    val gnReward: Long = 0L,
    val lpReward: Long = 0L
)

@Entity(tableName = "point_logs")
data class PointLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerId: Long,
    val title: String,
    val points: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class NonFinancialPerk(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isActive: Boolean = true,
    val startDate: String = "",
    val endDate: String = ""
)

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




