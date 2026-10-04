package com.cakeshop.manager.data.dao

import androidx.room.*
import com.cakeshop.manager.data.entity.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors ORDER BY name")
    fun observeAll(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors ORDER BY name")
    suspend fun getAllOnce(): List<Vendor>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getById(id: String): Vendor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vendor: Vendor)

    @Query("DELETE FROM vendors WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vendors")
    suspend fun deleteAll()
}
