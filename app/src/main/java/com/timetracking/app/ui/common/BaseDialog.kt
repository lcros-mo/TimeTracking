package com.timetracking.app.ui.common


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

/**
 * Clase base para todos los diálogos de la aplicación.
 * Proporciona funcionalidad común y manejo de ViewBinding.
 */
abstract class BaseDialog<T : ViewBinding> : DialogFragment() {

    private var _binding: T? = null
    protected val binding get() = _binding!!

    /**
     * Método abstracto que las subclases deben implementar para inflar el binding
     */
    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): T

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    /**
     * Método para configurar la UI del diálogo
     */
    open fun setupUI() {
        // Por defecto no hace nada, las subclases sobrescribirán según necesiten
    }

    /**
     * Método para observar el ViewModel
     */
    open fun observeViewModel() {
        // Por defecto no hace nada, las subclases sobrescribirán según necesiten
    }

    /**
     * Muestra un mensaje Toast
     */
    protected fun showToast(message: String) {
        (requireActivity() as? ToastHandler)?.showToast(message)
            ?: android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Interfaz para manejar toasts desde la actividad
     */
    interface ToastHandler {
        fun showToast(message: String)
    }
}