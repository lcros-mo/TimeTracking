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
import com.timetracking.app.data.model.TimeRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PDFManager(private val context: Context) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun createOrUpdatePDF(records: List<TimeRecord>) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val userName = account?.let {
            "${it.familyName} ${it.givenName}"
        } ?: "Usuario"

        val fileName = "RegistroHorario_${userName.replace(" ", "_")}.pdf"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs() // Nos aseguramos que el directorio existe
        val file = File(downloadsDir, fileName)

        // Si el archivo existe, crearemos uno nuevo con los registros existentes + nuevos
        // Si no existe, crearemos uno nuevo
        PdfWriter(file).use { writer ->
            val pdf = PdfDocument(writer)
            Document(pdf).use { document ->
                // Añadir título
                val title = Paragraph("Registro Horario - $userName")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(16f)
                document.add(title)

                // Añadir fecha de generación
                val dateGenerated = Paragraph("Documento generado: ${dateFormat.format(Date())}")
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(10f)
                document.add(dateGenerated)

                // Crear tabla de registros
                val table = createTable(records)
                document.add(table)

                // Añadir total de horas
                val totalHours = calculateTotalHours(records)
                val totalText = Paragraph("Total horas: $totalHours")
                    .setTextAlignment(TextAlignment.RIGHT)
                document.add(totalText)
            }
        }
    }

    private fun createTable(records: List<TimeRecord>): Table {
        val table = Table(floatArrayOf(150f, 100f, 100f, 150f))
            .setTextAlignment(TextAlignment.CENTER)

        // Añadir cabeceras
        table.addHeaderCell("Fecha")
        table.addHeaderCell("Hora")
        table.addHeaderCell("Tipo")
        table.addHeaderCell("Notas")

        // Agrupar registros por fecha
        val recordsByDate = records.groupBy {
            dateFormat.format(it.date)
        }

        // Añadir registros a la tabla
        recordsByDate.forEach { (date, dayRecords) ->
            dayRecords.forEach { record ->
                table.addCell(date)
                table.addCell(timeFormat.format(record.date))
                table.addCell(if (record.type.toString() == "CHECK_IN") "Entrada" else "Salida")
                table.addCell(record.note ?: "")
            }
        }

        return table
    }

    private fun calculateTotalHours(records: List<TimeRecord>): String {
        var totalMinutes = 0L
        val sortedRecords = records.sortedBy { it.date }

        var i = 0
        while (i < sortedRecords.size - 1) {
            val current = sortedRecords[i]
            val next = sortedRecords[i + 1]

            if (current.type.toString() == "CHECK_IN" && next.type.toString() == "CHECK_OUT") {
                val diffInMillis = next.date.time - current.date.time
                totalMinutes += diffInMillis / (1000 * 60)
                i += 2
            } else {
                i++
            }
        }

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format("%d horas %d minutos", hours, minutes)
    }
}