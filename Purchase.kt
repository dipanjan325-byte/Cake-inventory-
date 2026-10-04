package com.cakeshop.manager.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey val id: String,
    val kind: String,          // "vendor" or "inhouse"
    val vendorId: String? = null,
    val date: String,          // yyyy-MM-dd
    val dueDate: String? = null,
    val invoiceNo: String = "",
    val total: Double = 0.0,
    val ts: Long = 0
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [ForeignKey(
        entity = Purchase::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("purchaseId"), Index("productId")]
)
data class PurchaseLine(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0,
    val purchaseId: String,
    val productId: String,
    val qty: Double,
    val unitCost: Double
)
