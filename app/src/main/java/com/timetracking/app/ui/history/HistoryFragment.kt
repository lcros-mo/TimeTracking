package com.timetracking.app.ui.history

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.ui.history.adapter.TimeRecordBlockAdapter
import com.timetracking.app.core.data.model.TimeRecordBlock
import com.timetracking.app.core.utils.DateTimeUtils
import com.timetracking.app.core.utils.PDFManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment(), TimeEditBottomSheet.Callback {
    private lateinit var repository: TimeRecordRepository
    private lateinit var adapter: TimeRecordBlockAdapter
    private lateinit var weekTabs: TabLayout
    private var currentWeekStart: Date = DateTimeUtils.getStartOfWeek(Date())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = TimeRecordRepository((requireActivity().application as TimeTrackingApp).database.timeRecordDao())

        setupToolbar(view)
        setupRecyclerView(view)
        setupTabs(view)
        setupExportButton(view)
        setupAddRecordButton(view)
        loadCurrentWeek()
    }

    private fun setupToolbar(view: View) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupAddRecordButton(view: View) {
        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addRecordButton).setOnClickListener {
            // Mostrar el diálogo para añadir un nuevo registro
            AddRecordDialog.newInstance(object : AddRecordDialog.Callback {
                override fun onRecordAdded() {
                    loadCurrentWeek()
                    setupTabs(view)
                }
            }).show(childFragmentManager, "addRecord")
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recordsList)
        adapter = TimeRecordBlockAdapter(
            onCheckInClick = { record ->
                showTimeEditDialog(record)
            },
            onCheckOutClick = { record ->
                showTimeEditDialog(record)
            },
            onBlockUpdated = {
                loadCurrentWeek()
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupTabs(view: View) {
        weekTabs = view.findViewById(R.id.weekTabs)
        weekTabs.removeAllTabs() // Limpiar pestañas existentes

        lifecycleScope.launch {
            try {
                // Obtener solo semanas no exportadas (máximo 3)
                val unexportedWeeks = repository.getUnexportedWeeks()

                if (unexportedWeeks.isEmpty()) {
                    view.findViewById<RecyclerView>(R.id.recordsList)?.visibility = View.GONE
                    view.findViewById<TextView>(R.id.emptyStateText)?.visibility = View.VISIBLE
                    view.findViewById<TextView>(R.id.emptyStateText)?.text = "No hay semanas pendientes de exportar"
                    view.findViewById<MaterialButton>(R.id.exportButton)?.isEnabled = false
                    return@launch
                }

                view.findViewById<RecyclerView>(R.id.recordsList)?.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.emptyStateText)?.visibility = View.GONE
                view.findViewById<MaterialButton>(R.id.exportButton)?.isEnabled = true

                // Formato para mostrar las fechas
                val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
                val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

                // Crear pestañas para cada semana no exportada
                unexportedWeeks.forEach { weekStart ->
                    val endOfWeek = DateTimeUtils.addDays(weekStart, 6)
                    val tabText = "${dateFormat.format(weekStart)}-${dateFormat.format(endOfWeek)} ${monthFormat.format(endOfWeek)}"

                    weekTabs.addTab(weekTabs.newTab().apply {
                        text = tabText
                        tag = weekStart.time
                    })
                }

                // Seleccionar la pestaña más reciente
                if (weekTabs.tabCount > 0) {
                    weekTabs.selectTab(weekTabs.getTabAt(weekTabs.tabCount - 1))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error al cargar semanas: ${e.message}")
            }
        }

        weekTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentWeekStart = Date(tab.tag as Long)
                loadCurrentWeek()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadCurrentWeek() {
        lifecycleScope.launch {
            val records = repository.getRecordsForWeek(currentWeekStart)
            val blocks = TimeRecordBlock.createBlocks(records)
            adapter.submitList(blocks)
        }
    }

    private fun showTimeEditDialog(record: TimeRecord) {
        val calendar = Calendar.getInstance().apply { time = record.date }
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                lifecycleScope.launch {
                    repository.updateRecordTime(record.id, hourOfDay, minute)
                    loadCurrentWeek()
                    showToast("Registro actualizado")
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun setupExportButton(view: View) {
        view.findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val canExport = repository.canExportWeek(currentWeekStart)

                    if (!canExport) {
                        showToast("Debes exportar las semanas en orden cronológico")
                        return@launch
                    }

                    val records = repository.getRecordsForWeek(currentWeekStart)
                    if (records.isEmpty()) {
                        showToast("No hay registros para exportar en esta semana")
                        return@launch
                    }

                    // Mostrar diálogo de confirmación
                    AlertDialog.Builder(requireContext())
                        .setTitle("Exportar semana")
                        .setMessage("Una vez exportada, no podrás modificar esta semana. ¿Estás seguro de querer exportarla?")
                        .setPositiveButton("Exportar") { _, _ ->
                            lifecycleScope.launch {
                                try {
                                    val blocks = TimeRecordBlock.createBlocks(records)
                                    PDFManager(requireContext()).createAndUploadPDF(blocks)

                                    // Marcar como exportada
                                    repository.markWeekAsExported(currentWeekStart)

                                    showToast("Semana exportada correctamente")

                                    // Actualizar las pestañas para reflejar cambios
                                    setupTabs(view)
                                } catch (e: Exception) {
                                    showToast("Error al exportar: ${e.message}")
                                }
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .create()
                        .show()
                } catch (e: Exception) {
                    showToast("Error al verificar registros: ${e.message}")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }

    // Implementación de los métodos de la interfaz TimeEditBottomSheet.Callback
    override fun onTimeUpdated() {
        loadCurrentWeek()
    }

    override fun onRecordDeleted(recordId: Long) {
        loadCurrentWeek()
        setupTabs(view ?: return)
    }
}