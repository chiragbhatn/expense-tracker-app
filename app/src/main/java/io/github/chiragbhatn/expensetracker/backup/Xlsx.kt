package io.github.chiragbhatn.expensetracker.backup

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

class XlsxException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface XlsxCell {
    data class Text(val value: String) : XlsxCell

    /** A number written exactly as given, e.g. "38326" or "383.26". */
    data class Number(val value: String, val twoDecimals: Boolean = false) : XlsxCell
}

class XlsxSheet(
    val name: String,
    val header: List<String>,
    val rows: List<List<XlsxCell?>>,
    /** Column widths in characters; columns without one use a default. */
    val widths: List<Int> = emptyList(),
)

/**
 * Writes Office Open XML workbooks that Excel, Google Sheets and LibreOffice
 * open. Text cells use the text number format so editing a date or an ID in a
 * spreadsheet app does not turn it into a number.
 */
object XlsxWriter {
    private const val STYLE_HEADER = 1
    private const val STYLE_TWO_DECIMALS = 2
    private const val STYLE_TEXT = 3

    fun write(sheets: List<XlsxSheet>, out: OutputStream, title: String = "Backup", created: String? = null) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("docProps/app.xml", APP_PROPS)
            entry("docProps/core.xml", coreProps(title, created))
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
                val writer = zip.bufferedWriter(Charsets.UTF_8)
                writeSheet(sheet, writer)
                writer.flush()
                zip.closeEntry()
            }
        }
    }

    fun toBytes(sheets: List<XlsxSheet>, title: String = "Backup", created: String? = null): ByteArray =
        ByteArrayOutputStream().also { write(sheets, it, title, created) }.toByteArray()

    private fun writeSheet(sheet: XlsxSheet, out: Appendable) {
        out.append(XML_HEADER)
        out.append("""<worksheet xmlns="$MAIN_NS">""")
        out.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        out.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (sheet.header.isNotEmpty()) {
            out.append("<cols>")
            sheet.header.indices.forEach { column ->
                val width = sheet.widths.getOrNull(column) ?: maxOf(10, sheet.header[column].length + 2)
                out.append("""<col min="${column + 1}" max="${column + 1}" width="$width" customWidth="1"/>""")
            }
            out.append("</cols>")
        }
        out.append("<sheetData>")
        writeRow(out, 1, sheet.header.map { XlsxCell.Text(it) }, header = true)
        sheet.rows.forEachIndexed { index, row -> writeRow(out, index + 2, row, header = false) }
        out.append("</sheetData></worksheet>")
    }

    private fun writeRow(out: Appendable, rowNumber: Int, cells: List<XlsxCell?>, header: Boolean) {
        out.append("""<row r="$rowNumber">""")
        cells.forEachIndexed { column, cell ->
            val ref = columnName(column) + rowNumber
            when (cell) {
                null -> Unit
                is XlsxCell.Text -> {
                    if (cell.value.isEmpty() && !header) return@forEachIndexed
                    val style = if (header) STYLE_HEADER else STYLE_TEXT
                    val text = escape(cell.value)
                    val preserve = if (text != text.trim() || text.contains('\n')) """ xml:space="preserve"""" else ""
                    out.append("""<c r="$ref" s="$style" t="inlineStr"><is><t$preserve>$text</t></is></c>""")
                }
                is XlsxCell.Number -> {
                    val style = if (cell.twoDecimals) """ s="$STYLE_TWO_DECIMALS"""" else ""
                    out.append("""<c r="$ref"$style><v>${cell.value}</v></c>""")
                }
            }
        }
        out.append("</row>")
    }

    /** 0 → "A", 25 → "Z", 26 → "AA". */
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            name.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { c ->
            when {
                c == '&' -> out.append("&amp;")
                c == '<' -> out.append("&lt;")
                c == '>' -> out.append("&gt;")
                c == '"' -> out.append("&quot;")
                // Characters XML 1.0 does not allow are dropped.
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                c == '￾' || c == '￿' -> Unit
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        (1..sheetCount).forEach {
            append("""<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        append("</Types>")
    }

    private fun workbook(sheets: List<XlsxSheet>) = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        (1..sheetCount).forEach {
            append("""<Relationship Id="rId$it" Type="$REL_NS/worksheet" Target="worksheets/sheet$it.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="$REL_NS/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun coreProps(title: String, created: String?) = buildString {
        append(XML_HEADER)
        append(
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" """ +
                """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""",
        )
        append("<dc:title>${escape(title)}</dc:title><dc:creator>Expense Tracker</dc:creator>")
        if (created != null) append("""<dcterms:created xsi:type="dcterms:W3CDTF">${escape(created)}</dcterms:created>""")
        append("</cp:coreProperties>")
    }

    internal const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    internal const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    private const val ROOT_RELS = XML_HEADER +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>""" +
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
        """<Relationship Id="rId3" Type="$REL_NS/extended-properties" Target="docProps/app.xml"/>""" +
        """</Relationships>"""

    private const val APP_PROPS = XML_HEADER +
        """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Expense Tracker</Application></Properties>"""

    private const val STYLES = XML_HEADER +
        """<styleSheet xmlns="$MAIN_NS">""" +
        """<numFmts count="1"><numFmt numFmtId="164" formatCode="0.00"/></numFmts>""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="4">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="49" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""
}

/** A workbook's sheets as rows of cell text, keyed by sheet name in workbook order. */
class XlsxWorkbook(val sheets: LinkedHashMap<String, List<List<String>>>)

/**
 * Reads the cell values of an .xlsx file: shared and inline strings, numbers
 * and booleans (as "TRUE"/"FALSE"). Formatting is ignored. Sizes are limited
 * so a damaged or hostile file cannot exhaust memory.
 */
object XlsxReader {
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024
    private const val MAX_ROWS = 500_000

    fun read(input: InputStream): XlsxWorkbook {
        val parts = unzip(input)
        val workbookXml = parts["xl/workbook.xml"] ?: throw XlsxException("The file is not an Excel workbook (xl/workbook.xml is missing).")
        val relationships = parts["xl/_rels/workbook.xml.rels"]?.let(::parseRelationships).orEmpty()
        val sharedStrings = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheets = LinkedHashMap<String, List<List<String>>>()
        parseWorkbook(workbookXml).forEach { (name, relId) ->
            val target = relationships[relId] ?: "worksheets/sheet${sheets.size + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val xml = parts[normalize(path)] ?: throw XlsxException("Sheet \"$name\" is missing from the workbook.")
            sheets[name] = parseSheet(xml, sharedStrings)
        }
        return XlsxWorkbook(sheets)
    }

    fun read(bytes: ByteArray): XlsxWorkbook = read(ByteArrayInputStream(bytes))

    /** True when the bytes start like a zip archive, which every .xlsx file is. */
    fun looksLikeXlsx(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() && header[3] == 4.toByte()

    private fun normalize(path: String): String {
        val segments = ArrayDeque<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (parts.size >= MAX_ENTRIES) throw XlsxException("The workbook has too many parts.")
                    if (entry.isDirectory) continue
                    val name = normalize(entry.name.replace('\\', '/'))
                    // Only the parts this reader uses are kept in memory.
                    val wanted = name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" ||
                        name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/")
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = zip.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_TOTAL_BYTES) throw XlsxException("The workbook is too large to import.")
                        if (wanted) buffer.write(chunk, 0, read)
                    }
                    if (wanted) parts[name] = buffer.toByteArray()
                }
            }
        } catch (e: ZipException) {
            throw XlsxException("The file is damaged or is not an .xlsx workbook.", e)
        }
        if (parts.isEmpty()) throw XlsxException("The file is empty or is not an .xlsx workbook.")
        return parts
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        // No DTDs or external entities: a workbook never needs them.
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, value) ->
            try {
                factory.setFeature(feature, value)
            } catch (_: Exception) {
                // Not every parser supports every feature.
            }
        }
        try {
            factory.newSAXParser().parse(InputSource(ByteArrayInputStream(xml)), handler)
        } catch (e: XlsxException) {
            throw e
        } catch (e: Exception) {
            throw XlsxException("Part of the workbook could not be read (${e.message}).", e)
        }
    }

    private fun parseWorkbook(xml: ByteArray): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        parse(
            xml,
            object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    if (localName == "sheet") {
                        val name = attributes.getValue("", "name") ?: attributes.getValue("name") ?: return
                        val relId = attributes.getValue(XlsxWriter.REL_NS, "id") ?: attributes.getValue("r:id") ?: ""
                        sheets += name to relId
                    }
                }
            },
        )
        return sheets
    }

    private fun parseRelationships(xml: ByteArray): Map<String, String> {
        val rels = HashMap<String, String>()
        parse(
            xml,
            object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    if (localName == "Relationship") {
                        val id = attributes.getValue("Id") ?: return
                        val target = attributes.getValue("Target") ?: return
                        rels[id] = target
                    }
                }
            },
        )
        return rels
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        parse(
            xml,
            object : DefaultHandler() {
                private val current = StringBuilder()
                private var inText = false
                private var inPhonetic = false

                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    when (localName) {
                        "si" -> current.clear()
                        "rPh" -> inPhonetic = true
                        "t" -> inText = !inPhonetic
                    }
                }

                override fun endElement(uri: String, localName: String, qName: String) {
                    when (localName) {
                        "si" -> strings += current.toString()
                        "rPh" -> inPhonetic = false
                        "t" -> inText = false
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText) current.appendRange(ch, start, start + length)
                }
            },
        )
        return strings
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        parse(
            xml,
            object : DefaultHandler() {
                private var row: MutableList<String>? = null
                private var rowNumber = 0
                private var column = 0
                private var type: String? = null
                private val value = StringBuilder()
                private var collecting = false
                private var inPhonetic = false

                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    when (localName) {
                        "row" -> {
                            val number = attributes.getValue("r")?.toIntOrNull() ?: (rowNumber + 1)
                            // Keep row positions: missing rows become empty rows.
                            while (rows.size < number - 1) rows += emptyList<String>()
                            if (rows.size > MAX_ROWS) throw XlsxException("A sheet has too many rows to import.")
                            rowNumber = number
                            row = mutableListOf()
                            column = 0
                        }
                        "c" -> {
                            column = attributes.getValue("r")?.let(::columnIndex) ?: column
                            type = attributes.getValue("t")
                            value.clear()
                        }
                        "v" -> collecting = true
                        "rPh" -> inPhonetic = true
                        "t" -> collecting = !inPhonetic
                    }
                }

                override fun endElement(uri: String, localName: String, qName: String) {
                    when (localName) {
                        "v", "t" -> collecting = false
                        "rPh" -> inPhonetic = false
                        "c" -> {
                            val raw = value.toString()
                            val text = when (type) {
                                "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                "e" -> ""
                                else -> raw
                            }
                            row?.let { cells ->
                                while (cells.size < column) cells += ""
                                if (cells.size == column) cells += text else cells[column] = text
                            }
                            column++
                        }
                        "row" -> {
                            row?.let { rows += it }
                            row = null
                        }
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (collecting) value.appendRange(ch, start, start + length)
                }
            },
        )
        return rows
    }

    /** "A1" → 0, "AB12" → 27. */
    fun columnIndex(reference: String): Int {
        var index = 0
        for (c in reference) {
            if (c !in 'A'..'Z' && c !in 'a'..'z') break
            index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
