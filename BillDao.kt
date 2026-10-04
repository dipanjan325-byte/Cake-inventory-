package com.cakeshop.manager.data.dao

import androidx.room.*
import com.cakeshop.manager.data.entity.Bill
import com.cakeshop.manager.data.entity.BillLine
import kotlinx.coroutines.flow.Flow

data class BillWithLines(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "id", entityColumn = "billId")
    val lines: List<BillLine>
)

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bills ORDER BY ts DESC")
    fun observeAllWithLines(): Flow<List<BillWithLines>>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY ts DESC")
    suspend fun getAllWithLinesOnce(): List<BillWithLines>

    @Query("SELECT * FROM bill_lines")
    suspend fun getAllLinesOnce(): List<BillLine>

    @Query("SELECT COUNT(*) FROM bills WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Insert
    suspend fun insertBill(bill: Bill)

    @Insert
    suspend fun insertLines(lines: List<BillLine>)

    @Transaction
    suspend fun insertBillWithLines(bill: Bill, lines: List<BillLine>) {
        insertBill(bill)
        insertLines(lines)
    }

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: String) // bill_lines cascade on delete

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
