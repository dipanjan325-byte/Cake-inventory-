package com.cakeshop.manager.data.dao

import androidx.room.*
import com.cakeshop.manager.data.entity.Purchase
import com.cakeshop.manager.data.entity.PurchaseLine
import kotlinx.coroutines.flow.Flow

data class PurchaseWithLines(
    @Embedded val purchase: Purchase,
    @Relation(parentColumn = "id", entityColumn = "purchaseId")
    val lines: List<PurchaseLine>
)

@Dao
interface PurchaseDao {
    @Transaction
    @Query("SELECT * FROM purchases ORDER BY ts DESC")
    fun observeAllWithLines(): Flow<List<PurchaseWithLines>>

    @Transaction
    @Query("SELECT * FROM purchases ORDER BY ts DESC")
    suspend fun getAllWithLinesOnce(): List<PurchaseWithLines>

    @Insert
    suspend fun insertPurchase(purchase: Purchase)

    @Insert
    suspend fun insertLines(lines: List<PurchaseLine>)

    @Transaction
    suspend fun insertPurchaseWithLines(purchase: Purchase, lines: List<PurchaseLine>) {
        insertPurchase(purchase)
        insertLines(lines)
    }

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
}
