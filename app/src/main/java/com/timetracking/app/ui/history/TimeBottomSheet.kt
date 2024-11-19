package com.timetracking.app.ui.history

import android.app.TimePickerDialog
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.data.repository.TimeRecordRepository
import com.timetracking.app.ui.history.model.TimeRecordBlock
import kotlinx.coroutines.launch
import java.util.Locale

class TimeEditBottomSheet : BottomSheetDialogFragment() {
    private var timeBlock: TimeRecordBlock? = null
    private var onTimeUpdated: (() -> Unit)? = null
    private lateinit var repository: TimeRecordRepository

    companion object {
        fun newInstance(block: TimeRecordBlock, onTimeUpdated: () -> Unit): TimeEditBottomSheet {
            return TimeEditBottomSheet().apply {
                this.timeBlock = block
                this.onTimeUpdated = onTimeUpdated
            }
        }
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

        repository = TimeRecordRepository((requireActivity().application as TimeTrackingApp).database.timeRecordDao())

        timeBlock?.let { block ->
            setupViews(view, block)
        }
    }

    private fun setupViews(view: View, block: TimeRecordBlock) {
        val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        view.findViewById<TextView>(R.id.dateTitle).text = dateFormat.format(block.date)

        // Setup check-in time
        view.findViewById<TextView>(R.id.checkInTime).text = timeFormat.format(block.checkIn.date)
        view.findViewById<MaterialButton>(R.id.editCheckInButton).setOnClickListener {
            showTimePickerDialog(block.checkIn)
        }

        // Setup check-out time if exists
        block.checkOut?.let { checkOut ->
            view.findViewById<TextView>(R.id.checkOutTime).text = timeFormat.format(checkOut.date)
            view.findViewById<MaterialButton>(R.id.editCheckOutButton).setOnClickListener {
                showTimePickerDialog(checkOut)
            }
        } ?: run {
            view.findViewById<TextView>(R.id.checkOutTime).text = "Pendiente"
            view.findViewById<MaterialButton>(R.id.editCheckOutButton).isEnabled = false
        }
    }

    private fun showTimePickerDialog(record: TimeRecord) {
        val calendar = Calendar.getInstance().apply { time = record.date }

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                lifecycleScope.launch {
                    repository.updateRecordTime(record.id, hourOfDay, minute)
                    onTimeUpdated?.invoke()
                    dismiss()
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
}
