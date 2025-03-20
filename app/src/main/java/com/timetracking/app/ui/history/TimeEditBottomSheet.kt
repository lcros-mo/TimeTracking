package com.timetracking.app.ui.history

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.data.repository.TimeRecordRepository
import com.timetracking.app.ui.history.model.TimeRecordBlock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimeEditBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onTimeUpdated()
        fun onRecordDeleted()
    }

    companion object {
        private const val ARG_BLOCK = "arg_block"

        @Suppress("DEPRECATION")
        fun newInstance(block: TimeRecordBlock, callback: Callback): TimeEditBottomSheet {
            return TimeEditBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_BLOCK, block)
                }
                timeEditCallback = callback
            }
        }
    }

    private var timeBlock: TimeRecordBlock? = null
    private var timeEditCallback: Callback? = null
    private lateinit var repository: TimeRecordRepository

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timeBlock = arguments?.getParcelable(ARG_BLOCK)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_time_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = TimeRecordRepository(
            (requireActivity().application as TimeTrackingApp).database.timeRecordDao()
        )

        timeBlock?.let { block ->
            setupViews(view, block)
        }
    }

    private fun setupViews(view: View, block: TimeRecordBlock) {
        val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Configurar título
        view.findViewById<TextView>(R.id.dateTitle).apply {
            text = dateFormat.format(block.date)
        }

        // Obtener referencias a las tarjetas
        val checkInCard = view.findViewById<MaterialCardView>(R.id.checkInCard)
        val checkOutCard = view.findViewById<MaterialCardView>(R.id.checkOutCard)

        // Inicialmente invisibles
        checkInCard.alpha = 0f
        checkOutCard.alpha = 0f

        // Configurar check-in
        view.findViewById<TextView>(R.id.checkInTime).apply {
            text = timeFormat.format(block.checkIn.date)
        }
        view.findViewById<MaterialButton>(R.id.editCheckInButton).setOnClickListener {
            showTimePickerDialog(block.checkIn)
        }

        // Configurar check-out
        block.checkOut?.let { checkOut ->
            view.findViewById<TextView>(R.id.checkOutTime).apply {
                text = timeFormat.format(checkOut.date)
            }
            view.findViewById<MaterialButton>(R.id.editCheckOutButton).setOnClickListener {
                showTimePickerDialog(checkOut)
            }
        } ?: run {
            view.findViewById<TextView>(R.id.checkOutTime).apply {
                text = getString(R.string.pending)
            }
            view.findViewById<MaterialButton>(R.id.editCheckOutButton).isEnabled = false
        }

        // Animar las tarjetas
        checkInCard.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200)
            .start()

        checkOutCard.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(400)
            .start()
        view.findViewById<MaterialButton>(R.id.deleteButton).setOnClickListener {
            showDeleteConfirmation(block)
        }
    }

    private fun showTimePickerDialog(record: TimeRecord) {
        val calendar = Calendar.getInstance().apply { time = record.date }

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                lifecycleScope.launch {
                    repository.updateRecordTime(record.id, hourOfDay, minute)
                    timeEditCallback?.onTimeUpdated()
                    dismiss()
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showDeleteConfirmation(block: TimeRecordBlock) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar registro")
            .setMessage("¿Estás seguro de que deseas eliminar este registro? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    if (block.checkOut != null) {
                        repository.deleteRecord(block.checkOut.id)
                    }
                    repository.deleteRecord(block.checkIn.id)
                    timeEditCallback?.onRecordDeleted()
                    dismiss()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}