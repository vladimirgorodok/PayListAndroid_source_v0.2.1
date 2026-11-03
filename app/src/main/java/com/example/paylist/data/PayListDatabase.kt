package com.example.paylist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PaymentTemplate::class, PaymentOccurrence::class, PaymentOperation::class],
    version = 1,
    exportSchema = true
)
abstract class PayListDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile private var INSTANCE: PayListDatabase? = null
        fun getInstance(context: Context): PayListDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PayListDatabase::class.java,
                    "paylist.db"
                ).build().also { INSTANCE = it }
            }
    }
}
