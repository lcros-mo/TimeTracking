package com.timetracking.app.core.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
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

// Clase para almacenar la información de registros extraída del PDF
data class RecordInfo(
    val date: String,
    val entryTime: String,
    val exitTime: String,
    val duration: String,
    val comment: String = ""  // Añadido campo de comentario
)

// Mantener la dirección del servidor para compatibilidad
object ServerConfig {
    private const val SERVER_IP = "80.32.125.224"
    private const val SERVER_PORT = 5000
    const val SERVER_URL_BASE = "https://$SERVER_IP:$SERVER_PORT"
    const val SERVER_URL_UPLOAD = "$SERVER_URL_BASE/upload"
    const val SERVER_URL_CHECK_FILE = "$SERVER_URL_BASE/files/check"
    const val SERVER_URL_DOWNLOAD = "$SERVER_URL_BASE/files/download"
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

    private fun getUserDisplayName(): String {
        // 1. Intentar obtener de la ubicación estándar de la app
        val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val userName = authPrefs.getString("user_name", null)
        if (!userName.isNullOrEmpty()) {
            return userName
        }

        val userEmail = authPrefs.getString("user_email", null)
        if (!userEmail.isNullOrEmpty()) {
            // Si tenemos email pero no nombre, formateamos el email
            val namePart = userEmail.substringBefore("@")
            if (namePart.contains(".")) {
                return namePart.split(".").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                }
            }
            return namePart.replaceFirstChar { it.uppercase() }
        }

        val googleSignInPrefs = context.getSharedPreferences("google_sign_in", Context.MODE_PRIVATE)
        val googleUserName = googleSignInPrefs.getString("display_name", null)
        if (!googleUserName.isNullOrEmpty()) {
            return googleUserName
        }

        val googleUserEmail = googleSignInPrefs.getString("email", null)
        if (!googleUserEmail.isNullOrEmpty()) {
            // Formatear el email de la misma manera
            val namePart = googleUserEmail.substringBefore("@")
            if (namePart.contains(".")) {
                return namePart.split(".").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                }
            }
            return namePart.replaceFirstChar { it.uppercase() }
        }

        val allPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).all +
                context.getSharedPreferences("google_sign_in", Context.MODE_PRIVATE).all +
                context.getSharedPreferences("accounts", Context.MODE_PRIVATE).all

        val emailPattern = "[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()

        for ((_, value) in allPrefs) {
            if (value is String && emailPattern.matches(value)) {
                val namePart = value.substringBefore("@")
                if (namePart.contains(".")) {
                    return namePart.split(".").joinToString(" ") {
                        it.replaceFirstChar { char -> char.uppercase() }
                    }
                }
                return namePart.replaceFirstChar { it.uppercase() }
            }
        }
        return "Usuario Desconocido"
    }

    private fun generateFileName(): String {
        val userIdentifier = getUserDisplayName().replace(" ", "_")
        return "RegistroHorario_${userIdentifier}.pdf"
    }

    suspend fun createAndUploadPDF(blocks: List<TimeRecordBlock>) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = generateFileName()
                val pdfData: ByteArray

                // Verificar si el archivo ya existe en el servidor
                if (checkFileExists(fileName)) {
                    // Descargar archivo existente, extraer registros, combinar con nuevos y actualizar
                    val existingPdf = downloadExistingPdf(fileName)
                    pdfData = createPDFWithCombinedRecords(existingPdf, blocks)
                } else {
                    // Crear un nuevo PDF desde cero
                    pdfData = createPDFInMemory(blocks)
                }

                // Subir el PDF actualizado o nuevo al servidor
                uploadToServer(fileName, pdfData)

                // Guardar una copia local también para tener respaldo
                saveLocalCopy(fileName, pdfData)
            } catch (e: Exception) {
                // En caso de error, intentar guardar localmente
                val pdfData = createPDFInMemory(blocks)
                saveLocally(pdfData)
                throw e
            }
        }
    }

    private fun checkFileExists(fileName: String): Boolean {
        val request = Request.Builder()
            .url("${ServerConfig.SERVER_URL_CHECK_FILE}?name=$fileName")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "{\"exists\": false}"
                    responseBody.contains("\"exists\":true")
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun downloadExistingPdf(fileName: String): ByteArray {
        val request = Request.Builder()
            .url("${ServerConfig.SERVER_URL_DOWNLOAD}?name=$fileName")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.bytes() ?: ByteArray(0)
            } else {
                throw IOException("Failed to download existing PDF: ${response.code}")
            }
        }
    }

    // Extraer registros del PDF existente
    private fun extractRecordsFromExistingPDF(existingPdfData: ByteArray): List<RecordInfo> {
        val extractedRecords = mutableListOf<RecordInfo>()

        try {
            // Usar PdfTextExtractor para obtener el texto del PDF
            val pdfReader = PdfReader(existingPdfData.inputStream())
            val pdfDocument = PdfDocument(pdfReader)

            // Extraer texto de cada página
            for (i in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(i)
                val text = PdfTextExtractor.getTextFromPage(page)

                // Parsear el texto para encontrar registros
                // Este regex busca fechas, horas de entrada y salida, duración y comentarios
                val recordPattern = "(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2})\\s+(\\d{2}:\\d{2}|Pendiente)\\s+(\\d+h\\s+\\d+m)\\s*([\\s\\S]*?)(?=\\d{2}/\\d{2}/\\d{4}|$)".toRegex()

                val matches = recordPattern.findAll(text)
                matches.forEach { match ->
                    val (date, entryTime, exitTime, duration, comment) = match.destructured
                    extractedRecords.add(RecordInfo(date, entryTime, exitTime, duration, comment.trim()))
                }
            }

            pdfReader.close()
        } catch (e: Exception) {
            // Log el error pero continuar
            Log.e("PDFManager", "Error extracting records from PDF: ${e.message}")
        }

        return extractedRecords
    }

    // Combinar registros existentes con nuevos y crear un PDF actualizado
    private fun createPDFWithCombinedRecords(existingPdfData: ByteArray, newBlocks: List<TimeRecordBlock>): ByteArray {
        // Extraer registros del PDF existente
        val existingRecords = extractRecordsFromExistingPDF(existingPdfData)

        // Convertir los TimeRecordBlocks a RecordInfo para comparación
        val newRecordsInfo = newBlocks.map { block ->
            RecordInfo(
                dateFormat.format(block.date),
                timeFormat.format(block.checkIn.date),
                block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente",
                "${block.duration / 60}h ${block.duration % 60}m",
                block.checkIn.note ?: block.checkOut?.note ?: ""  // Añadir el comentario
            )
        }

        // Filtrar solo los registros que no existen ya en el PDF
        val uniqueNewRecords = newRecordsInfo.filter { newRecord ->
            !existingRecords.any { it.date == newRecord.date &&
                    it.entryTime == newRecord.entryTime &&
                    it.exitTime == newRecord.exitTime }
        }

        // Si no hay registros nuevos únicos, devolver el PDF existente
        if (uniqueNewRecords.isEmpty()) {
            return existingPdfData
        }

        // Crear un nuevo PDF con todos los registros (existentes + nuevos únicos)
        val allRecords = existingRecords + uniqueNewRecords
        return createPDFWithAllRecords(allRecords)
    }

    // Crear un PDF con una lista combinada de RecordInfo
    private fun createPDFWithAllRecords(records: List<RecordInfo>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val userName = getUserDisplayName()

        PdfWriter(outputStream).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                document.add(
                    Paragraph("$userName")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                )

                document.add(
                    Paragraph("Actualizado: ${dateFormat.format(Date())}")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFontSize(10f)
                )

                // Crear tabla con todos los registros (incluyendo observaciones)
                val table = Table(floatArrayOf(130f, 80f, 80f, 80f, 180f))
                    .setTextAlignment(TextAlignment.CENTER)

                table.addHeaderCell("Fecha")
                table.addHeaderCell("Entrada")
                table.addHeaderCell("Salida")
                table.addHeaderCell("Duración")
                table.addHeaderCell("Observaciones")

                records.forEach { record ->
                    table.addCell(record.date)
                    table.addCell(record.entryTime)
                    table.addCell(record.exitTime)
                    table.addCell(record.duration)
                    table.addCell(record.comment)  // Añadir comentario
                }

                document.add(table)

                // Calcular la suma total de horas
                var totalHours = 0
                var totalMinutes = 0

                records.forEach { record ->
                    val durationPattern = "(\\d+)h\\s+(\\d+)m".toRegex()
                    val match = durationPattern.find(record.duration)
                    if (match != null) {
                        val (hours, minutes) = match.destructured
                        totalHours += hours.toInt()
                        totalMinutes += minutes.toInt()
                    }
                }

                // Convertir minutos excedentes a horas
                totalHours += totalMinutes / 60
                totalMinutes %= 60

                document.add(
                    Paragraph("Total: ${totalHours}h ${totalMinutes}m")
                        .setTextAlignment(TextAlignment.RIGHT)
                )
            }
        }
        return outputStream.toByteArray()
    }

    private fun createPDFInMemory(blocks: List<TimeRecordBlock>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val userName = getUserDisplayName()

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
        val table = Table(floatArrayOf(130f, 80f, 80f, 80f, 180f))  // Añadir columna para observaciones
            .setTextAlignment(TextAlignment.CENTER)

        table.addHeaderCell("Fecha")
        table.addHeaderCell("Entrada")
        table.addHeaderCell("Salida")
        table.addHeaderCell("Duración")
        table.addHeaderCell("Observaciones")  // Nueva columna

        blocks.forEach { block ->
            table.addCell(dateFormat.format(block.date))
            table.addCell(timeFormat.format(block.checkIn.date))
            table.addCell(block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente")

            val hours = block.duration / 60
            val minutes = block.duration % 60
            table.addCell("${hours}h ${minutes}m")

            // Añadir la observación si existe
            val comment = block.checkOut?.note ?: block.checkIn.note ?: ""
            table.addCell(comment)
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
            .url(ServerConfig.SERVER_URL_UPLOAD)
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

    private fun saveLocalCopy(fileName: String, pdfData: ByteArray) {
        // Guardar en directorio de la aplicación
        val appDir = context.getExternalFilesDir("pdfs") ?: context.filesDir
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, fileName)
        file.writeBytes(pdfData)
    }
}