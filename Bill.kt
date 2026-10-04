package com.cakeshop.manager.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String,
    val no: String,
    val date: String,      // yyyy-MM-dd
    val ts: Long,          // epoch millis, for same-day ordering and receipt time
    val customer: String = "",
    val phone: String = "",
    val pay: String = "Cash",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val taxRate: Double = 0.0,
    val tax: Double = 0.0,
    val roundOff: Double = 0.0,
    val total: Double = 0.0
)

@Entity(
    tableName = "bill_lines",
    foreignKeys = [ForeignKey(
        entity = Bill::class,
        parentColumns = ["id"],
        childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("billId"), Index("productId")]
)
data class BillLine(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0,
    val billId: String,
    val productId: String,
    val name: String,      // snapshot of the product name at sale time
    val unit: String,
    val qty: Double,
    val price: Double,     // snapshot of the selling price at sale time
    val cost: Double       // snapshot of the unit cost at sale time
)
