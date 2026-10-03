package com.industri.fleettrack.data.repository

import com.industri.fleettrack.data.local.dao.DeliveryDao
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.remote.api.CourierApiService
import com.industri.fleettrack.data.remote.api.DeliveryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class DeliveryRepository(
    private val deliveryDao: DeliveryDao,
    private val apiService: CourierApiService
) {

    val manifestOrdersFlow: Flow<List<DeliveryOrderEntity>> = deliveryDao.getAllDeliveryOrders()

    suspend fun refreshManifest(): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Push tertunda: periksa paket yang belum tersinkron (isSynced == false)
        try {
            val localSnapshot = manifestOrdersFlow.first()
            val pendingOrders = localSnapshot.filter { !it.isSynced }
            for (pending in pendingOrders) {
                try {
                    val pushResponse = apiService.upsertDelivery(pending.orderId, pending.toDto())
                    if (pushResponse.isSuccessful) {
                        deliveryDao.updateOrderStatus(
                            orderId = pending.orderId,
                            status = pending.deliveryStatus,
                            isSynced = true,
                            timestamp = pending.updatedAt
                        )
                    } else {
                        Timber.w("Gagal push order ${pending.orderId} ke server: HTTP ${pushResponse.code()}")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Gagal koneksi saat push order ${pending.orderId} ke server")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Gagal membaca snapshot lokal saat push perubahan tertunda")
        }

        // 2. Pull server: ambil manifest terbaru dari server
        try {
            val response = apiService.getTodayManifest()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val currentLocalSnapshot = manifestOrdersFlow.first()
                    val unsyncedOrderIds = currentLocalSnapshot.filter { !it.isSynced }.map { it.orderId }.toSet()

                    // Lewati order yang di lokal masih isSynced == false (jangan timpa perubahan lokal)
                    val remoteEntities = body.filter { dto ->
                        !unsyncedOrderIds.contains(dto.orderId)
                    }.map { dto ->
                        dto.toEntity(isSynced = true, updatedAt = System.currentTimeMillis())
                    }

                    if (remoteEntities.isNotEmpty()) {
                        deliveryDao.insertAll(remoteEntities)
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Respons server kosong"))
                }
            } else {
                Result.failure(Exception("Gagal mengambil data server (${response.code()})"))
            }
        } catch (e: Exception) {
            Timber.w(e, "Jaringan gagal saat pull manifest dari server")
            Result.failure(Exception("Mode offline: menampilkan data lokal"))
        }
    }

    suspend fun updateDeliveryStatus(orderId: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Tulis lokal dulu (isSynced = false)
        deliveryDao.updateOrderStatus(
            orderId = orderId,
            status = newStatus,
            isSynced = false,
            timestamp = now
        )

        // 2. Coba kirim ke server
        try {
            val response = apiService.updateDeliveryStatus(orderId, newStatus)
            if (response.isSuccessful) {
                deliveryDao.updateOrderStatus(
                    orderId = orderId,
                    status = newStatus,
                    isSynced = true,
                    timestamp = now
                )
            } else {
                Timber.w("Gagal update status di server untuk order $orderId: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Koneksi jaringan gagal saat update status order $orderId")
        }

        // Tetap Result.success karena perubahan lokal sudah tersimpan
        Result.success(Unit)
    }

    suspend fun addOrder(order: DeliveryOrderEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val currentOrders = manifestOrdersFlow.first()
        val isDuplicate = currentOrders.any {
            it.trackingNumber.equals(order.trackingNumber.trim(), ignoreCase = true)
        }
        if (isDuplicate) {
            return@withContext Result.failure(IllegalStateException("Nomor resi sudah dipakai paket lain"))
        }

        val now = System.currentTimeMillis()
        val localOrder = order.copy(
            trackingNumber = order.trackingNumber.trim().uppercase(),
            isSynced = false,
            updatedAt = now
        )
        deliveryDao.insertAll(listOf(localOrder))
        pushOrder(localOrder)
        Result.success(Unit)
    }

    suspend fun updateOrder(order: DeliveryOrderEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val currentOrders = manifestOrdersFlow.first()
        val isDuplicate = currentOrders.any {
            it.orderId != order.orderId && it.trackingNumber.equals(order.trackingNumber.trim(), ignoreCase = true)
        }
        if (isDuplicate) {
            return@withContext Result.failure(IllegalStateException("Nomor resi sudah dipakai paket lain"))
        }

        val now = System.currentTimeMillis()
        val localOrder = order.copy(
            trackingNumber = order.trackingNumber.trim().uppercase(),
            isSynced = false,
            updatedAt = now
        )
        deliveryDao.insertAll(listOf(localOrder))
        pushOrder(localOrder)
        Result.success(Unit)
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        deliveryDao.deleteOrder(orderId)
        try {
            val response = apiService.deleteDelivery(orderId)
            if (!response.isSuccessful) {
                Timber.w("Gagal menghapus order $orderId di server: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Koneksi jaringan gagal saat delete order $orderId di server")
        }
        Result.success(Unit)
    }

    suspend fun restoreOrder(order: DeliveryOrderEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val restoredOrder = order.copy(isSynced = false, updatedAt = now)
        deliveryDao.insertAll(listOf(restoredOrder))
        pushOrder(restoredOrder)
        Result.success(Unit)
    }

    private suspend fun pushOrder(order: DeliveryOrderEntity) {
        try {
            val response = apiService.upsertDelivery(order.orderId, order.toDto())
            if (response.isSuccessful) {
                deliveryDao.updateOrderStatus(
                    orderId = order.orderId,
                    status = order.deliveryStatus,
                    isSynced = true,
                    timestamp = order.updatedAt
                )
            } else {
                Timber.w("Gagal push order ${order.orderId} ke server: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Koneksi jaringan gagal saat push order ${order.orderId} ke server")
        }
    }

    private fun DeliveryOrderEntity.toDto() = DeliveryDto(
        orderId = orderId,
        trackingNumber = trackingNumber,
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        destinationAddress = destinationAddress,
        codAmount = codAmount,
        status = deliveryStatus,
        failureReason = failureReason
    )

    private fun DeliveryDto.toEntity(isSynced: Boolean = true, updatedAt: Long = System.currentTimeMillis()) = DeliveryOrderEntity(
        orderId = orderId,
        trackingNumber = trackingNumber,
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        destinationAddress = destinationAddress,
        codAmount = codAmount,
        deliveryStatus = status,
        failureReason = failureReason,
        isSynced = isSynced,
        updatedAt = updatedAt
    )
}
