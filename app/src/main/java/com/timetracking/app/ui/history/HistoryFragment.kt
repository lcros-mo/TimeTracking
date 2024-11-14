package com.timetracking.app.ui.history

import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.timetracking.app.data.model.TimeRecord
import kotlinx.coroutines.launch
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import android.Manifest
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.data.repository.TimeRecordRepository
import com.timetracking.app.utils.DateUtils
import com.timetracking.app.utils.PDFManager
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {
    private lateinit var repository: TimeRecordRepository
    private lateinit var adapter: TimeRecordAdapter
    private lateinit var weekTabs: TabLayout
    private var currentWeekStart: Date = DateUtils.getStartOfWeek(Date())

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
        loadCurrentWeek()
    }

    private fun setupToolbar(view: View) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recordsList)
        adapter = TimeRecordAdapter { record ->
            showTimeEditDialog(record)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupTabs(view: View) {
        weekTabs = view.findViewById(R.id.weekTabs)

        lifecycleScope.launch {
            // Calculamos el inicio de la semana actual
            val today = Calendar.getInstance()
            val currentWeekStart = DateUtils.getStartOfWeek(today.time)

            // Formato para mostrar las fechas
            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

            // Calendar para ir retrocediendo semanas
            val calendar = Calendar.getInstance()

            // Lista para almacenar las semanas con registros
            val weeksWithRecords = mutableListOf<Date>()

            // Retrocedemos hasta 4 semanas revisando si hay registros
            for (i in 0 until 4) {
                val weekStart = DateUtils.getStartOfWeek(calendar.time)

                // Verificamos que la semana no sea futura y tenga registros
                if (weekStart.time <= currentWeekStart.time) {
                    val records = repository.getRecordsForWeek(weekStart)
                    if (records.isNotEmpty()) {
                        weeksWithRecords.add(0, weekStart) // Añadimos al principio para orden cronológico
                    }
                }

                calendar.add(Calendar.WEEK_OF_YEAR, -1)
            }

            // Solo creamos pestañas si hay semanas con registros
            if (weeksWithRecords.isNotEmpty()) {
                weeksWithRecords.forEach { weekStart ->
                    val endOfWeek = Calendar.getInstance().apply {
                        time = weekStart
                        add(Calendar.DAY_OF_WEEK, 6)
                    }.time

                    val tabText = "${dateFormat.format(weekStart)}-${dateFormat.format(endOfWeek)} ${monthFormat.format(endOfWeek)}"

                    weekTabs.addTab(weekTabs.newTab().apply {
                        text = tabText
                        tag = weekStart.time
                    })
                }

                // Seleccionamos la semana más reciente por defecto
                weekTabs.selectTab(weekTabs.getTabAt(weeksWithRecords.size - 1))
            }
        }

        weekTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                this@HistoryFragment.currentWeekStart = Date(tab.tag as Long)
                loadCurrentWeek()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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

    private fun loadCurrentWeek() {
        lifecycleScope.launch {
            val records = repository.getRecordsForWeek(currentWeekStart)
            adapter.submitList(records)
        }
    }

    private fun setupExportButton(view: View) {
        view.findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            if (checkStoragePermission()) {
                exportPDF()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // En Android 10 y superior no necesitamos permiso para escribir en Downloads
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun exportPDF() {
        lifecycleScope.launch {
            try {
                val records = repository.getRecordsForWeek(currentWeekStart)
                if (records.isNotEmpty()) {
                    PDFManager(requireContext()).createOrUpdatePDF(records)
                    showToast("Registro exportado en la carpeta Descargas")
                } else {
                    showToast("No hay registros para exportar en esta semana")
                }
            } catch (e: Exception) {
                showToast("Error al exportar: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportPDF()
                } else {
                    showToast("Necesitamos permiso para guardar el PDF")
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1001
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}