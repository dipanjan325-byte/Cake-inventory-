package com.cakeshop.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vendors")
data class Vendor(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val creditDays: Int = 7,
    val note: String = ""
)
