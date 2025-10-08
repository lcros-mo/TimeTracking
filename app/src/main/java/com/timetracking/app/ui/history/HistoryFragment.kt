package com.timetracking.app.ui.history

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.timetracking.app.R
import com.timetracking.app.core.di.ServiceLocator
import com.timetracking.app.databinding.FragmentHistoryBinding
import com.timetracking.app.ui.history.adapter.TimeRecordBlockAdapter
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*
import com.timetracking.app.core.utils.TrustAllCerts
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class HistoryFragment : Fragment(), TimeEditBottomSheet.Callback {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Usar ViewModel con el factory de ServiceLocator
    private val viewModel: HistoryViewModel by viewModels {
        ServiceLocator.provideHistoryViewModelFactory(requireContext())
    }

    private lateinit var adapter: TimeRecordBlockAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        setupToolbar()
        setupRecyclerView()
        setupTabs()
        setupButtons()
    }

    private fun setupToolbar() {
        // Configurar callback para manejar el botón de navegación hacia atrás
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Remover este callback y ejecutar el comportamiento por defecto de MainActivity
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        binding.toolbar.setNavigationOnClickListener {
            callback.handleOnBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = TimeRecordBlockAdapter(
            onCheckInClick = { record ->
                showTimeEditDialog(record)
            },
            onCheckOutClick = { record ->
                showTimeEditDialog(record)
            },
            onBlockUpdated = {
                viewModel.loadAvailableWeeks()
            }
        )

        binding.recordsList.apply {
            adapter = this@HistoryFragment.adapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupTabs() {
        binding.weekTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val weekStart = tab.tag as? Long ?: return
                viewModel.loadWeekRecords(java.util.Date(weekStart))
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupButtons() {
        binding.exportButton.setOnClickListener {
            showExportConfirmation()
        }

        binding.addRecordButton.setOnClickListener {
            AddRecordDialog.newInstance(object : AddRecordDialog.Callback {
                override fun onRecordAdded() {
                    viewModel.loadAvailableWeeks()
                }
            }).show(childFragmentManager, "addRecord")
        }
    }

    private fun observeViewModel() {
        // Observar estado general de UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // Mostrar/ocultar loading
                binding.recordsList.visibility = if (state.isLoading) View.GONE else View.VISIBLE

                // Mostrar mensajes de error
                state.error?.let {
                    showToast(it)
                    viewModel.clearMessages()
                }

                // Mostrar mensajes de éxito
                state.message?.let {
                    showToast(it)
                    viewModel.clearMessages()
                }

                // Actualizar estado del botón de exportar
                binding.exportButton.isEnabled = !state.isLoading && !state.isExporting
            }
        }

        // Observar semanas disponibles
        viewModel.availableWeeks.observe(viewLifecycleOwner) { weeks ->
            updateWeekTabs(weeks)
        }

        // Observar bloques de tiempo
        viewModel.timeBlocks.observe(viewLifecycleOwner) { blocks ->
            adapter.submitList(blocks)
            updateWeeklySummary(blocks)
        }
    }

    private fun updateWeekTabs(weeks: List<WeekData>) {
        binding.weekTabs.removeAllTabs()

        if (weeks.isEmpty()) {
            binding.recordsList.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
            binding.exportButton.isEnabled = false
            binding.weekSummaryCard.visibility = View.GONE
            return
        }

        binding.recordsList.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE
        binding.exportButton.isEnabled = true
        binding.weekSummaryCard.visibility = View.VISIBLE

        // Agregar pestañas para cada semana
        weeks.forEach { week ->
            binding.weekTabs.addTab(binding.weekTabs.newTab().apply {
                text = week.displayText
                tag = week.startDate.time
            })
        }

        // Seleccionar la pestaña más reciente
        if (binding.weekTabs.tabCount > 0) {
            binding.weekTabs.selectTab(binding.weekTabs.getTabAt(binding.weekTabs.tabCount - 1))
        }
    }

    private fun updateWeeklySummary(blocks: List<com.timetracking.app.core.data.model.TimeRecordBlock>) {
        if (blocks.isEmpty()) {
            binding.weekSummaryCard.visibility = View.GONE
            return
        }

        binding.weekSummaryCard.visibility = View.VISIBLE

        var totalMinutes = 0L
        blocks.forEach { block ->
            totalMinutes += block.duration
        }

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        binding.weeklyTotalText.text = getString(R.string.weekly_total, hours, minutes)
    }

    private fun showTimeEditDialog(record: com.timetracking.app.core.data.model.TimeRecord) {
        // Buscar el bloque completo que contiene este registro
        val currentBlocks = viewModel.timeBlocks.value ?: return
        val block = currentBlocks.find {
            it.checkIn.id == record.id || it.checkOut?.id == record.id
        } ?: return

        TimeEditBottomSheet.newInstance(block, this)
            .show(childFragmentManager, "timeEdit")
    }

    private fun showExportConfirmation() {
        val currentBlocks = viewModel.timeBlocks.value
        if (currentBlocks.isNullOrEmpty()) {
            showToast(getString(R.string.no_records_to_export))
            return
        }

        // Calcular el total de horas
        var totalMinutes = 0L
        currentBlocks.forEach { block ->
            totalMinutes += block.duration
        }

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val totalTimeText = "${hours}h ${minutes}m"
        val totalHours = totalMinutes / 60.0

        // Verificar si excede las 40 horas
        val isOvertime = totalHours > 40.0

        val messageText = if (isOvertime) {
            val overtimeHours = totalHours - 37.5
            val overtimeText = String.format(Locale.getDefault(), "%.1f", overtimeHours)

            "⚠️ <b>ATENCIÓ:</b> Has trabajado <b>$totalTimeText</b> esta semana.<br><br>" +
                    "Excedes las <b>37.5h reglamentarias</b> en <b>$overtimeText horas</b>.<br><br>" +
                    "Se enviará una <b>notificación automática a administración</b>.<br><br>" +
                    "¿Continuar con la exportación?"
        } else {
            getString(R.string.export_week_confirmation, totalTimeText)
        }

        val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(messageText, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(messageText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isOvertime) "⚠️ Horas Extras" else getString(R.string.export_week_title))
            .setMessage(message)
            .setPositiveButton(R.string.export) { _, _ ->
                // Si hay horas extras, enviar notificación
                if (isOvertime) {
                    // Usar el PDFManager existente para enviar la notificación
                    sendOvertimeAlert(userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Usuario",
                        userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "email@desconocido.com",
                        totalHours = totalHours)
                }
                exportCurrentWeek()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun sendOvertimeAlert(userName: String, userEmail: String, totalHours: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val weekStart = viewModel.uiState.value.selectedWeek?.startDate ?: Date()
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                // Cliente igual que PDFManager
                val client = okhttp3.OkHttpClient.Builder()
                    .hostnameVerifier { _, _ -> true }
                    .sslSocketFactory(
                        TrustAllCerts.createSSLSocketFactory(),
                        TrustAllCerts.trustManager
                    )
                    .build()

                val json = """
                {
                    "userName": "$userName",
                    "userEmail": "$userEmail",
                    "totalHours": $totalHours,
                    "weekStartDate": "${dateFormat.format(weekStart)}"
                }
            """.trimIndent()

                val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())

                val request = okhttp3.Request.Builder()
                    .url("https://80.32.125.224:5000/upload/send-overtime")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    launch(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            showToast("✅ Notificación enviada a administración")
                        } else {
                            showToast("Response: ${response.code}")
                        }
                    }
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    showToast("Error: ${e.message}")
                }
            }
        }
    }

    private fun exportCurrentWeek() {
        viewModel.exportWeekToPdf { success, message ->
            showToast(message)
            if (success) {
                // Recargar las semanas disponibles después de exportar
                viewModel.loadAvailableWeeks()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }

    // Implementación de TimeEditBottomSheet.Callback
    override fun onTimeUpdated() {
        // Recargar la semana actual
        viewModel.uiState.value.selectedWeek?.let {
            viewModel.loadWeekRecords(it.startDate)
        }
    }

    override fun onRecordDeleted(recordId: Long) {
        viewModel.deleteRecord(recordId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}