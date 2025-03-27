package com.timetracking.app.core.utils

import android.content.Context
import android.os.Environment
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.timetracking.app.core.data.model.TimeRecordBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.create
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Mantener la dirección del servidor para compatibilidad
object ServerConfig {
    private const val SERVER_IP = "80.32.125.224"
    private const val SERVER_PORT = 5000
    const val SERVER_URL = "https://$SERVER_IP:$SERVER_PORT/upload"
    const val PDF_DIRECTORY = "D:\\Administracio\\GEST ADM PERSONAL\\2024 GEST PERS\\24 REG HORES ADM\\A_PRUEBA_REGISTROS_APP"
}

class PDFManager(private val context: Context) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .hostnameVerifier { _, _ -> true }
        .sslSocketFactory(
            TrustAllCerts.createSSLSocketFactory(),
            TrustAllCerts.trustManager
        )
        .build()

    private fun generateFileName(): String {
        // Obtener la información del usuario de SharedPreferences
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", null)
        val userEmail = sharedPref.getString("user_email", null)

        // Si tenemos un nombre de usuario, usarlo; de lo contrario, extraer del email
        val userIdentifier = if (!userName.isNullOrEmpty()) {
            userName.replace(" ", "_") // Reemplazar espacios con guiones bajos
        } else if (!userEmail.isNullOrEmpty()) {
            // Extraer la parte del correo antes del @
            userEmail.substringBefore("@").replace(".", "_")
        } else {
            "Usuario_Desconocido"
        }

        // Añadir fecha actual al nombre del archivo para hacerlo único
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        return "RegistroHorario_${userIdentifier}_$timestamp.pdf"
    }

    suspend fun createAndUploadPDF(blocks: List<TimeRecordBlock>) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = generateFileName()
                val pdfData = createPDFInMemory(blocks)
                uploadToServer(fileName, pdfData)
            } catch (e: Exception) {
                val pdfData = createPDFInMemory(blocks)
                saveLocally(pdfData)
                throw e
            }
        }
    }

    private fun createPDFInMemory(blocks: List<TimeRecordBlock>): ByteArray {
        val outputStream = ByteArrayOutputStream()

        // Obtener el nombre del usuario desde SharedPreferences para el contenido del PDF
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("Usuario", "user_name")

        PdfWriter(outputStream).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                document.add(
                    Paragraph("$userName")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                )

                document.add(
                    Paragraph("Documento generado: ${dateFormat.format(Date())}")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFontSize(10f)
                )

                val table = createTable(blocks)
                document.add(table)

                val totalMinutes = blocks.sumOf { it.duration }
                val totalHours = totalMinutes / 60
                val remainingMinutes = totalMinutes % 60
                document.add(
                    Paragraph("Total: ${totalHours}h ${remainingMinutes}m")
                        .setTextAlignment(TextAlignment.RIGHT)
                )
            }
        }
        return outputStream.toByteArray()
    }

    private fun createTable(blocks: List<TimeRecordBlock>): Table {
        val table = Table(floatArrayOf(150f, 100f, 100f, 100f))
            .setTextAlignment(TextAlignment.CENTER)

        table.addHeaderCell("Fecha")
        table.addHeaderCell("Entrada")
        table.addHeaderCell("Salida")
        table.addHeaderCell("Duración")

        blocks.forEach { block ->
            table.addCell(dateFormat.format(block.date))
            table.addCell(timeFormat.format(block.checkIn.date))
            table.addCell(block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente")
            val hours = block.duration / 60
            val minutes = block.duration % 60
            table.addCell("${hours}h ${minutes}m")
        }
        return table
    }

    private fun uploadToServer(fileName: String, pdfData: ByteArray) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                create("application/pdf".toMediaTypeOrNull(), pdfData)
            )
            .build()

        val request = Request.Builder()
            .url(ServerConfig.SERVER_URL)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: ${response.code}")
            }
        }
    }

    private fun saveLocally(pdfData: ByteArray) {
        val fileName = generateFileName()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        file.writeBytes(pdfData)
    }
}