package com.timetracking.app.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.timetracking.app.core.data.model.TimeRecordBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class RecordInfo(
    val date: String,
    val entryTime: String,
    val exitTime: String,
    val duration: String,
    val comment: String = "",
    val timestamp: Long = 0L
) {
    companion object {
        fun fromTimeRecordBlock(block: TimeRecordBlock, dateFormat: SimpleDateFormat, timeFormat: SimpleDateFormat): RecordInfo {
            return RecordInfo(
                date = dateFormat.format(block.date),
                entryTime = timeFormat.format(block.checkIn.date),
                exitTime = block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente",
                duration = "${block.duration / 60}h ${block.duration % 60}m",
                comment = block.checkIn.note ?: block.checkOut?.note ?: "",
                timestamp = block.checkIn.date.time
            )
        }
    }
}

object ServerConfig {
    private const val SERVER_IP = "80.32.125.224"
    private const val SERVER_PORT = 5000
    const val SERVER_URL_BASE = "https://$SERVER_IP:$SERVER_PORT"
    const val SERVER_URL_UPLOAD = "$SERVER_URL_BASE/upload"
    const val SERVER_URL_CHECK_FILE = "$SERVER_URL_BASE/files/check"
    const val SERVER_URL_DOWNLOAD = "$SERVER_URL_BASE/files/download"
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
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            currentUser.displayName?.let {
                return it
            }

            currentUser.email?.let { email ->
                return email.substringBefore('@')
                    .split('.')
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }
        }

        return "Usuario_Desconocido"
    }

    private fun generateFileName(): String {
        val userIdentifier = getUserDisplayName().replace(" ", "_").replace(".", "_")
        return "RegistroHorario_${userIdentifier}.pdf"
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    suspend fun createAndUploadPDF(blocks: List<TimeRecordBlock>) {
        withContext(Dispatchers.IO) {
            val fileName = generateFileName()
            var lastError: Exception? = null

            if (blocks.isEmpty()) {
                throw IllegalArgumentException("No hay bloques de tiempo para exportar")
            }

            // Intentar hasta 3 veces con backoff
            repeat(3) { attempt ->
                try {
                    Log.d("PDFManager", "Intento ${attempt + 1} de creación de PDF con ${blocks.size} bloques")

                    if (!isNetworkAvailable()) {
                        throw IOException("Sin conexión a internet")
                    }

                    val pdfData: ByteArray = if (checkFileExists(fileName)) {
                        Log.d("PDFManager", "Archivo existente encontrado, combinando registros")
                        val existingPdf = downloadExistingPdf(fileName)
                        createPDFWithCombinedRecords(existingPdf, blocks)
                    } else {
                        Log.d("PDFManager", "Creando nuevo PDF")
                        createPDFInMemory(blocks)
                    }

                    uploadToServerDirect(fileName, pdfData)
                    saveLocalCopy(fileName, pdfData)

                    Log.d("PDFManager", "PDF procesado exitosamente en intento ${attempt + 1}")
                    return@withContext

                } catch (e: Exception) {
                    lastError = e
                    Log.w("PDFManager", "Intento ${attempt + 1} falló: ${e.message}")

                    if (attempt < 2) {
                        delay(1000L * (attempt + 1))
                    }
                }
            }

            Log.e("PDFManager", "Todos los intentos fallaron, guardando para retry manual")
            savePendingWorkIndicator(fileName, blocks)
            throw lastError ?: IOException("Error desconocido al procesar PDF")
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
                    Log.w("PDFManager", "Error verificando archivo: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("PDFManager", "Error verificando existencia del archivo", e)
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
                response.body?.bytes() ?: throw IOException("Respuesta vacía del servidor")
            } else {
                throw IOException("Error descargando PDF existente: ${response.code}")
            }
        }
    }

    private fun extractRecordsFromExistingPDF(existingPdfData: ByteArray): List<RecordInfo> {
        val extractedRecords = mutableListOf<RecordInfo>()

        try {
            val pdfReader = PdfReader(existingPdfData.inputStream())
            val pdfDocument = PdfDocument(pdfReader)

            for (i in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(i)
                val text = PdfTextExtractor.getTextFromPage(page)

                // Dividir por líneas para procesamiento más preciso
                val lines = text.split('\n').filter { it.trim().isNotEmpty() }

                for (line in lines) {
                    // Regex más específico para una línea completa de registro
                    val recordPattern = "^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2})\\s+(\\d{2}:\\d{2}|Pendiente)\\s+(\\d+h\\s+\\d+m)\\s*(.*)$".toRegex()

                    val match = recordPattern.find(line.trim())
                    if (match != null) {
                        val (date, entryTime, exitTime, duration, rawComment) = match.destructured

                        // Limpiar COMPLETAMENTE las observaciones automáticas
                        val cleanComment = cleanObservationsField(rawComment.trim())

                        val timestamp = try {
                            val parsedDate = dateFormat.parse(date)
                            val parsedTime = timeFormat.parse(entryTime)
                            (parsedDate?.time ?: 0L) + (parsedTime?.time ?: 0L)
                        } catch (e: Exception) {
                            0L
                        }

                        extractedRecords.add(RecordInfo(date, entryTime, exitTime, duration, cleanComment, timestamp))
                    }
                }
            }

            pdfReader.close()
        } catch (e: Exception) {
            Log.e("PDFManager", "Error extrayendo registros del PDF", e)
        }

        return extractedRecords.sortedBy { it.timestamp }
    }

    private fun cleanObservationsField(comment: String): String {
        if (comment.isBlank()) return ""

        val trimmedComment = comment.trim()

        // Detectar patrones que NO son observaciones de usuario
        val forbiddenPatterns = listOf(
            // Fechas en cualquier formato
            "\\d{2}/\\d{2}/\\d{4}".toRegex(),
            "\\d{4}-\\d{2}-\\d{2}".toRegex(),

            // Horas
            "\\d{2}:\\d{2}".toRegex(),

            // Duraciones
            "\\d+h\\s+\\d+m".toRegex(),

            // Totales
            "Total.*".toRegex(RegexOption.IGNORE_CASE),

            // Palabra "Pendiente"
            "Pendiente".toRegex(RegexOption.IGNORE_CASE),

            // Solo números y espacios
            "^[\\d\\s:/-]+$".toRegex(),

            // Timestamps automáticos
            "\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}".toRegex()
        )

        // Si contiene cualquier patrón prohibido, devolver vacío
        for (pattern in forbiddenPatterns) {
            if (pattern.containsMatchIn(trimmedComment)) {
                return ""
            }
        }

        // Solo devolver si parece texto real de usuario (letras)
        return if (trimmedComment.any { it.isLetter() }) {
            trimmedComment
        } else {
            ""
        }
    }

    private fun createPDFWithCombinedRecords(existingPdfData: ByteArray, newBlocks: List<TimeRecordBlock>): ByteArray {
        val existingRecords = extractRecordsFromExistingPDF(existingPdfData)

        val newRecordsInfo = newBlocks.map { block ->
            RecordInfo.fromTimeRecordBlock(block, dateFormat, timeFormat)
        }

        val uniqueNewRecords = newRecordsInfo.filter { newRecord ->
            !existingRecords.any { existing ->
                existing.date == newRecord.date && existing.entryTime == newRecord.entryTime
            }
        }

        if (uniqueNewRecords.isEmpty()) {
            Log.d("PDFManager", "No hay registros nuevos únicos, devolviendo PDF existente")
            return existingPdfData
        }

        val allRecords = (existingRecords + uniqueNewRecords).sortedBy { it.timestamp }

        Log.d("PDFManager", "Combinando ${existingRecords.size} registros existentes con ${uniqueNewRecords.size} nuevos")

        return createPDFWithAllRecords(allRecords)
    }

    private fun createPDFWithAllRecords(records: List<RecordInfo>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val userName = getUserDisplayName()

        PdfWriter(outputStream).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                document.add(
                    Paragraph(userName)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                        .setBold()
                )

                document.add(
                    Paragraph("Actualizado: ${dateFormat.format(Date())}")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFontSize(10f)
                )

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
                    table.addCell(record.comment)
                }

                document.add(table)

                var totalHours = 0
                var totalMinutes = 0

                records.forEach { record ->
                    if (record.exitTime != "Pendiente") {
                        val durationPattern = "(\\d+)h\\s+(\\d+)m".toRegex()
                        val match = durationPattern.find(record.duration)
                        if (match != null) {
                            val (hours, minutes) = match.destructured
                            totalHours += hours.toInt()
                            totalMinutes += minutes.toInt()
                        }
                    }
                }

                totalHours += totalMinutes / 60
                totalMinutes %= 60

                document.add(
                    Paragraph("Total: ${totalHours}h ${totalMinutes}m")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setBold()
                        .setFontSize(14f)
                        .setMarginTop(10f)
                )
            }
        }
        return outputStream.toByteArray()
    }

    private fun createPDFInMemory(blocks: List<TimeRecordBlock>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val userName = getUserDisplayName()

        val sortedBlocks = blocks.sortedBy { it.checkIn.date.time }

        PdfWriter(outputStream).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                document.add(
                    Paragraph(userName)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                        .setBold()
                )

                document.add(
                    Paragraph("Documento generado: ${dateFormat.format(Date())}")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFontSize(10f)
                )

                val table = createTable(sortedBlocks)
                document.add(table)

                val totalMinutes = sortedBlocks.sumOf { it.duration }
                val totalHours = totalMinutes / 60
                val remainingMinutes = totalMinutes % 60
                document.add(
                    Paragraph("Total: ${totalHours}h ${remainingMinutes}m")
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setBold()
                        .setFontSize(14f)
                        .setMarginTop(10f)
                )
            }
        }
        return outputStream.toByteArray()
    }

    private fun createTable(blocks: List<TimeRecordBlock>): Table {
        val table = Table(floatArrayOf(130f, 80f, 80f, 80f, 180f))
            .setTextAlignment(TextAlignment.CENTER)

        table.addHeaderCell("Fecha")
        table.addHeaderCell("Entrada")
        table.addHeaderCell("Salida")
        table.addHeaderCell("Duración")
        table.addHeaderCell("Observaciones")

        blocks.forEach { block ->
            table.addCell(dateFormat.format(block.date))
            table.addCell(timeFormat.format(block.checkIn.date))
            table.addCell(block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente")

            val hours = block.duration / 60
            val minutes = block.duration % 60
            table.addCell("${hours}h ${minutes}m")

            val rawComment = block.checkOut?.note ?: block.checkIn.note ?: ""
            val cleanComment = cleanObservationsField(rawComment)
            table.addCell(cleanComment)
        }
        return table
    }

    fun uploadToServerDirect(fileName: String, pdfData: ByteArray) {
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
                throw IOException("Upload falló con código: ${response.code}")
            }
            Log.d("PDFManager", "Upload exitoso: $fileName")
        }
    }

    private fun savePendingWorkIndicator(fileName: String, blocks: List<TimeRecordBlock>) {
        try {
            val pendingDir = File(context.getExternalFilesDir("pdfs"), "pending")
            if (!pendingDir.exists()) pendingDir.mkdirs()

            val indicatorFile = File(pendingDir, "$fileName.retry")
            indicatorFile.writeText("${blocks.size} registros pendientes - ${Date()}")

            val localPdf = createPDFInMemory(blocks)
            saveLocally(localPdf)

            Log.d("PDFManager", "Indicador de retry guardado y PDF local creado")
        } catch (e: Exception) {
            Log.e("PDFManager", "Error guardando indicador de retry", e)
        }
    }

    private fun saveLocally(pdfData: ByteArray) {
        val fileName = generateFileName()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        file.writeBytes(pdfData)
        Log.d("PDFManager", "PDF guardado en Downloads: $fileName")
    }

    private fun saveLocalCopy(fileName: String, pdfData: ByteArray) {
        val appDir = context.getExternalFilesDir("pdfs") ?: context.filesDir
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, fileName)
        file.writeBytes(pdfData)
        Log.d("PDFManager", "Copia local guardada: $fileName")
    }
}