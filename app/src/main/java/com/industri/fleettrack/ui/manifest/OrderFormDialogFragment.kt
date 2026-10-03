package com.industri.fleettrack.ui.manifest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.industri.fleettrack.R
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.databinding.DialogOrderFormBinding
import kotlinx.coroutines.launch

class OrderFormDialogFragment : DialogFragment() {

    private var _binding: DialogOrderFormBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ManifestViewModel
    private var orderId: String? = null

    companion object {
        private const val ARG_ORDER_ID = "arg_order_id"

        fun newInstance(orderId: String?): OrderFormDialogFragment {
            return OrderFormDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderId = arguments?.getString(ARG_ORDER_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOrderFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ManifestViewModel::class.java]

        setupStatusSpinner()
        setupFormContent()
        setupListeners()
        observeEvents()
    }

    private fun setupStatusSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            DeliveryStatus.ALL
        )
        binding.spStatus.adapter = adapter

        binding.spStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStatus = DeliveryStatus.ALL[position]
                val isFailed = selectedStatus == DeliveryStatus.FAILED
                binding.tvLabelFailureReason.visibility = if (isFailed) View.VISIBLE else View.GONE
                binding.etFailureReason.visibility = if (isFailed) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupFormContent() {
        if (orderId == null) {
            // Mode Tambah
            binding.tvFormTitle.text = getString(R.string.dialog_title_add)
            val pendingIndex = DeliveryStatus.ALL.indexOf(DeliveryStatus.PENDING)
            if (pendingIndex >= 0) {
                binding.spStatus.setSelection(pendingIndex)
            }
        } else {
            // Mode Edit
            binding.tvFormTitle.text = getString(R.string.dialog_title_edit)
            val currentState = viewModel.uiState.value
            val existingOrder = (currentState as? ManifestUiState.Success)
                ?.orders
                ?.find { it.orderId == orderId }

            existingOrder?.let { order ->
                binding.etTrackingNo.setText(order.trackingNumber)
                binding.etRecipientName.setText(order.recipientName)
                binding.etRecipientPhone.setText(order.recipientPhone)
                binding.etAddress.setText(order.destinationAddress)
                binding.etCod.setText(
                    if (order.codAmount % 1.0 == 0.0) {
                        order.codAmount.toLong().toString()
                    } else {
                        order.codAmount.toString()
                    }
                )

                val statusIndex = DeliveryStatus.ALL.indexOf(order.deliveryStatus)
                if (statusIndex >= 0) {
                    binding.spStatus.setSelection(statusIndex)
                }

                if (order.deliveryStatus == DeliveryStatus.FAILED) {
                    binding.tvLabelFailureReason.visibility = View.VISIBLE
                    binding.etFailureReason.visibility = View.VISIBLE
                    binding.etFailureReason.setText(order.failureReason.orEmpty())
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            validateAndSave()
        }
    }

    private fun validateAndSave() {
        var isValid = true

        val trackingNo = binding.etTrackingNo.text.toString().trim()
        if (trackingNo.isEmpty()) {
            binding.etTrackingNo.error = getString(R.string.error_required)
            isValid = false
        }

        val recipientName = binding.etRecipientName.text.toString().trim()
        if (recipientName.isEmpty()) {
            binding.etRecipientName.error = getString(R.string.error_required)
            isValid = false
        }

        val recipientPhone = binding.etRecipientPhone.text.toString().trim()
        val phoneRegex = Regex("^(\\+62|62|0)8[0-9]{8,12}$")
        if (recipientPhone.isEmpty()) {
            binding.etRecipientPhone.error = getString(R.string.error_required)
            isValid = false
        } else if (!phoneRegex.matches(recipientPhone)) {
            binding.etRecipientPhone.error = getString(R.string.error_phone_invalid)
            isValid = false
        }

        val address = binding.etAddress.text.toString().trim()
        if (address.isEmpty()) {
            binding.etAddress.error = getString(R.string.error_required)
            isValid = false
        } else if (address.length < 5) {
            binding.etAddress.error = getString(R.string.error_address_min)
            isValid = false
        }

        val codText = binding.etCod.text.toString().trim()
        val codAmount = if (codText.isEmpty()) 0.0 else codText.toDoubleOrNull() ?: -1.0
        if (codAmount < 0.0) {
            binding.etCod.error = getString(R.string.error_required)
            isValid = false
        }

        val selectedStatus = binding.spStatus.selectedItem as? String ?: DeliveryStatus.PENDING
        var failureReason: String? = null
        if (selectedStatus == DeliveryStatus.FAILED) {
            val reason = binding.etFailureReason.text.toString().trim()
            if (reason.isEmpty()) {
                binding.etFailureReason.error = getString(R.string.error_required)
                isValid = false
            } else {
                failureReason = reason
            }
        }

        if (!isValid) return

        // Cegah klik ganda
        binding.btnSave.isEnabled = false

        val input = OrderInput(
            trackingNumber = trackingNo,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            destinationAddress = address,
            codAmount = codAmount,
            status = selectedStatus,
            failureReason = failureReason
        )

        viewModel.saveOrder(orderId, input)
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.SaveSuccess -> {
                            dismiss()
                        }
                        is UiEvent.SaveError -> {
                            binding.btnSave.isEnabled = true
                            binding.etTrackingNo.error = event.message
                        }
                        else -> {
                            // Event lain ditangani oleh Activity
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
