package io.github.chiragbhatn.expensetracker.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvAndXlsxTest {

    @Test
    fun `csv round-trips quotes, commas, line breaks and unicode`() {
        val rows = listOf(
            listOf("id", "note", "amount"),
            listOf("1", "Team dinner, Friday", "₹1,000.50"),
            listOf("2", "Says \"hi\"\nthen leaves", ""),
            listOf("3", " padded ", "0"),
        )

        val text = Csv.write(rows)

        assertTrue(text.contains("\"Team dinner, Friday\""))
        assertTrue(text.contains("\"Says \"\"hi\"\"\nthen leaves\""))
        assertTrue(text.endsWith("\r\n"))
        assertEquals(rows, Csv.parse(text))
        assertEquals(rows, Csv.parse(Csv.BOM + text.replace("\r\n", "\n")))
    }

    @Test
    fun `csv parsing tolerates a missing final newline and skips blank lines`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "")), Csv.parse("a,b\n\n1,\"\""))
        assertEquals(listOf(listOf("a")), Csv.parse("a\r\n\r\n"))
    }

    @Test(expected = CsvException::class)
    fun `an unclosed quote is an error`() {
        Csv.parse("a,\"b\n1,2")
    }

    @Test
    fun `text that looks like a formula is neutralised and restored`() {
        assertEquals("'=SUM(A1)", Csv.safeText("=SUM(A1)"))
        assertEquals("'+91 98765", Csv.safeText("+91 98765"))
        assertEquals("Swiggy", Csv.safeText("Swiggy"))
        assertEquals("=SUM(A1)", Csv.readText(Csv.safeText("=SUM(A1)")))
        assertEquals("'quoted", Csv.readText("'quoted"))
    }

    @Test
    fun `xlsx round-trips text, numbers and empty cells across sheets`() {
        val sheets = listOf(
            XlsxSheet(
                "People",
                listOf("id", "name", "note"),
                listOf(
                    listOf(XlsxCell.Text("p-1"), XlsxCell.Text("Rahul & Co <ltd>"), XlsxCell.Text("  spaced\nlines  ")),
                    listOf(XlsxCell.Text("p-2"), null, XlsxCell.Text("₹ 😀 \u0001control")),
                ),
            ),
            XlsxSheet("Numbers", listOf("paise", "rupees"), listOf(listOf(XlsxCell.Number("38326"), XlsxCell.Number("383.26", twoDecimals = true)))),
            XlsxSheet("Empty", listOf("only", "header"), emptyList()),
        )

        val workbook = XlsxReader.read(XlsxWriter.toBytes(sheets))

        assertEquals(listOf("People", "Numbers", "Empty"), workbook.sheets.keys.toList())
        assertEquals(
            listOf(listOf("id", "name", "note"), listOf("p-1", "Rahul & Co <ltd>", "  spaced\nlines  "), listOf("p-2", "", "₹ 😀 control")),
            workbook.sheets["People"],
        )
        assertEquals(listOf(listOf("paise", "rupees"), listOf("38326", "383.26")), workbook.sheets["Numbers"])
        assertEquals(listOf(listOf("only", "header")), workbook.sheets["Empty"])
    }

    @Test
    fun `reads workbooks saved by spreadsheet apps, with shared strings and rich text`() {
        val bytes = zip(
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:x="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="7" x:id="R1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="R1" Type="worksheet" Target="/xl/worksheets/data.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>id</t></si><si><r><t>Ra</t></r><r><rPr><b/></rPr><t>hul</t></r><rPh><t>ignored</t></rPh></si></sst>""",
            "xl/worksheets/data.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
                """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="b"><v>1</v></c></row>""" +
                """<row r="3"><c r="B3" t="s"><v>1</v></c><c r="C3"><v>4.6303E4</v></c><c r="D3" t="str"><f>A1</f><v>id</v></c></row>""" +
                """</sheetData></worksheet>""",
        )

        val rows = XlsxReader.read(bytes).sheets.getValue("Data")

        assertEquals(listOf("id", "", "TRUE"), rows[0])
        assertEquals(emptyList<String>(), rows[1])
        assertEquals(listOf("", "Rahul", "4.6303E4", "id"), rows[2])
    }

    @Test
    fun `columns are named like spreadsheets`() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA", "ZZ", "AAA"), listOf(0, 25, 26, 51, 52, 701, 702).map(XlsxWriter::columnName))
        listOf(0, 25, 26, 51, 52, 701, 702).forEach { assertEquals(it, XlsxReader.columnIndex(XlsxWriter.columnName(it) + "17")) }
    }

    @Test
    fun `recognises xlsx files and rejects others`() {
        assertTrue(XlsxReader.looksLikeXlsx(XlsxWriter.toBytes(listOf(XlsxSheet("A", listOf("x"), emptyList())))))
        assertFalse(XlsxReader.looksLikeXlsx("id,name\n".toByteArray()))
        assertArrayEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4), XlsxWriter.toBytes(listOf(XlsxSheet("A", listOf("x"), emptyList()))).copyOf(4))
    }

    @Test(expected = XlsxException::class)
    fun `a damaged workbook fails with a readable error`() {
        XlsxReader.read(zip("xl/workbook.xml" to "<workbook><sheets><sheet name=\"A\""))
    }

    @Test(expected = XlsxException::class)
    fun `a zip without a workbook is not a spreadsheet`() {
        XlsxReader.read(zip("word/document.xml" to "<document/>"))
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
