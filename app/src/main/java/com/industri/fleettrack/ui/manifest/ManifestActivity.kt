package com.industri.fleettrack.ui.manifest

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.industri.fleettrack.R
import com.industri.fleettrack.data.local.AppDatabase
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.remote.api.CourierApiService
import com.industri.fleettrack.data.repository.DeliveryRepository
import com.industri.fleettrack.databinding.ActivityManifestBinding
import kotlinx.coroutines.launch

class ManifestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManifestBinding
    private lateinit var viewModel: ManifestViewModel
    private lateinit var adapter: DeliveryListAdapter

    private var allOrders: List<DeliveryOrderEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManifestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Rakit dependensi manual (tanpa DI framework)
        val database = AppDatabase.getInstance(this)
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val apiService = CourierApiService.create(isDebug)
        val repository = DeliveryRepository(database.deliveryDao(), apiService)
        val factory = ManifestViewModel.Factory(repository)

        viewModel = ViewModelProvider(this, factory)[ManifestViewModel::class.java]

        setupRecyclerView()
        setupFab()
        setupSwipeRefresh()
        setupSearchBar()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = DeliveryListAdapter(
            onDeliverClicked = { order ->
                viewModel.markAsDelivered(order.orderId)
                Toast.makeText(
                    this,
                    getString(R.string.toast_deliver_success, order.trackingNumber),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onEditClicked = { order ->
                OrderFormDialogFragment.newInstance(order.orderId)
                    .show(supportFragmentManager, "order_form")
            },
            onDeleteClicked = { order ->
                showDeleteConfirmation(order)
            }
        )
        binding.rvManifest.layoutManager = LinearLayoutManager(this)
        binding.rvManifest.adapter = adapter
    }

    private fun showDeleteConfirmation(order: DeliveryOrderEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_confirm_title)
            .setMessage(getString(R.string.dialog_delete_confirm_msg, order.trackingNumber))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteOrder(order)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupFab() {
        binding.fabAddOrder.setOnClickListener {
            OrderFormDialogFragment.newInstance(null)
                .show(supportFragmentManager, "order_form")
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun setupSearchBar() {
        binding.etSearchManifest.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterOrders(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterOrders(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allOrders
        } else {
            allOrders.filter { order ->
                order.trackingNumber.contains(trimmed, ignoreCase = true) ||
                        order.recipientName.contains(trimmed, ignoreCase = true) ||
                        order.destinationAddress.contains(trimmed, ignoreCase = true)
            }
        }

        adapter.submitList(filtered.toMutableList())

        if (filtered.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            if (allOrders.isEmpty()) {
                binding.tvEmptyState.setText(R.string.empty_state_no_data)
            } else {
                binding.tvEmptyState.setText(R.string.empty_state_no_result)
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observasi UiState
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is ManifestUiState.Loading -> {
                                if (allOrders.isEmpty()) {
                                    binding.pbMainLoading.visibility = View.VISIBLE
                                }
                            }
                            is ManifestUiState.Success -> {
                                binding.pbMainLoading.visibility = View.GONE
                                allOrders = state.orders
                                filterOrders(binding.etSearchManifest.text.toString())
                            }
                            is ManifestUiState.Error -> {
                                binding.pbMainLoading.visibility = View.GONE
                                Toast.makeText(
                                    this@ManifestActivity,
                                    state.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                // 2. Observasi Swipe Refreshing
                launch {
                    viewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefresh.isRefreshing = isRefreshing
                    }
                }

                // 3. Observasi Events (SharedFlow)
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ShowMessage -> {
                                Toast.makeText(
                                    this@ManifestActivity,
                                    event.text,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is UiEvent.SaveSuccess -> {
                                Toast.makeText(
                                    this@ManifestActivity,
                                    R.string.toast_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is UiEvent.OrderDeleted -> {
                                val snackbar = Snackbar.make(
                                    binding.root,
                                    R.string.toast_deleted,
                                    Snackbar.LENGTH_LONG
                                )
                                    .setAction(R.string.action_undo) {
                                        viewModel.undoDelete(event.order)
                                    }
                                    .setBackgroundTint(
                                        ContextCompat.getColor(
                                            this@ManifestActivity,
                                            R.color.neo_yellow
                                        )
                                    )
                                    .setTextColor(
                                        ContextCompat.getColor(
                                            this@ManifestActivity,
                                            R.color.neo_black
                                        )
                                    )
                                    .setActionTextColor(
                                        ContextCompat.getColor(
                                            this@ManifestActivity,
                                            R.color.neo_black
                                        )
                                    )
                                snackbar.show()
                            }
                            is UiEvent.SaveError -> {
                                // Ditangani langsung oleh OrderFormDialogFragment
                            }
                        }
                    }
                }
            }
        }
    }
}
