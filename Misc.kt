package com.cakeshop.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "returns")
data class ReturnEntry(
    @PrimaryKey val id: String,
    val type: String,          // "customer" | "waste" | "kitchen"
    val date: String,          // yyyy-MM-dd
    val productId: String,
    val qty: Double,
    val refund: Double = 0.0,  // customer type only
    val restock: Boolean = false, // customer type only
    val reason: String = "",
    val billNo: String = "",
    val loss: Double = 0.0,    // waste type only (qty * unit cost)
    val ts: Long = 0
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val id: String,
    val vendorId: String,
    val amount: Double,
    val date: String,          // yyyy-MM-dd
    val mode: String = "Cash",
    val note: String = "",
    val ts: Long = 0
)

@Entity(tableName = "feedback")
data class Feedback(
    @PrimaryKey val id: String,
    val billId: String = "",
    val billNo: String = "",
    val customer: String = "",
    val rating: Int,           // 1..5
    val comment: String = "",
    val date: String,          // yyyy-MM-dd
    val ts: Long = 0
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: String = "main",
    val shopName: String = "My cake shop",
    val taxPct: Double = 5.0,
    val countryCode: String = "91",
    val reviewUrl: String = "https://www.google.com/maps/search/?api=1&query=Monginis+Jore+Bridge",
    val anthropicApiKey: String = ""
)
