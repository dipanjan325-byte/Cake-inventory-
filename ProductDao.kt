package com.cakeshop.manager.data.dao

import androidx.room.*
import com.cakeshop.manager.data.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name")
    suspend fun getAllOnce(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): Product?

    @Query("SELECT * FROM products WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<Product>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
