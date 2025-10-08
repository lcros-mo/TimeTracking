package com.timetracking.app.ui.home

import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    companion object {
        private const val TAG = "MainViewModel"
    }

    // Mutex para prevenir race conditions en handleCheckInOut
    private val checkInOutMutex = Mutex()

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
     * MEJORADO: Valida consistencia de registros del día
     */
    fun loadLastState() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Cargando último estado...")
                val todayRecords = repository.getDayRecords(DateTimeUtils.truncateToDay(Date()))
                val record = repository.getLastRecord()
                _lastRecord.value = record

                // Verificar estado basado en registros DEL DÍA
                if (todayRecords.isEmpty()) {
                    Log.d(TAG, "No hay registros hoy, estado: desconectado")
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = false,
                        checkInTime = null,
                        lastCheckText = TimeTrackingApp.appContext.getString(R.string.no_records)
                    )
                    updateTodayTime()
                    updateWeeklyTime()
                    updateOvertimeBalance()
                    return@launch
                }

                // NUEVA VALIDACIÓN: Verificar consistencia antes de determinar estado
                val (isConsistent, _, errorMsg) = TimeRecordValidator.validateNextAction(todayRecords)
                if (!isConsistent) {
                    Log.e(TAG, "Estado inconsistente detectado: $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isCheckedIn = false,
                        checkInTime = null,
                        lastCheckText = "⚠️ Registros inconsistentes",
                        error = errorMsg
                    )
                    return@launch
                }

                val sortedRecords = todayRecords.sortedBy { it.date }
                val lastRecord = sortedRecords.last()

                Log.d(TAG, "Último registro del día: ${lastRecord.type} a las ${formatTime(lastRecord.date)}")

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
                Log.e(TAG, "Error cargando último estado", e)
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_loading_last_state, e.message)
                )
            }
        }
    }

    /**
     * Gestiona la acción de fichar entrada o salida
     * MEJORADO: Usa Mutex para prevenir race conditions
     */
    fun handleCheckInOut() {
        viewModelScope.launch {
            // CRÍTICO: Usar mutex para evitar que múltiples clics causen registros duplicados
            if (!checkInOutMutex.tryLock()) {
                Log.w(TAG, "handleCheckInOut ya en ejecución, ignorando clic duplicado")
                return@launch
            }

            try {
                Log.d(TAG, "=== Iniciando handleCheckInOut ===")
                val currentTime = Date()

                // Obtener todos los registros del día
                val todayRecords = repository.getDayRecords(DateTimeUtils.truncateToDay(Date()))
                Log.d(TAG, "Registros encontrados hoy: ${todayRecords.size}")

                // Validar la acción a realizar
                val (isValid, recordType, errorMessage) = TimeRecordValidator.validateNextAction(todayRecords)

                if (!isValid) {
                    Log.e(TAG, "Validación falló: $errorMessage")
                    _uiState.value = _uiState.value.copy(error = errorMessage)
                    return@launch
                }

                when (recordType) {
                    RecordType.CHECK_IN -> {
                        Log.d(TAG, "Insertando CHECK_IN")
                        repository.insertRecord(currentTime, RecordType.CHECK_IN)
                        _uiState.value = _uiState.value.copy(
                            isCheckedIn = true,
                            checkInTime = currentTime,
                            lastCheckText = TimeTrackingApp.appContext.getString(R.string.last_check_in, formatTime(currentTime))
                        )
                        Log.d(TAG, "CHECK_IN insertado exitosamente")
                    }
                    RecordType.CHECK_OUT -> {
                        Log.d(TAG, "Preparando CHECK_OUT")
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
                                Log.w(TAG, "Validación de tiempo falló: $timeErrorMsg")
                                _uiState.value = _uiState.value.copy(error = timeErrorMsg)
                                return@launch
                            }
                        }

                        Log.d(TAG, "Insertando CHECK_OUT")
                        repository.insertRecord(currentTime, RecordType.CHECK_OUT)
                        _uiState.value = _uiState.value.copy(
                            isCheckedIn = false,
                            checkInTime = null,
                            lastCheckText = TimeTrackingApp.appContext.getString(R.string.last_check_out, formatTime(currentTime))
                        )
                        Log.d(TAG, "CHECK_OUT insertado exitosamente")
                    }
                    null -> {
                        Log.e(TAG, "recordType es null - no debería pasar")
                        _uiState.value = _uiState.value.copy(
                            error = TimeTrackingApp.appContext.getString(R.string.error_determine_action)
                        )
                        return@launch
                    }
                }

                // Actualizar métricas
                updateTodayTime()
                updateWeeklyTime()
                updateOvertimeBalance()
                Log.d(TAG, "=== handleCheckInOut completado ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error en handleCheckInOut", e)
                _uiState.value = _uiState.value.copy(
                    error = TimeTrackingApp.appContext.getString(R.string.error_processing_check, e.message)
                )
            } finally {
                checkInOutMutex.unlock()
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