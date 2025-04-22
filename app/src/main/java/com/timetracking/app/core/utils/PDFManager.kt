package com.timetracking.app.core.utils

import android.content.Context
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

data class RecordInfo(
    val date: String,
    val entryTime: String,
    val exitTime: String,
    val duration: String,
    val comment: String = ""
)

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
        // Obtener el usuario actual de Firebase
        val currentUser = FirebaseAuth.getInstance().currentUser

        // Priorizar nombre de display de Firebase
        if (currentUser != null) {
            // Primero intentar con el nombre de display
            currentUser.displayName?.let {
                return it
            }

            // Si no hay nombre de display, usar el email
            currentUser.email?.let { email ->
                // Formatear el email como nombre
                return email.substringBefore('@')
                    .split('.')
                    .joinToString(" ") { it.capitalize() }
            }
        }

        // Fallback: Nombre de usuario desconocido
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

    private fun extractRecordsFromExistingPDF(existingPdfData: ByteArray): List<RecordInfo> {
        val extractedRecords = mutableListOf<RecordInfo>()

        try {
            val pdfReader = PdfReader(existingPdfData.inputStream())
            val pdfDocument = PdfDocument(pdfReader)

            for (i in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(i)
                val text = PdfTextExtractor.getTextFromPage(page)

                val recordPattern = "(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2})\\s+(\\d{2}:\\d{2}|Pendiente)\\s+(\\d+h\\s+\\d+m)\\s*([\\s\\S]*?)(?=\\d{2}/\\d{2}/\\d{4}|$)".toRegex()

                val matches = recordPattern.findAll(text)
                matches.forEach { match ->
                    val (date, entryTime, exitTime, duration, comment) = match.destructured
                    extractedRecords.add(RecordInfo(date, entryTime, exitTime, duration, comment.trim()))
                }
            }

            pdfReader.close()
        } catch (e: Exception) {
            Log.e("PDFManager", "Error extracting records from PDF: ${e.message}")
        }

        return extractedRecords
    }

    private fun createPDFWithCombinedRecords(existingPdfData: ByteArray, newBlocks: List<TimeRecordBlock>): ByteArray {
        val existingRecords = extractRecordsFromExistingPDF(existingPdfData)

        val newRecordsInfo = newBlocks.map { block ->
            RecordInfo(
                dateFormat.format(block.date),
                timeFormat.format(block.checkIn.date),
                block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente",
                "${block.duration / 60}h ${block.duration % 60}m",
                block.checkIn.note ?: block.checkOut?.note ?: ""
            )
        }

        val uniqueNewRecords = newRecordsInfo.filter { newRecord ->
            !existingRecords.any { it.date == newRecord.date &&
                    it.entryTime == newRecord.entryTime &&
                    it.exitTime == newRecord.exitTime }
        }

        if (uniqueNewRecords.isEmpty()) {
            return existingPdfData
        }

        val allRecords = existingRecords + uniqueNewRecords
        return createPDFWithAllRecords(allRecords)
    }

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
                    val durationPattern = "(\\d+)h\\s+(\\d+)m".toRegex()
                    val match = durationPattern.find(record.duration)
                    if (match != null) {
                        val (hours, minutes) = match.destructured
                        totalHours += hours.toInt()
                        totalMinutes += minutes.toInt()
                    }
                }

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
        val appDir = context.getExternalFilesDir("pdfs") ?: context.filesDir
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, fileName)
        file.writeBytes(pdfData)
    }
}