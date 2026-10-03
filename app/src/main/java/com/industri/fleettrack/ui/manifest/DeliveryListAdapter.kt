package com.industri.fleettrack.ui.manifest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.industri.fleettrack.R
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.databinding.ItemDeliveryOrderBinding
import java.text.NumberFormat
import java.util.Locale

class DeliveryListAdapter(
    private val onDeliverClicked: (DeliveryOrderEntity) -> Unit,
    private val onEditClicked: (DeliveryOrderEntity) -> Unit,
    private val onDeleteClicked: (DeliveryOrderEntity) -> Unit
) : ListAdapter<DeliveryOrderEntity, DeliveryListAdapter.OrderViewHolder>(OrderDiffCallback()) {

    // NumberFormat dibuat sekali sebagai properti adapter untuk efisiensi
    private val currencyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemDeliveryOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(
        val binding: ItemDeliveryOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: DeliveryOrderEntity) {
            val context = binding.root.context

            binding.tvTrackingNo.text = order.trackingNumber
            binding.tvRecipient.text = "${order.recipientName} (${order.recipientPhone})"
            binding.tvAddress.text = order.destinationAddress
            binding.tvCodAmount.text = context.getString(
                R.string.cod_format,
                currencyFormat.format(order.codAmount)
            )

            // Status badge text & background
            binding.tvStatusBadge.text = order.deliveryStatus
            val badgeRes = when (order.deliveryStatus) {
                DeliveryStatus.PENDING -> R.drawable.bg_badge_pending
                DeliveryStatus.IN_DELIVERY -> R.drawable.bg_badge_in_delivery
                DeliveryStatus.DELIVERED -> R.drawable.bg_badge_delivered
                DeliveryStatus.FAILED -> R.drawable.bg_badge_failed
                else -> R.drawable.bg_badge_pending
            }
            binding.tvStatusBadge.background = ContextCompat.getDrawable(context, badgeRes)

            // Indikator Belum Tersinkron (isSynced == false)
            binding.tvSyncState.visibility = if (!order.isSynced) View.VISIBLE else View.GONE

            // Alasan gagal kirim (hanya tampil jika FAILED dan ada isinya)
            if (order.deliveryStatus == DeliveryStatus.FAILED && !order.failureReason.isNullOrBlank()) {
                binding.tvFailureReason.visibility = View.VISIBLE
                binding.tvFailureReason.text = context.getString(R.string.reason_prefix, order.failureReason)
            } else {
                binding.tvFailureReason.visibility = View.GONE
            }

            // Tombol status pengiriman
            when (order.deliveryStatus) {
                DeliveryStatus.PENDING, DeliveryStatus.IN_DELIVERY -> {
                    binding.btnActionDeliver.isEnabled = true
                    binding.btnActionDeliver.setText(R.string.action_deliver)
                }
                DeliveryStatus.DELIVERED -> {
                    binding.btnActionDeliver.isEnabled = false
                    binding.btnActionDeliver.setText(R.string.action_completed)
                }
                DeliveryStatus.FAILED -> {
                    binding.btnActionDeliver.isEnabled = false
                    binding.btnActionDeliver.setText(R.string.action_failed)
                }
                else -> {
                    binding.btnActionDeliver.isEnabled = true
                    binding.btnActionDeliver.setText(R.string.action_deliver)
                }
            }

            // Hook listeners
            binding.btnActionDeliver.setOnClickListener {
                onDeliverClicked(order)
            }
            binding.btnActionEdit.setOnClickListener {
                onEditClicked(order)
            }
            binding.btnActionDelete.setOnClickListener {
                onDeleteClicked(order)
            }
        }
    }

    private class OrderDiffCallback : DiffUtil.ItemCallback<DeliveryOrderEntity>() {
        override fun areItemsTheSame(
            oldItem: DeliveryOrderEntity,
            newItem: DeliveryOrderEntity
        ): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(
            oldItem: DeliveryOrderEntity,
            newItem: DeliveryOrderEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
