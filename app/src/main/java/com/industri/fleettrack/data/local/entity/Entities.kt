package com.industri.fleettrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object DeliveryStatus {
    const val PENDING = "PENDING"
    const val IN_DELIVERY = "IN_DELIVERY"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    val ALL = listOf(PENDING, IN_DELIVERY, DELIVERED, FAILED)
}

@Entity(
    tableName = "delivery_orders",
    indices = [Index(value = ["tracking_number"], unique = true)]
)
data class DeliveryOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: String,

    @ColumnInfo(name = "tracking_number")
    val trackingNumber: String,

    @ColumnInfo(name = "recipient_name")
    val recipientName: String,

    @ColumnInfo(name = "recipient_phone")
    val recipientPhone: String,

    @ColumnInfo(name = "destination_address")
    val destinationAddress: String,

    @ColumnInfo(name = "cod_amount")
    val codAmount: Double = 0.0,

    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val queueId: Long = 0,

    @ColumnInfo(name = "order_id")
    val orderId: String,

    @ColumnInfo(name = "target_status")
    val targetStatus: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
