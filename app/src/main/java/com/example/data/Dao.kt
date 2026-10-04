package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StationStateDao {
    @Query("SELECT * FROM station_states ORDER BY id ASC")
    fun getAll(): Flow<List<StationState>>

    @Query("SELECT * FROM station_states WHERE id = :id")
    suspend fun getById(id: Int): StationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: StationState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<StationState>)

    @Query("DELETE FROM station_states WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM station_states")
    suspend fun clearAll()
}

@Dao
interface ConsoleTypeDao {
    @Query("SELECT * FROM console_types ORDER BY name ASC")
    fun getAll(): Flow<List<ConsoleType>>

    @Query("SELECT * FROM console_types WHERE name = :name")
    suspend fun getByName(name: String): ConsoleType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(consoleType: ConsoleType)

    @Query("DELETE FROM console_types WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM console_types")
    suspend fun clearAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name = :name")
    suspend fun getByName(name: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    @Query("DELETE FROM products WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}

@Dao
interface StationOrderDao {
    @Query("SELECT * FROM station_orders WHERE stationId = :stationId")
    fun getOrdersForStation(stationId: Int): Flow<List<StationOrder>>

    @Query("SELECT * FROM station_orders WHERE stationId = :stationId")
    suspend fun getOrdersForStationSync(stationId: Int): List<StationOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: StationOrder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<StationOrder>)

    @Query("DELETE FROM station_orders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM station_orders WHERE stationId = :stationId")
    suspend fun clearForStation(stationId: Int)
}

@Dao
interface SessionHistoryDao {
    @Query("SELECT * FROM session_history ORDER BY endTimeMillis DESC")
    fun getAll(): Flow<List<SessionHistory>>

    @Query("SELECT * FROM session_history ORDER BY endTimeMillis DESC")
    suspend fun getAllSync(): List<SessionHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SessionHistory)

    @Query("DELETE FROM session_history")
    suspend fun clearAll()
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSync(): List<AppSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSetting)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY fullName ASC")
    fun getAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY fullName ASC")
    suspend fun getAllList(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<Customer>)

    @Delete
    suspend fun delete(customer: Customer)

    @Query("DELETE FROM customers WHERE id NOT IN (:serverIds)")
    suspend fun deleteCustomersMissingFromServer(serverIds: List<Long>)

    @Query("DELETE FROM customers WHERE phoneNumber NOT IN (:serverPhones)")
    suspend fun deleteCustomersMissingFromServerPhones(serverPhones: List<String>)

    @Query("DELETE FROM customers")
    suspend fun clearAll()
}

@Dao
interface InviteCodeRecordDao {
    @Query("SELECT * FROM invite_code_records WHERE phoneNumber = :phone AND fullName = :name")
    suspend fun getRecord(phone: String, name: String): InviteCodeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: InviteCodeRecord)
}

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations ORDER BY reservationTimeMillis ASC")
    fun getAll(): Flow<List<Reservation>>

    @Query("SELECT * FROM reservations WHERE id = :id")
    suspend fun getById(id: Long): Reservation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reservation: Reservation): Long

    @Delete
    suspend fun delete(reservation: Reservation)

    @Query("DELETE FROM `reservations` WHERE phoneNumber = :phone")
    suspend fun deleteByPhone(phone: String)

    @Query("DELETE FROM `reservations`")
    suspend fun clearAll()
}

@Dao
interface CustomerTransactionDao {
    @Query("SELECT * FROM customer_transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CustomerTransaction>>

    @Query("SELECT * FROM customer_transactions ORDER BY timestamp DESC")
    suspend fun getAllList(): List<CustomerTransaction>

    @Query("SELECT * FROM customer_transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getByCustomerId(customerId: Long): Flow<List<CustomerTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: CustomerTransaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CustomerTransaction>)

    @Update
    suspend fun update(transaction: CustomerTransaction)

    @Delete
    suspend fun delete(transaction: CustomerTransaction)

    @Query("DELETE FROM customer_transactions WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: Long)

    @Query("DELETE FROM customer_transactions")
    suspend fun clearAll()
}

@Dao
interface PointLogDao {
    @Query("SELECT * FROM point_logs WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getLogsByCustomerId(customerId: Long): Flow<List<PointLog>>

    @Query("SELECT * FROM point_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PointLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PointLog)

    @Query("DELETE FROM point_logs WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: Long)
}

@Dao
interface OperatorAuditLogDao {
    @Query("SELECT * FROM operator_audit_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<OperatorAuditLog>>

    @Query("SELECT * FROM operator_audit_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentList(): List<OperatorAuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: OperatorAuditLog): Long

    @Query("DELETE FROM operator_audit_logs")
    suspend fun clearAll()
}

@Dao
interface GnLedgerDao {
    @Query("SELECT * FROM gn_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getByCustomerId(customerId: Long): Flow<List<GnLedgerEntry>>

    @Query("SELECT * FROM gn_ledger ORDER BY timestamp DESC")
    fun getAllLedgerEntries(): Flow<List<GnLedgerEntry>>

    @Query("SELECT * FROM gn_ledger WHERE referenceId = :referenceId LIMIT 1")
    suspend fun getByReferenceId(referenceId: String): GnLedgerEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GnLedgerEntry): Long

    @Update
    suspend fun update(entry: GnLedgerEntry)

    @Delete
    suspend fun delete(entry: GnLedgerEntry)

    @Query("DELETE FROM gn_ledger WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: Long)
}

@Dao
interface BehaviorRuleDao {
    @Query("SELECT * FROM behavior_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<BehaviorRule>>

    @Query("SELECT * FROM behavior_rules ORDER BY id ASC")
    suspend fun getAllRulesList(): List<BehaviorRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BehaviorRule): Long

    @Update
    suspend fun update(rule: BehaviorRule)

    @Delete
    suspend fun delete(rule: BehaviorRule)
}

@Dao
interface BehaviorLogDao {
    @Query("SELECT * FROM behavior_logs WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getByCustomerId(customerId: Long): Flow<List<BehaviorLog>>

    @Query("SELECT * FROM behavior_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BehaviorLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BehaviorLog): Long

    @Query("DELETE FROM behavior_logs WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: Long)
}

@Dao
interface ReferralProgressRecordDao {
    @Query("SELECT * FROM referral_progress_records WHERE referrerCustomerId = :referrerCustomerId ORDER BY createdTimestamp DESC")
    fun getByReferrerId(referrerCustomerId: Long): Flow<List<ReferralProgressRecord>>

    @Query("SELECT * FROM referral_progress_records WHERE referredCustomerId = :referredCustomerId LIMIT 1")
    suspend fun getByReferredId(referredCustomerId: Long): ReferralProgressRecord?

    @Query("SELECT * FROM referral_progress_records ORDER BY createdTimestamp DESC")
    suspend fun getAllList(): List<ReferralProgressRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ReferralProgressRecord): Long

    @Update
    suspend fun update(record: ReferralProgressRecord)

    @Query("DELETE FROM referral_progress_records WHERE referrerCustomerId = :customerId OR referredCustomerId = :customerId")
    suspend fun deleteByCustomerId(customerId: Long)
}


