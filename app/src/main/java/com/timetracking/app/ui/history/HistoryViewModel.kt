package com.timetracking.app.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.model.TimeRecordBlock
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.utils.DateTimeUtils
import com.timetracking.app.core.utils.PDFManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Estado UI para la pantalla de historial
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val selectedWeek: WeekData? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Datos de semana para la UI
 */
data class WeekData(
    val startDate: Date,
    val displayText: String
)

/**
 * ViewModel para la pantalla de historial que gestiona los registros de fichaje
 * y sus operaciones.
 */
class HistoryViewModel(
    private val repository: TimeRecordRepository,
    private val pdfManager: PDFManager? = null
) : ViewModel() {

    // Estado UI general
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // Bloques de tiempo (pares de entrada/salida)
    private val _timeBlocks = MutableLiveData<List<TimeRecordBlock>>()
    val timeBlocks: LiveData<List<TimeRecordBlock>> = _timeBlocks

    // Semanas disponibles
    private val _availableWeeks = MutableLiveData<List<WeekData>>()
    val availableWeeks: LiveData<List<WeekData>> = _availableWeeks

    init {
        loadAvailableWeeks()
    }

    /**
     * Carga las semanas que tienen registros
     */
    fun loadAvailableWeeks() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Calcular el inicio de la semana actual
                val today = Calendar.getInstance()
                val currentWeekStart = DateTimeUtils.getStartOfWeek(today.time)

                // Calendar para ir retrocediendo semanas
                val calendar = Calendar.getInstance()

                // Lista para almacenar las semanas con registros
                val weeksWithRecords = mutableListOf<WeekData>()

                // Retrocedemos hasta 4 semanas revisando si hay registros
                for (i in 0 until 4) {
                    val weekStart = DateTimeUtils.getStartOfWeek(calendar.time)

                    // Verificamos que la semana no sea futura y tenga registros
                    if (weekStart.time <= currentWeekStart.time) {
                        val records = repository.getRecordsForWeek(weekStart)
                        if (records.isNotEmpty()) {
                            val endOfWeek = Calendar.getInstance().apply {
                                time = weekStart
                                add(Calendar.DAY_OF_WEEK, 6)
                            }.time

                            // Formato para mostrar fechas
                            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
                            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

                            val displayText = "${dateFormat.format(weekStart)}-${dateFormat.format(endOfWeek)} ${monthFormat.format(endOfWeek)}"

                            weeksWithRecords.add(0, WeekData(weekStart, displayText))
                        }
                    }

                    calendar.add(Calendar.WEEK_OF_YEAR, -1)
                }

                _availableWeeks.value = weeksWithRecords

                // Si hay semanas disponibles, seleccionar la más reciente por defecto
                if (weeksWithRecords.isNotEmpty()) {
                    val mostRecentWeek = weeksWithRecords.last()
                    _uiState.value = _uiState.value.copy(
                        selectedWeek = mostRecentWeek,
                        isLoading = false
                    )
                    loadWeekRecords(mostRecentWeek.startDate)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al cargar semanas: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Carga los registros de una semana específica
     */
    fun loadWeekRecords(weekStart: Date) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val records = repository.getRecordsForWeek(weekStart)
                val blocks = TimeRecordBlock.createBlocks(records)

                _timeBlocks.value = blocks
                _uiState.value = _uiState.value.copy(
                    selectedWeek = _availableWeeks.value?.find { it.startDate == weekStart },
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al cargar registros: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Actualiza la hora de un registro
     */
    fun updateRecordTime(recordId: Long, hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                repository.updateRecordTime(recordId, hour, minute)

                // Recargar datos
                _uiState.value.selectedWeek?.let {
                    loadWeekRecords(it.startDate)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Registro actualizado correctamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al actualizar registro: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Elimina un registro
     */
    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                repository.deleteRecord(recordId)

                // Recargar datos
                _uiState.value.selectedWeek?.let {
                    loadWeekRecords(it.startDate)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Registro eliminado correctamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al eliminar registro: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Añade un nuevo registro
     */
    fun addRecord(date: Date, type: RecordType, note: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                repository.insertRecord(date, type, note)

                // Recargar datos
                _uiState.value.selectedWeek?.let {
                    loadWeekRecords(it.startDate)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Registro añadido correctamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al añadir registro: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Exporta los registros a PDF
     */
    fun exportWeekToPdf(onComplete: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, isExporting = true)

                val currentBlocks = _timeBlocks.value
                if (currentBlocks.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isExporting = false,
                        error = "No hay registros para exportar"
                    )
                    onComplete(false, "No hay registros para exportar")
                    return@launch
                }

                // Utilizar PDFManager si está disponible, de lo contrario fallar
                val pdfManager = this@HistoryViewModel.pdfManager
                    ?: throw IllegalStateException("PDFManager no disponible")

                pdfManager.createAndUploadPDF(currentBlocks)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isExporting = false,
                    message = "Exportación completada"
                )
                onComplete(true, "Exportación completada")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isExporting = false,
                    error = "Error al exportar: ${e.message}"
                )
                onComplete(false, "Error al exportar: ${e.message}")
            }
        }
    }

    /**
     * Limpia los mensajes de UI
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            message = null
        )
    }
}