// app/src/main/java/com/timetracking/app/utils/PDFManager.kt
package com.timetracking.app.utils

import android.content.Context
import android.os.Environment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.timetracking.app.ui.history.model.TimeRecordBlock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PDFManager(private val context: Context) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun createOrUpdatePDF(blocks: List<TimeRecordBlock>) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val userName = account?.let {
            "${it.familyName} ${it.givenName}"
        } ?: "Usuario"

        val fileName = "RegistroHorario_${userName.replace(" ", "_")}.pdf"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val filePath = downloadsDir.absolutePath + File.separator + fileName
        val file = File(filePath)

        PdfWriter(file).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                addTitle(document, userName)
                addGenerationDate(document)
                addRecordsTable(document, blocks)
                addTotalHours(document, blocks)
            }
        }
    }

    private fun addTitle(document: Document, userName: String) {
        document.add(
            Paragraph("Registro Horario - $userName")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16f)
        )
    }

    private fun addGenerationDate(document: Document) {
        document.add(
            Paragraph("Documento generado: ${dateFormat.format(Date())}")
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(10f)
        )
    }

    private fun addRecordsTable(document: Document, blocks: List<TimeRecordBlock>) {
        val table = Table(floatArrayOf(150f, 100f, 100f, 100f))
            .setTextAlignment(TextAlignment.CENTER)

        // Cabeceras
        table.addHeaderCell("Fecha")
        table.addHeaderCell("Entrada")
        table.addHeaderCell("Salida")
        table.addHeaderCell("Duración")

        // Añadir bloques
        blocks.forEach { block ->
            table.addCell(dateFormat.format(block.date))
            table.addCell(timeFormat.format(block.checkIn.date))
            table.addCell(block.checkOut?.let { timeFormat.format(it.date) } ?: "Pendiente")
            val hours = block.duration / 60
            val minutes = block.duration % 60
            table.addCell("${hours}h ${minutes}m")
        }

        document.add(table)
    }

    private fun addTotalHours(document: Document, blocks: List<TimeRecordBlock>) {
        val totalMinutes = blocks.sumOf { it.duration }
        val totalHours = totalMinutes / 60
        val remainingMinutes = totalMinutes % 60

        document.add(
            Paragraph("Total: ${totalHours}h ${remainingMinutes}m")
                .setTextAlignment(TextAlignment.RIGHT)
        )
    }
}