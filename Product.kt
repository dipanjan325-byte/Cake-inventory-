package com.cakeshop.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val code: String = "",
    val name: String,
    val category: String,
    val unit: String = "pc",
    val price: Double = 0.0,
    // Shelf life as typed/imported, e.g. "2 days" or "6 months".
    val shelfLifeRaw: String = "",
    // Parsed number of days, only when shelfLifeRaw is of the form "N day(s)".
    // Null for month-based or unrecognised shelf life (no next-day alert for those).
    val shelfLifeDays: Int? = null,
    val reorderAt: Double = 0.0,
    val opening: Double = 0.0,
    // yyyy-MM-dd — the date the opening stock figure applies from.
    val dateAdded: String
)

/** Categories that are not sold directly to a customer at the counter. */
val NON_SELL_CATEGORIES = setOf("Ingredients", "Packaging", "Supplies")
fun Product.isSellable(): Boolean = category !in NON_SELL_CATEGORIES

/** 20% margin is assumed; cost = 80% of price for sellable items, or the
 *  price itself for Ingredients/Packaging/Supplies, where "price" is really
 *  their unit cost since they are never sold at a marked-up price. */
fun Product.unitCost(): Double = if (isSellable()) price * 0.8 else price
fun Product.margin20(): Double = if (isSellable()) price * 0.2 else 0.0
