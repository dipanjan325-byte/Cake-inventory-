package com.cakeshop.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cakeshop.manager.data.dao.*
import com.cakeshop.manager.data.entity.*

@Database(
    entities = [
        Product::class, Vendor::class, Bill::class, BillLine::class,
        Purchase::class, PurchaseLine::class, ReturnEntry::class,
        Payment::class, Feedback::class, AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun vendorDao(): VendorDao
    abstract fun billDao(): BillDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun returnDao(): ReturnDao
    abstract fun paymentDao(): PaymentDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cake_shop.db"
                ).build().also { INSTANCE = it }
            }
    }
}
