package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StationState::class,
        ConsoleType::class,
        Product::class,
        StationOrder::class,
        SessionHistory::class,
        AppSetting::class,
        Customer::class,
        CustomerTransaction::class,
        Reservation::class,
        LicenseCacheEntity::class,
        InviteCodeRecord::class,
        PointLog::class,
        OperatorAuditLog::class,
        GnLedgerEntry::class,
        BehaviorRule::class,
        BehaviorLog::class,
        ReferralProgressRecord::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationStateDao(): StationStateDao
    abstract fun consoleTypeDao(): ConsoleTypeDao
    abstract fun productDao(): ProductDao
    abstract fun stationOrderDao(): StationOrderDao
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun customerDao(): CustomerDao
    abstract fun customerTransactionDao(): CustomerTransactionDao
    abstract fun reservationDao(): ReservationDao
    abstract fun licenseCacheDao(): LicenseCacheDao
    abstract fun inviteCodeRecordDao(): InviteCodeRecordDao
    abstract fun pointLogDao(): PointLogDao
    abstract fun operatorAuditLogDao(): OperatorAuditLogDao
    abstract fun gnLedgerDao(): GnLedgerDao
    abstract fun behaviorRuleDao(): BehaviorRuleDao
    abstract fun behaviorLogDao(): BehaviorLogDao
    abstract fun referralProgressRecordDao(): ReferralProgressRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station_states ADD COLUMN customerPrepaymentsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN rewardsConsumed DECIMAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN password TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS operator_audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, operatorName TEXT NOT NULL, actionTitle TEXT NOT NULL, details TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema-compatible version bump; preserve all local data.
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema-compatible version bump; preserve all local data.
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Passwords are server-authoritative and must never remain in the local Room database.
                db.execSQL("UPDATE customers SET password = ''")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN availableGn DECIMAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN pendingGn DECIMAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN lp DECIMAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN tier TEXT NOT NULL DEFAULT 'BRONZE'")
                db.execSQL("ALTER TABLE customers ADD COLUMN lastActivityTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN totalQualifiedSpend DECIMAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN totalVisitsCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN lastTierReviewTimestamp INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gn_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerId INTEGER NOT NULL,
                        customerName TEXT NOT NULL DEFAULT '',
                        gnAmount DECIMAL NOT NULL,
                        transactionType TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT 'EARNED',
                        status TEXT NOT NULL DEFAULT 'AVAILABLE',
                        timestamp INTEGER NOT NULL,
                        referenceId TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS behavior_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        gnChange DECIMAL NOT NULL DEFAULT 0.0,
                        lpChange DECIMAL NOT NULL DEFAULT 0.0,
                        severity TEXT NOT NULL DEFAULT 'LOW',
                        cooldownMinutes INTEGER NOT NULL DEFAULT 0,
                        dailyLimit INTEGER NOT NULL DEFAULT 0,
                        weeklyLimit INTEGER NOT NULL DEFAULT 0,
                        monthlyLimit INTEGER NOT NULL DEFAULT 0,
                        requiresAdminApproval INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS behavior_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerId INTEGER NOT NULL,
                        customerName TEXT NOT NULL DEFAULT '',
                        ruleTitle TEXT NOT NULL,
                        gnChange DECIMAL NOT NULL DEFAULT 0.0,
                        lpChange DECIMAL NOT NULL DEFAULT 0.0,
                        appliedBy TEXT NOT NULL DEFAULT 'مدیر سالن',
                        reason TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS referral_progress_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        referrerCustomerId INTEGER NOT NULL,
                        referredCustomerId INTEGER NOT NULL,
                        referredCustomerName TEXT NOT NULL DEFAULT '',
                        rewardGn DECIMAL NOT NULL DEFAULT 100.0,
                        currentGamingSpend DECIMAL NOT NULL DEFAULT 0.0,
                        requiredGamingSpend DECIMAL NOT NULL DEFAULT 100000.0,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        createdTimestamp INTEGER NOT NULL,
                        completedTimestamp INTEGER
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gamenet_manager_db"
                ).addMigrations(
                    MIGRATION_4_5,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
