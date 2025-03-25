package com.timetracking.app.ui.history

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
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
    private var selectedTime: Calendar = Calendar.getInstance()
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

        updateDateDisplay(view)
        updateTimeDisplay(view)

        // Configurar selector de fecha
        view.findViewById<MaterialButton>(R.id.selectDateButton).setOnClickListener {
            showDatePicker()
        }

        // Configurar selector de hora
        view.findViewById<MaterialButton>(R.id.selectTimeButton).setOnClickListener {
            showTimePicker()
        }

        // Configurar botón guardar
        view.findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            saveRecord(view)
        }

        // Configurar botón cancelar
        view.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener {
            dismiss()
        }
    }

    private fun updateDateDisplay(view: View) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.dateText).text = dateFormat.format(selectedDate.time)
    }

    private fun updateTimeDisplay(view: View) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        view.findViewById<TextView>(R.id.timeText).text = timeFormat.format(selectedTime.time)
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

    private fun showTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                updateTimeDisplay(requireView())
            },
            selectedTime.get(Calendar.HOUR_OF_DAY),
            selectedTime.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveRecord(view: View) {
        // Combinar fecha y hora seleccionadas
        val finalDateTime = Calendar.getInstance().apply {
            set(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
                selectedTime.get(Calendar.HOUR_OF_DAY),
                selectedTime.get(Calendar.MINUTE),
                0
            )
        }.time

        // Determinar tipo de registro
        val isCheckIn = view.findViewById<RadioButton>(R.id.checkInRadio).isChecked
        val recordType = if (isCheckIn) RecordType.CHECK_IN else RecordType.CHECK_OUT

        lifecycleScope.launch {
            repository.insertRecord(finalDateTime, recordType)
            callback?.onRecordAdded()
            dismiss()
        }
    }
}