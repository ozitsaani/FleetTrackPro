package com.industri.fleettrack.data.local

import android.content.Context
import com.industri.fleettrack.data.local.dao.DeliveryDao
import com.industri.fleettrack.data.local.dao.DeliveryDaoImpl

abstract class AppDatabase {
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dao = DeliveryDaoImpl(context.applicationContext)
                val instance = object : AppDatabase() {
                    override fun deliveryDao(): DeliveryDao = dao
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
