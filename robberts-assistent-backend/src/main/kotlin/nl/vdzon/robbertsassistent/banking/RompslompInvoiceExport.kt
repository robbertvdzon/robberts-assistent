package nl.vdzon.robbertsassistent.banking

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Leest de factuurexport van Rompslomp (Facturen → Exporteer → .xls, in werkelijkheid een .xlsx).
 * Bewust zonder Excel-bibliotheek: het bestand komt van buiten, dus alles is begrensd en
 * DOCTYPE/externe entiteiten staan uit.
 */
object RompslompInvoiceExport {
    const val MAX_FILE_BYTES = 2_000_000
    private const val MAX_ENTRY_BYTES = 20_000_000
    private const val MAX_INVOICES = 1_000
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    fun parse(bytes: ByteArray): List<BankInvoice> {
        require(bytes.size in 1..MAX_FILE_BYTES) { "Bestand is leeg of te groot" }
        val entries = unzip(bytes)
        val sheet = entries.filterKeys { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }.toSortedMap().values.firstOrNull()
            ?: throw IllegalArgumentException("Geen werkblad gevonden")
        val shared = entries["xl/sharedStrings.xml"]?.let { xml ->
            children(document(xml).documentElement, "si").map { si -> texts(si) }
        }.orEmpty()
        val rows = children(document(sheet).documentElement, "sheetData").flatMap { children(it, "row") }.map { row ->
            children(row, "c").mapIndexed { index, cell ->
                val column = cell.getAttribute("r").takeWhile { it.isLetter() }.ifBlank { null }?.let(::columnIndex) ?: index
                val raw = children(cell, "v").firstOrNull()?.textContent.orEmpty()
                column to when (cell.getAttribute("t")) {
                    "s" -> shared.getOrElse(raw.toIntOrNull() ?: -1) { "" }
                    "inlineStr" -> texts(cell)
                    else -> raw
                }
            }.toMap()
        }
        val header = rows.firstOrNull()?.entries?.associate { it.value.trim() to it.key }.orEmpty()
        fun column(name: String) = header[name] ?: throw IllegalArgumentException("Kolom $name ontbreekt")
        val number = column("Factuurnummer"); val date = column("Datum"); val amount = column("Bedrag incl. BTW")
        val status = header["Status"]; val due = header["Vervaldatum"]; val description = header["Beschrijving"]
        val company = header["Klant bedrijfsnaam"]; val name = header["Klant naam"]; val paid = header["Betaald Totaal"]
        val invoices = rows.drop(1).mapNotNull { row ->
            fun cell(index: Int?) = index?.let { row[it] }.orEmpty().trim()
            val id = cell(number).removeSuffix(".0")
            // Concepten zijn nog niet verstuurd en horen niet in een betaalcontrole.
            if (id.isBlank() || cell(status).lowercase() in setOf("draft", "concept")) return@mapNotNull null
            require(id.length <= 40 && id.matches(Regex("[A-Za-z0-9._-]+"))) { "Onverwacht factuurnummer" }
            BankInvoice(id, date(cell(date)) ?: throw IllegalArgumentException("Factuur zonder datum"),
                date(cell(due)).orEmpty(), cell(description).take(300), money(cell(amount)),
                cell(company).take(120), cell(name).take(120), cell(paid).ifBlank { "0" }.let(::money))
        }
        require(invoices.isNotEmpty()) { "Geen facturen gevonden" }
        require(invoices.size <= MAX_INVOICES && invoices.map { it.number }.distinct().size == invoices.size) { "Onverwacht aantal facturen" }
        return invoices
    }

    private fun money(value: String) = value.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
    /** Excel bewaart datums als dagnummer; een ISO-datum als tekst accepteren we ook. */
    private fun date(value: String): String? = when {
        value.isBlank() -> null
        value.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) -> LocalDate.parse(value.take(10)).toString()
        else -> value.toBigDecimal().toLong().also { require(it in 20_000..80_000) { "Onverwachte datum" } }
            .let { excelEpoch.plusDays(it).toString() }
    }
    private fun columnIndex(letters: String) = letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    private fun children(parent: Element, name: String): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.localName == name }
    }
    private fun texts(element: Element): String {
        val nodes = element.getElementsByTagNameNS(NS, "t")
        return (0 until nodes.length).joinToString("") { nodes.item(it).textContent }
    }
    private fun document(xml: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val wanted = Regex("xl/sharedStrings\\.xml|xl/worksheets/sheet\\d+\\.xml")
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.name.matches(wanted)) continue
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ENTRY_BYTES) { "Bestand is uitgepakt te groot" }
                        out.write(buffer, 0, read)
                    }
                    entries[entry.name] = out.toByteArray()
                }
            }
        } catch (ex: java.io.IOException) {
            throw IllegalArgumentException("Geen geldig Excel-bestand", ex)
        }
        return entries
    }
}
