package com.gymmanager.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gymmanager.app.data.Branch
import com.gymmanager.app.data.Member
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MemberExcelExporter {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun createAndShare(context: Context, members: List<Member>, branches: List<Branch>) {
        val branchNames = branches.associateBy({ it.id }, { it.name })
        val fileName = "GymMembers_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.xlsx"
        val file = File(context.cacheDir, fileName)
        writeXlsx(file, members, branchNames)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Members Excel"))
    }

    private fun writeXlsx(file: File, members: List<Member>, branchNames: Map<Long, String>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            add(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent())
            add(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent())
            add(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Members" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent())
            add(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent())

            val headers = listOf("Name", "Phone Number", "Joined Date", "Gender", "Branch")
            val rows = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                append(rowXml(1, headers))
                members.sortedBy { it.name.lowercase() }.forEachIndexed { index, member ->
                    val gender = if (member.gender.name == "LADY") "Ladies" else "Gents"
                    val branch = branchNames[member.branchId] ?: ""
                    append(rowXml(index + 2, listOf(member.name, member.phone, dateFormat.format(Date(member.joinDateMillis)), gender, branch)))
                }
                append("</sheetData></worksheet>")
            }
            add(zip, "xl/worksheets/sheet1.xml", rows)
        }
    }

    private fun rowXml(rowNumber: Int, values: List<String>): String = buildString {
        append("<row r=\"$rowNumber\">")
        values.forEachIndexed { index, value ->
            val col = ('A'.code + index).toChar()
            append("<c r=\"$col$rowNumber\" t=\"inlineStr\"><is><t>${escape(value)}</t></is></c>")
        }
        append("</row>")
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun add(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
