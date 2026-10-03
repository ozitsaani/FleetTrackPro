package com.industri.fleettrack.data.local.dao

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeliveryDaoImpl(context: Context) : DeliveryDao {

    private val sharedPreferences =
        context.getSharedPreferences("fleettrack_local_db", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val mutex = Mutex()

    private val _ordersFlow = MutableStateFlow<List<DeliveryOrderEntity>>(emptyList())

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        if (!sharedPreferences.contains("delivery_orders")) {
            val defaults = defaultOrders()
            _ordersFlow.value = defaults
            saveToPrefsInternal(defaults)
            return
        }

        val json = sharedPreferences.getString("delivery_orders", null)
        if (json == null) {
            val defaults = defaultOrders()
            _ordersFlow.value = defaults
            saveToPrefsInternal(defaults)
        } else {
            try {
                val type = object : TypeToken<List<DeliveryOrderEntity>>() {}.type
                val orders: List<DeliveryOrderEntity>? = gson.fromJson(json, type)
                if (orders != null) {
                    // Daftar kosong sungguhan ([]) dimuat apa adanya, jangan di-seed ulang
                    _ordersFlow.value = orders.sortedByDescending { it.updatedAt }
                } else {
                    val defaults = defaultOrders()
                    _ordersFlow.value = defaults
                    saveToPrefsInternal(defaults)
                }
            } catch (e: Exception) {
                val defaults = defaultOrders()
                _ordersFlow.value = defaults
                saveToPrefsInternal(defaults)
            }
        }
    }

    private fun saveToPrefsInternal(orders: List<DeliveryOrderEntity>) {
        val json = gson.toJson(orders)
        sharedPreferences.edit().putString("delivery_orders", json).apply()
    }

    private fun defaultOrders(): List<DeliveryOrderEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            DeliveryOrderEntity(
                orderId = "ORD-2026-8801",
                trackingNumber = "EXP-90021-JKT",
                recipientName = "Ibu Ratna Dewi",
                recipientPhone = "081234567890",
                destinationAddress = "Jl. Melati Raya No. 45, Harjamukti, Cirebon",
                codAmount = 125000.0,
                deliveryStatus = DeliveryStatus.PENDING,
                failureReason = null,
                isSynced = true,
                updatedAt = now - 2000
            ),
            DeliveryOrderEntity(
                orderId = "ORD-2026-8802",
                trackingNumber = "EXP-90022-BDG",
                recipientName = "Pak Bambang Sutrisno",
                recipientPhone = "081398765432",
                destinationAddress = "Jl. Kartini No. 12, Kejaksan, Cirebon",
                codAmount = 0.0,
                deliveryStatus = DeliveryStatus.IN_DELIVERY,
                failureReason = null,
                isSynced = true,
                updatedAt = now - 1000
            ),
            DeliveryOrderEntity(
                orderId = "ORD-2026-8803",
                trackingNumber = "EXP-90023-CRB",
                recipientName = "Sdr. Dimas Prasetyo",
                recipientPhone = "085712349988",
                destinationAddress = "Perum Citra Garden Blok B-3, Kedawung, Cirebon",
                codAmount = 75000.0,
                deliveryStatus = DeliveryStatus.DELIVERED,
                failureReason = null,
                isSynced = true,
                updatedAt = now
            )
        ).sortedByDescending { it.updatedAt }
    }

    override fun getAllDeliveryOrders(): Flow<List<DeliveryOrderEntity>> = _ordersFlow.asStateFlow()

    override fun getOrdersByStatus(status: String): Flow<List<DeliveryOrderEntity>> {
        return _ordersFlow.map { list ->
            list.filter { it.deliveryStatus == status }
        }
    }

    override suspend fun insertAll(orders: List<DeliveryOrderEntity>) {
        mutex.withLock {
            val currentMap = _ordersFlow.value.associateBy { it.orderId }.toMutableMap()
            orders.forEach { newOrder ->
                currentMap[newOrder.orderId] = newOrder
            }
            val updatedList = currentMap.values.sortedByDescending { it.updatedAt }
            _ordersFlow.value = updatedList
            saveToPrefsInternal(updatedList)
        }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: String,
        isSynced: Boolean,
        timestamp: Long
    ) {
        mutex.withLock {
            val currentList = _ordersFlow.value
            val updatedList = currentList.map { order ->
                if (order.orderId == orderId) {
                    order.copy(
                        deliveryStatus = status,
                        isSynced = isSynced,
                        updatedAt = timestamp
                    )
                } else {
                    order
                }
            }.sortedByDescending { it.updatedAt }
            _ordersFlow.value = updatedList
            saveToPrefsInternal(updatedList)
        }
    }

    override suspend fun deleteOrder(orderId: String) {
        mutex.withLock {
            val currentList = _ordersFlow.value
            val updatedList = currentList.filterNot { it.orderId == orderId }
            _ordersFlow.value = updatedList
            saveToPrefsInternal(updatedList)
        }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            _ordersFlow.value = emptyList()
            saveToPrefsInternal(emptyList())
        }
    }
}
