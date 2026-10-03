package com.industri.fleettrack.ui.manifest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.data.repository.DeliveryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class ManifestUiState {
    object Loading : ManifestUiState()
    data class Success(val orders: List<DeliveryOrderEntity>) : ManifestUiState()
    data class Error(val message: String) : ManifestUiState()
}

data class OrderInput(
    val trackingNumber: String,
    val recipientName: String,
    val recipientPhone: String,
    val destinationAddress: String,
    val codAmount: Double,
    val status: String,
    val failureReason: String?
)

sealed class UiEvent {
    data class ShowMessage(val text: String) : UiEvent()
    object SaveSuccess : UiEvent()
    data class SaveError(val message: String) : UiEvent()
    data class OrderDeleted(val order: DeliveryOrderEntity) : UiEvent()
}

class ManifestViewModel(
    private val repository: DeliveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManifestUiState>(ManifestUiState.Loading)
    val uiState: StateFlow<ManifestUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.manifestOrdersFlow
                .catch { e ->
                    _uiState.value = ManifestUiState.Error(e.message ?: "Terjadi kesalahan data")
                }
                .collect { orders ->
                    _uiState.value = ManifestUiState.Success(orders)
                }
        }
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = repository.refreshManifest()
                result.onFailure {
                    _events.emit(UiEvent.ShowMessage("Mode offline: menampilkan data lokal"))
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowMessage("Mode offline: menampilkan data lokal"))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun markAsDelivered(orderId: String) {
        viewModelScope.launch {
            repository.updateDeliveryStatus(orderId, DeliveryStatus.DELIVERED)
        }
    }

    fun saveOrder(existingOrderId: String?, input: OrderInput) {
        viewModelScope.launch {
            val normalizedTracking = input.trackingNumber.trim().uppercase()
            val normalizedReason = if (input.status == DeliveryStatus.FAILED) {
                input.failureReason?.trim()?.ifEmpty { null }
            } else {
                null
            }

            val result = if (existingOrderId == null) {
                val newEntity = DeliveryOrderEntity(
                    orderId = "LOC-" + System.currentTimeMillis(),
                    trackingNumber = normalizedTracking,
                    recipientName = input.recipientName.trim(),
                    recipientPhone = input.recipientPhone.trim(),
                    destinationAddress = input.destinationAddress.trim(),
                    codAmount = input.codAmount,
                    deliveryStatus = input.status,
                    failureReason = normalizedReason,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
                repository.addOrder(newEntity)
            } else {
                val currentState = _uiState.value
                val existingEntity = (currentState as? ManifestUiState.Success)
                    ?.orders
                    ?.find { it.orderId == existingOrderId }

                if (existingEntity != null) {
                    val updatedEntity = existingEntity.copy(
                        trackingNumber = normalizedTracking,
                        recipientName = input.recipientName.trim(),
                        recipientPhone = input.recipientPhone.trim(),
                        destinationAddress = input.destinationAddress.trim(),
                        codAmount = input.codAmount,
                        deliveryStatus = input.status,
                        failureReason = normalizedReason,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateOrder(updatedEntity)
                } else {
                    Result.failure(IllegalStateException("Paket tidak ditemukan"))
                }
            }

            result.fold(
                onSuccess = {
                    _events.emit(UiEvent.SaveSuccess)
                },
                onFailure = { error ->
                    _events.emit(UiEvent.SaveError(error.message ?: "Gagal menyimpan paket"))
                }
            )
        }
    }

    fun deleteOrder(order: DeliveryOrderEntity) {
        viewModelScope.launch {
            val result = repository.deleteOrder(order.orderId)
            result.onSuccess {
                _events.emit(UiEvent.OrderDeleted(order))
            }
        }
    }

    fun undoDelete(order: DeliveryOrderEntity) {
        viewModelScope.launch {
            repository.restoreOrder(order)
        }
    }

    class Factory(private val repository: DeliveryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ManifestViewModel::class.java)) {
                return ManifestViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
