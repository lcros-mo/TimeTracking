package com.timetracking.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.utils.DateTimeUtils
import com.timetracking.app.core.utils.TimeCalculationUtils
import com.timetracking.app.core.utils.TimeRecordValidator
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
    val lastCheckText: String = "",
    val error: String? = null
)

/**
 * Estadísticas de tiempo para mostrar en la UI
 */
data class TimeStats(
    val totalMinutes: Long = 0
) {
    val hours: Long get() = totalMinutes / 60
    val minutes: Long get() = totalMinutes % 60

    val isZero: Boolean get() = totalMinutes == 0L

    override fun toString(): String = "${hours}h ${minutes}m"

    companion object {
        val ZERO = TimeStats(0L)

        fun fromMinutes(minutes: Long): TimeStats = TimeStats(minutes)
    }

    operator fun plus(other: TimeStats): TimeStats =
        TimeStats(totalMinutes + other.totalMinutes)
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

    // Balance de horas extras
    private val _overtimeBalance = MutableLiveData<TimeStats>()
    val overtimeBalance: LiveData<TimeStats> = _overtimeBalance

    init {
        loadLastState()
    }

    /**
     * Método para reiniciar el estado cuando el botón se queda bloqueado
     */
    fun resetState() {
        _uiState.value = MainUiState(
            isCheckedIn = false,
            checkInTime = null,
            lastCheckText = "Estado reiniciado manualmente"
        )

        // Actualizar tiempos
        updateTodayTime()
        updateWeeklyTime()
    }

    /**
     * Carga el último estado de fichaje
     */
    fun loadLastState() {
        viewModelScope.launch {
            try {
                val todayRecords = repository.getDayRecords(DateTimeUtils.truncateToDay(Date()))
                val record = repository.getLastRecord()
                _lastRecord.value = record

                // Verificar estado basado en registros
                if (todayRecords.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = false,
                        checkInTime = null,
                        lastCheckText = TimeTrackingApp.appContext.getString(R.string.no_records)
                    )
                    return@launch
                }

                val sortedRecords = todayRecords.sortedBy { it.date }
                val lastRecord = sortedRecords.last()

                val lastCheckText = if (lastRecord.type == RecordType.CHECK_IN) {
                    TimeTrackingApp.appContext.getString(R.string.last_check_in, formatTime(lastRecord.date))
                } else {
                    TimeTrackingApp.appContext.getString(R.string.last_check_out, formatTime(lastRecord.date))
                }

                _uiState.value = _uiState.value.copy(
                    isCheckedIn = lastRecord.type == RecordType.CHECK_IN,
                    checkInTime = if (lastRecord.type == RecordType.CHECK_IN) lastRecord.date else null,
                    lastCheckText = lastCheckText
                )

                updateTodayTime()
                updateWeeklyTime()
                updateOvertimeBalance()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_loading_last_state, e.message)
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
                // Obtener todos los registros del día
                val todayRecords = repository.getDayRecords(DateTimeUtils.truncateToDay(Date()))

                // Validar la acción a realizar
                val (isValid, recordType, errorMessage) = TimeRecordValidator.validateNextAction(todayRecords)

                if (!isValid) {
                    _uiState.value = _uiState.value.copy(error = errorMessage)
                    return@launch
                }

                when (recordType) {
                    RecordType.CHECK_IN -> {
                        repository.insertRecord(currentTime, RecordType.CHECK_IN)
                        _uiState.value = _uiState.value.copy(
                            isCheckedIn = true,
                            checkInTime = currentTime,
                            lastCheckText = TimeTrackingApp.appContext.getString(R.string.last_check_in, formatTime(currentTime))
                        )
                    }
                    RecordType.CHECK_OUT -> {
                        // Validar que la salida sea posterior a la entrada
                        val lastCheckIn = todayRecords.filter { it.type == RecordType.CHECK_IN }
                            .maxByOrNull { it.date }

                        if (lastCheckIn != null) {
                            // Limpiar las fechas antes de comparar para evitar problemas con segundos/milisegundos
                            val cleanCheckInTime = DateTimeUtils.clearSeconds(lastCheckIn.date)
                            val cleanCurrentTime = DateTimeUtils.clearSeconds(currentTime)

                            val (timeValid, timeErrorMsg) = TimeRecordValidator.validateCheckOutTime(
                                cleanCheckInTime, cleanCurrentTime
                            )

                            if (!timeValid) {
                                _uiState.value = _uiState.value.copy(error = timeErrorMsg)
                                return@launch
                            }
                        }

                        repository.insertRecord(currentTime, RecordType.CHECK_OUT)
                        _uiState.value = _uiState.value.copy(
                            isCheckedIn = false,
                            checkInTime = null,
                            lastCheckText = TimeTrackingApp.appContext.getString(R.string.last_check_out, formatTime(currentTime))
                        )
                    }
                    null -> {
                        _uiState.value = _uiState.value.copy(
                            error = TimeTrackingApp.appContext.getString(R.string.error_determine_action)
                        )
                        return@launch
                    }
                }

                updateTodayTime()
                updateWeeklyTime()
                updateOvertimeBalance()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_processing_check, e.message)
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
                val todayRecords = repository.getDayRecords(Date())
                val totalMinutes = TimeCalculationUtils.calculateWorkingMinutes(
                    records = todayRecords,
                    includeInProgressToday = true
                )

                _todayTime.value = TimeStats.fromMinutes(totalMinutes)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_calculating_daily_time, e.message)
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
                val weekRecords = repository.getRecordsForWeek(weekStart)
                val totalMinutes = TimeCalculationUtils.calculateWorkingMinutes(
                    records = weekRecords,
                    includeInProgressToday = true
                )

                _weeklyTime.value = TimeStats.fromMinutes(totalMinutes)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_calculating_weekly_time, e.message)
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

    /**
     * Actualiza el balance acumulado de horas extras
     */
    /**
     * Actualiza el balance de horas extras - MÉTODO LIMPIO
     */
    private fun updateOvertimeBalance() {
        viewModelScope.launch {
            try {
                val balanceMinutes = repository.getOvertimeBalance()

                _overtimeBalance.value = TimeStats.fromMinutes(kotlin.math.abs(balanceMinutes))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_calculating_balance, e.message)
                )
            }
        }
    }
}