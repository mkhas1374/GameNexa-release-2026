package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "license_cache")
data class LicenseCacheEntity(
    @PrimaryKey val id: Int = 1,
    val userId: String = "",
    val deviceId: String = "",
    val licenseStatus: String = "INACTIVE", // ACTIVE, EXPIRED, TRIAL, INACTIVE
    val planType: String = "",
    val expireTime: Long = 0L,
    val lastServerValidationTime: Long = 0L
)

@Dao
interface LicenseCacheDao {
    @Query("SELECT * FROM license_cache WHERE id = 1 LIMIT 1")
    suspend fun getLicenseCache(): LicenseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLicenseCache(cache: LicenseCacheEntity)

    @Query("DELETE FROM license_cache")
    suspend fun clearCache()
}
