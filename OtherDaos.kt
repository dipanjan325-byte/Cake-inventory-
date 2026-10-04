package com.cakeshop.manager.data.dao

import androidx.room.*
import com.cakeshop.manager.data.entity.AppSettingsEntity
import com.cakeshop.manager.data.entity.Feedback
import com.cakeshop.manager.data.entity.Payment
import com.cakeshop.manager.data.entity.ReturnEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ReturnDao {
    @Query("SELECT * FROM returns ORDER BY ts DESC")
    fun observeAll(): Flow<List<ReturnEntry>>

    @Query("SELECT * FROM returns ORDER BY ts DESC")
    suspend fun getAllOnce(): List<ReturnEntry>

    @Insert
    suspend fun insert(entry: ReturnEntry)

    @Query("DELETE FROM returns WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM returns")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY ts DESC")
    fun observeAll(): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY ts DESC")
    suspend fun getAllOnce(): List<Payment>

    @Insert
    suspend fun insert(payment: Payment)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback ORDER BY ts DESC")
    fun observeAll(): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback ORDER BY ts DESC")
    suspend fun getAllOnce(): List<Feedback>

    @Insert
    suspend fun insert(entry: Feedback)

    @Query("DELETE FROM feedback WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM feedback")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 'main' LIMIT 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'main' LIMIT 1")
    suspend fun getOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
