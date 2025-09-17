package com.timetracking.app.ui.history

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.data.model.TimeRecordBlock
import com.timetracking.app.core.utils.DateTimeUtils
import com.timetracking.app.core.utils.TimeRecordValidator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimeEditBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onTimeUpdated()
        fun onRecordDeleted(recordId: Long)
    }

    companion object {
        private const val ARG_BLOCK = "arg_block"

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
            (requireActivity().application as TimeTrackingApp).database.timeRecordDao(),
            (requireActivity().application as TimeTrackingApp).database.overtimeBalanceDao()
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

        // Configurar check-out - REVERTIDO AL ORIGINAL
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
            view.findViewById<MaterialButton>(R.id.editCheckOutButton).apply {
                isEnabled = true
                setOnClickListener {
                    showCompleteCheckOutDialog(block.checkIn)
                }
            }
        }



        // Configurar el campo de comentarios - MANTENER ESTO
        val commentInput = view.findViewById<TextInputEditText>(R.id.commentInput)

        // Cargar el comentario actual (puede ser de entrada o salida)
        val currentComment = block.checkOut?.note ?: block.checkIn.note
        commentInput.setText(currentComment)

        // Añadir botón de guardar comentario
        view.findViewById<MaterialButton>(R.id.saveCommentButton)?.setOnClickListener {
            val newComment = commentInput.text.toString().trim()
            saveComment(block, newComment)
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

        // Añadir botón de eliminación
        view.findViewById<MaterialButton>(R.id.deleteButton)?.setOnClickListener {
            showDeleteConfirmation(block)
        }
    }

    private fun showCompleteCheckOutDialog(checkIn: TimeRecord) {
        val calendar = Calendar.getInstance()

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                lifecycleScope.launch {
                    try {
                        // Crear fecha de salida basada en la misma fecha que la entrada pero con la hora seleccionada
                        val checkOutDate = DateTimeUtils.setTimeToDate(checkIn.date, hourOfDay, minute)

                        // Validar que la hora sea posterior a la entrada
                        val (isValid, errorMsg) = TimeRecordValidator.validateCheckOutTime(checkIn.date, checkOutDate)

                        if (isValid) {
                            // Crear un nuevo registro de salida
                            repository.insertRecord(checkOutDate, RecordType.CHECK_OUT, checkIn.note)
                            timeEditCallback?.onTimeUpdated()
                            dismiss()
                            Toast.makeText(context, getString(R.string.record_completed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, getString(R.string.error_saving, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
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

    private fun saveComment(block: TimeRecordBlock, comment: String) {
        lifecycleScope.launch {
            try {
                // Guardar comentario en entrada
                repository.updateRecordNote(block.checkIn.id, comment)

                // Si hay salida, guardar también ahí
                block.checkOut?.let { checkOut ->
                    repository.updateRecordNote(checkOut.id, comment)
                }

                Toast.makeText(context, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
                timeEditCallback?.onTimeUpdated()
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_saving, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation(block: TimeRecordBlock) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_record)
            .setMessage(R.string.delete_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        if (block.checkOut != null) {
                            repository.deleteRecord(block.checkOut.id)
                        }
                        repository.deleteRecord(block.checkIn.id)
                        timeEditCallback?.onRecordDeleted(block.checkIn.id)
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, getString(R.string.error_deleting, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}