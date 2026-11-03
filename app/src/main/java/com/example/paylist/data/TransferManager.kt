package com.example.paylist.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object TransferManager {
    private val header = listOf(
        "type", "id", "templateId", "occurrenceId", "title", "category", "amountMinor", "currency",
        "recurrence", "startDate", "dueDay", "endDate", "amountLimitMinor", "active", "logoUri", "notes",
        "periodKey", "plannedDate", "dueDate", "paidMinor", "status", "operationDate", "comment"
    )

    suspend fun export(context: Context, uri: Uri, format: String, database: PayListDatabase) = withContext(Dispatchers.IO) {
        val csv = database.withTransaction { createCsv(database.paymentDao()) }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            when (format) {
                "xlsx" -> writeXlsx(output, parseCsvRecords(csv))
                "zip" -> ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("paylist.csv")); zip.write(csv.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                else -> output.write(csv.toByteArray(Charsets.UTF_8))
            }
        } ?: error("Не удалось открыть файл для записи")
    }

    suspend fun import(context: Context, uri: Uri, database: PayListDatabase) = withContext(Dispatchers.IO) {
        val name = (uri.lastPathSegment ?: "").lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        val rows = input.use {
            when {
                name.endsWith(".zip") || mime.contains("zip") -> ZipInputStream(it).use { zip ->
                    var entry = zip.nextEntry
                    var content: String? = null
                    while (entry != null) {
                        if (entry.name.endsWith(".csv", true)) content = zip.readBytes().toString(Charsets.UTF_8)
                        zip.closeEntry(); entry = zip.nextEntry
                    }
                    parseCsv(content ?: error("В архиве не найден файл paylist.csv"))
                }
                name.endsWith(".xlsx") || mime.contains("spreadsheet") -> parseXlsx(it)
                else -> parseCsv(it.bufferedReader().use { reader -> reader.readText() })
            }
        }
        database.withTransaction {
            val dao = database.paymentDao()
            if (rows.templates.isNotEmpty()) dao.importTemplates(rows.templates)
            if (rows.occurrences.isNotEmpty()) dao.importOccurrences(rows.occurrences)
            if (rows.operations.isNotEmpty()) dao.importOperations(rows.operations)
        }
    }

    private suspend fun createCsv(dao: PaymentDao): String {
        val lines = mutableListOf(header.joinToString(",") { csv(it) })
        dao.getAllTemplates().forEach { t ->
            lines += listOf("T", t.id, "", "", t.title, t.category, t.amountMinor, t.currency, t.recurrence,
                t.startDate, t.dueDay, t.endDate.orEmpty(), t.amountLimitMinor?.toString().orEmpty(), t.active,
                t.logoUri.orEmpty(), t.notes, "", "", "", "", "", "", "").joinToString(",") { csv(it.toString()) }
        }
        dao.getAllOccurrences().forEach { o ->
            lines += listOf("O", o.id, o.templateId, "", "", "", o.amountMinor, "", "", "", "", "", "", "",
                "", "", o.periodKey, o.plannedDate, o.dueDate, o.paidMinor, o.status, "", "").joinToString(",") { csv(it.toString()) }
        }
        dao.getAllOperations().forEach { o ->
            lines += listOf("P", o.id, "", o.occurrenceId, "", "", o.amountMinor, "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", o.operationDate, o.comment).joinToString(",") { csv(it.toString()) }
        }
        return lines.joinToString("\r\n")
    }

    private data class Rows(
        val templates: List<PaymentTemplate>, val occurrences: List<PaymentOccurrence>, val operations: List<PaymentOperation>
    )

    private fun parseCsv(text: String): Rows {
        val records = parseCsvRecords(text)
        val templates = mutableListOf<PaymentTemplate>()
        val occurrences = mutableListOf<PaymentOccurrence>()
        val operations = mutableListOf<PaymentOperation>()
        records.drop(1).filter { it.isNotEmpty() }.forEach { r ->
            fun v(i: Int) = r.getOrNull(i).orEmpty()
            when (v(0)) {
                "T" -> templates += PaymentTemplate(
                    id = v(1).toLong(), title = v(4), category = v(5), amountMinor = v(6).toLong(), currency = v(7),
                    recurrence = v(8), startDate = v(9), dueDay = v(10).toInt(), endDate = v(11).takeIf { it.isNotBlank() },
                    amountLimitMinor = v(12).takeIf { it.isNotBlank() }?.toLong(), active = v(13).toBoolean(),
                    logoUri = v(14).takeIf { it.isNotBlank() }, notes = v(15)
                )
                "O" -> occurrences += PaymentOccurrence(
                    id = v(1).toLong(), templateId = v(2).toLong(), periodKey = v(16), plannedDate = v(17),
                    dueDate = v(18), amountMinor = v(6).toLong(), paidMinor = v(19).toLong(), status = v(20)
                )
                "P" -> operations += PaymentOperation(
                    id = v(1).toLong(), occurrenceId = v(3).toLong(), operationDate = v(21),
                    amountMinor = v(6).toLong(), comment = v(22)
                )
            }
        }
        return Rows(templates, occurrences, operations)
    }

    private fun parseCsvRecords(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    private fun writeXlsx(output: java.io.OutputStream, rows: List<List<String>>) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="PayList" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val sheet = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                rows.forEachIndexed { ri, row ->
                    append("<row r=\"${ri + 1}\">")
                    row.forEachIndexed { ci, value ->
                        append("<c r=\"${columnName(ci)}${ri + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            entry("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun parseXlsx(input: java.io.InputStream): Rows {
        val zip = ZipInputStream(input)
        var xml: String? = null
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") xml = zip.readBytes().toString(Charsets.UTF_8)
            zip.closeEntry(); entry = zip.nextEntry
        }
        val source = xml ?: error("В XLSX не найден лист с данными")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(source.reader())
        val rows = mutableListOf<List<String>>()
        var current = mutableListOf<String>(); var col = 0; var inRow = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { current = mutableListOf(); inRow = true }
                    "c" -> col = columnIndex(parser.getAttributeValue(null, "r") ?: "A1")
                    "t", "v" -> if (inRow) {
                        while (current.size <= col) current.add("")
                        current[col] = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "row" && inRow) { rows += current; inRow = false }
            }
            event = parser.next()
        }
        return parseCsvRows(rows)
    }

    private fun parseCsvRows(records: List<List<String>>): Rows {
        // Reuse the canonical CSV decoder to keep CSV and XLSX import semantics identical.
        return parseCsv(records.joinToString("\n") { row -> row.joinToString(",") { csv(it) } })
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun columnName(index: Int): String { var n = index + 1; val out = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; out.insert(0, ('A'.code + r).toChar()); n = (n - 1) / 26 }; return out.toString() }
    private fun columnIndex(ref: String): Int { var result = 0; ref.takeWhile { it.isLetter() }.uppercase().forEach { result = result * 26 + (it - 'A' + 1) }; return result - 1 }
}
