package com.timetracking.app.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

/**
 * Estado UI para la pantalla principal
 */
data class MainUiState(
    val isCheckedIn: Boolean = false,
    val checkInTime: Date? = null,
    val lastCheckText: String = "Sin fichajes registrados",
    val error: String? = null
)

/**
 * Estadísticas de tiempo para mostrar en la UI
 */
data class TimeStats(
    val hours: Long = 0,
    val minutes: Long = 0,
    val totalMinutes: Long = 0
) {
    override fun toString(): String = "${hours}h ${minutes}m"
}

/**
 * ViewModel para la pantalla principal que gestiona los estados de fichaje
 * y proporciona datos para la UI.
 */
class MainViewModel(private val repository: TimeRecordRepository) : ViewModel() {

    // Estados UI
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Último registro
    private val _lastRecord = MutableLiveData<TimeRecord?>()
    val lastRecord: LiveData<TimeRecord?> = _lastRecord

    // Tiempo diario
    private val _todayTime = MutableLiveData<TimeStats>()
    val todayTime: LiveData<TimeStats> = _todayTime

    // Tiempo semanal
    private val _weeklyTime = MutableLiveData<TimeStats>()
    val weeklyTime: LiveData<TimeStats> = _weeklyTime

    init {
        loadLastState()
    }

    /**
     * Carga el último estado de fichaje
     */
    fun loadLastState() {
        viewModelScope.launch {
            try {
                val record = repository.getLastRecord()
                _lastRecord.value = record

                // Actualizar el estado de la UI basado en el último registro
                record?.let {
                    when (it.type) {
                        RecordType.CHECK_IN -> {
                            _uiState.value = _uiState.value.copy(
                                isCheckedIn = true,
                                checkInTime = it.date,
                                lastCheckText = "Último fichaje: Entrada a las ${formatTime(it.date)}"
                            )
                        }
                        RecordType.CHECK_OUT -> {
                            _uiState.value = _uiState.value.copy(
                                isCheckedIn = false,
                                checkInTime = null,
                                lastCheckText = "Último fichaje: Salida a las ${formatTime(it.date)}"
                            )
                        }
                    }
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = false,
                        checkInTime = null,
                        lastCheckText = "Sin fichajes registrados"
                    )
                }

                updateTodayTime()
                updateWeeklyTime()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al cargar el último estado: ${e.message}"
                )
            }
        }
    }

    /**
     * Gestiona la acción de fichar entrada o salida
     */
    fun handleCheckInOut() {
        val currentTime = Date()

        viewModelScope.launch {
            try {
                val lastRecord = repository.getLastRecord()

                // Determinar si debe ser entrada o salida
                val shouldBeCheckIn = lastRecord?.type == RecordType.CHECK_OUT || lastRecord == null
                val shouldBeCheckOut = lastRecord?.type == RecordType.CHECK_IN

                if (shouldBeCheckIn) {
                    repository.insertRecord(currentTime, RecordType.CHECK_IN)
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = true,
                        checkInTime = currentTime,
                        lastCheckText = "Último fichaje: Entrada a las ${formatTime(currentTime)}"
                    )
                } else if (shouldBeCheckOut) {
                    repository.insertRecord(currentTime, RecordType.CHECK_OUT)
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = false,
                        checkInTime = null,
                        lastCheckText = "Último fichaje: Salida a las ${formatTime(currentTime)}"
                    )
                }

                updateTodayTime()
                updateWeeklyTime()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al procesar el fichaje: ${e.message}"
                )
            }
        }
    }

    /**
     * Actualiza el tiempo trabajado hoy
     */
    private fun updateTodayTime() {
        viewModelScope.launch {
            try {
                val todayRecords = repository.getDayRecords(DateTimeUtils.clearSeconds(Date()))
                var totalMinutes = 0L

                // Ordenar los registros por fecha
                val sortedRecords = todayRecords.sortedBy { it.date }

                var i = 0
                while (i < sortedRecords.size - 1) {
                    val current = sortedRecords[i]
                    val next = sortedRecords[i + 1]

                    if (current.type == RecordType.CHECK_IN && next.type == RecordType.CHECK_OUT) {
                        val diffInMillis = next.date.time - current.date.time
                        totalMinutes += diffInMillis / (1000 * 60)
                        i += 2
                    } else {
                        i++
                    }
                }

                // Si el último registro es CHECK_IN, añadir tiempo hasta ahora
                if (sortedRecords.lastOrNull()?.type == RecordType.CHECK_IN) {
                    val diffInMillis = Date().time - sortedRecords.last().date.time
                    totalMinutes += diffInMillis / (1000 * 60)
                }

                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60

                _todayTime.value = TimeStats(hours, minutes, totalMinutes)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al calcular tiempo diario: ${e.message}"
                )
            }
        }
    }

    /**
     * Actualiza el tiempo trabajado esta semana
     */
    private fun updateWeeklyTime() {
        viewModelScope.launch {
            try {
                val weekStart = DateTimeUtils.getStartOfWeek(Date())
                val records = repository.getRecordsForWeek(weekStart)

                // Procesar registros para calcular tiempo semanal
                var totalMinutes = 0L

                // Ordenar por fecha y agrupar entradas y salidas
                val sortedRecords = records.sortedBy { it.date }

                var i = 0
                while (i < sortedRecords.size - 1) {
                    val current = sortedRecords[i]
                    val next = sortedRecords[i + 1]

                    if (current.type == RecordType.CHECK_IN && next.type == RecordType.CHECK_OUT) {
                        val diffInMillis = next.date.time - current.date.time
                        totalMinutes += diffInMillis / (1000 * 60)
                        i += 2
                    } else {
                        i++
                    }
                }

                // Si el último registro es CHECK_IN, añadir tiempo hasta ahora
                if (sortedRecords.lastOrNull()?.type == RecordType.CHECK_IN) {
                    val diffInMillis = Date().time - sortedRecords.last().date.time
                    totalMinutes += diffInMillis / (1000 * 60)
                }

                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60

                _weeklyTime.value = TimeStats(hours, minutes, totalMinutes)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al calcular tiempo semanal: ${e.message}"
                )
            }
        }
    }

    /**
     * Formatea una fecha para mostrar solo la hora y minutos
     */
    private fun formatTime(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$hour:$minute"
    }

    /**
     * Limpia los errores
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}