package com.timetracking.app.ui.history

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.repository.TimeRecordRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddRecordDialog : BottomSheetDialogFragment() {

    interface Callback {
        fun onRecordAdded()
    }

    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedCheckInTime: Calendar = Calendar.getInstance()
    private var selectedCheckOutTime: Calendar = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1) // Por defecto, una hora después de la entrada
    }

    private lateinit var repository: TimeRecordRepository
    private var callback: Callback? = null

    companion object {
        fun newInstance(callback: Callback): AddRecordDialog {
            val dialog = AddRecordDialog()
            dialog.callback = callback
            return dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_record, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = TimeRecordRepository(
            (requireActivity().application as TimeTrackingApp).database.timeRecordDao()
        )

        // Actualizar las visualizaciones iniciales
        updateDateDisplay(view)
        updateCheckInTimeDisplay(view)
        updateCheckOutTimeDisplay(view)

        // Configurar listeners
        view.findViewById<MaterialButton>(R.id.selectDateButton).setOnClickListener {
            showDatePicker()
        }

        view.findViewById<MaterialButton>(R.id.selectTimeButton).setOnClickListener {
            showCheckInTimePicker()
        }

        view.findViewById<MaterialButton>(R.id.selectCheckOutTimeButton).setOnClickListener {
            showCheckOutTimePicker()
        }

        view.findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            saveRecords()
        }

        view.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener {
            dismiss()
        }
    }

    private fun updateDateDisplay(view: View) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.dateText).text = dateFormat.format(selectedDate.time)
    }

    private fun updateCheckInTimeDisplay(view: View) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        view.findViewById<TextView>(R.id.timeText).text = timeFormat.format(selectedCheckInTime.time)
    }

    private fun updateCheckOutTimeDisplay(view: View) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        view.findViewById<TextView>(R.id.checkOutTimeText).text = timeFormat.format(selectedCheckOutTime.time)
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay(requireView())
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showCheckInTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedCheckInTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCheckInTime.set(Calendar.MINUTE, minute)
                updateCheckInTimeDisplay(requireView())

                // Actualizar la hora de salida para que sea después de la entrada
                if (selectedCheckOutTime.before(selectedCheckInTime)) {
                    selectedCheckOutTime.set(Calendar.HOUR_OF_DAY, hourOfDay + 1)
                    selectedCheckOutTime.set(Calendar.MINUTE, minute)
                    updateCheckOutTimeDisplay(requireView())
                }
            },
            selectedCheckInTime.get(Calendar.HOUR_OF_DAY),
            selectedCheckInTime.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showCheckOutTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                // Validar que la hora de salida sea posterior a la entrada
                val tempCalendar = Calendar.getInstance()
                tempCalendar.set(
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH),
                    hourOfDay,
                    minute
                )

                if (tempCalendar.before(selectedCheckInTime)) {
                    // Mostrar error si intenta establecer una salida antes de la entrada
                    Toast.makeText(context, getString(R.string.error_exit_after_entry), Toast.LENGTH_SHORT).show()
                } else {
                    selectedCheckOutTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    selectedCheckOutTime.set(Calendar.MINUTE, minute)
                    updateCheckOutTimeDisplay(requireView())
                }
            },
            selectedCheckOutTime.get(Calendar.HOUR_OF_DAY),
            selectedCheckOutTime.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveRecords() {
        // Combinar fecha con hora de entrada
        val checkInDateTime = Calendar.getInstance().apply {
            set(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
                selectedCheckInTime.get(Calendar.HOUR_OF_DAY),
                selectedCheckInTime.get(Calendar.MINUTE),
                0
            )
        }.time

        // Combinar fecha con hora de salida
        val checkOutDateTime = Calendar.getInstance().apply {
            set(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
                selectedCheckOutTime.get(Calendar.HOUR_OF_DAY),
                selectedCheckOutTime.get(Calendar.MINUTE),
                0
            )
        }.time

        lifecycleScope.launch {
            try {
                // Crear registro de entrada
                repository.insertRecord(checkInDateTime, RecordType.CHECK_IN)

                // Crear registro de salida
                repository.insertRecord(checkOutDateTime, RecordType.CHECK_OUT)

                callback?.onRecordAdded()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_adding_record, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
}