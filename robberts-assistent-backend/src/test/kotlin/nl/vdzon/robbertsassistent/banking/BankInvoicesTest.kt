package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.auth.AuthService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bouwt een minimale .xlsx zoals Rompslomp die levert: gedeelde strings, datums als dagnummer. */
internal fun invoiceXlsx(rows: List<List<String>>, header: List<String> = listOf("Factuurnummer", "Status", "Datum",
    "Beschrijving", "Bedrag incl. BTW", "Klant bedrijfsnaam", "Klant naam", "Klant emailadres", "Vervaldatum", "Betaald Totaal")): ByteArray {
    val strings = mutableListOf<String>()
    fun cell(column: Int, row: Int, value: String): String {
        val ref = "${'A' + column}${row + 1}"
        if (value.isEmpty()) return ""
        return if (value.toDoubleOrNull() != null && row > 0 && column != 0) "<c r=\"$ref\"><v>$value</v></c>"
        else "<c r=\"$ref\" t=\"s\"><v>${strings.size.also { strings += value }}</v></c>"
    }
    val sheet = (listOf(header) + rows).mapIndexed { r, row -> "<row r=\"${r + 1}\">" + row.mapIndexed { c, v -> cell(c, r, v) }.joinToString("") + "</row>" }
    val ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
        zip.write("<sst $ns>${strings.joinToString("") { "<si><t>$it</t></si>" }}</sst>".toByteArray())
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write("<worksheet $ns><sheetData>${sheet.joinToString("")}</sheetData></worksheet>".toByteArray())
    }
    return out.toByteArray()
}

class BankInvoicesTest {
    private val today = LocalDate.parse("2026-11-21")
    private fun invoice(number: String, amount: String, company: String = "Bakker Meijer", date: String = "2026-10-01",
        due: String = "2026-10-31", name: String = "Jan Meijer") =
        BankInvoice(number, date, due, "Ontwerp", amount.toBigDecimal(), company, name)
    private fun credit(id: String, amount: String, description: String, counterparty: String = "Iemand", date: String = "2026-10-10") =
        BankTransaction(id.padEnd(64, '0'), "account", date, amount.toBigDecimal(), "EUR", counterparty, "", description)
    private fun match(invoices: List<BankInvoice>, vararg transactions: BankTransaction) =
        BankInvoiceMatcher.match(invoices, transactions.toList(), today).associateBy { it.number }

    @Test fun `export is parsed with excel dates and without address data`() {
        val invoices = RompslompInvoiceExport.parse(invoiceXlsx(listOf(
            listOf("2026110033", "published", "46346.0", "Waardebon", "121.0", "Marcel Sport", "Corina", "c@example.invalid", "46376.0", "0"),
            listOf("2026110034", "draft", "46346.0", "Concept", "50.0", "Klant", "", "", "46376.0", "0"),
            listOf("2026110032", "published", "46341.0", "Kaartje", "181.5", "Bakker Meijer", "Jan Meijer", "", "", ""))))
        assertEquals(listOf("2026110033", "2026110032"), invoices.map { it.number })
        assertEquals(BankInvoice("2026110033", "2026-11-20", "2026-12-20", "Waardebon", "121.00".toBigDecimal(), "Marcel Sport", "Corina",
            "0.00".toBigDecimal()), invoices[0])
        assertEquals("", invoices[1].dueDate)
        assertEquals("181.50".toBigDecimal(), invoices[1].amount)
    }

    @Test fun `other files are rejected`() {
        assertThrows<IllegalArgumentException> { RompslompInvoiceExport.parse("geen zip".toByteArray()) }
        assertThrows<IllegalArgumentException> { RompslompInvoiceExport.parse(invoiceXlsx(emptyList())) }
        assertThrows<IllegalArgumentException> { RompslompInvoiceExport.parse(invoiceXlsx(listOf(listOf("1", "x")), listOf("Nummer", "Iets"))) }
        assertThrows<IllegalArgumentException> { RompslompInvoiceExport.parse(ByteArray(RompslompInvoiceExport.MAX_FILE_BYTES + 1)) }
    }

    @Test fun `invoice number in description settles the invoice`() {
        val result = match(listOf(invoice("2026100001", "121.00"), invoice("2026100002", "121.00")),
            credit("a", "121.00", "Betaling factuur 2026100002"), credit("b", "121.00", "nr 2026 1000 01 dank"))
        assertEquals(BankInvoiceStatus.PAID, result.getValue("2026100002").status)
        assertEquals("a".padEnd(64, '0'), result.getValue("2026100002").payments.single().transactionId)
        assertEquals(BankInvoiceStatus.PAID, result.getValue("2026100001").status)
        assertFalse(invoiceNumberMentioned("2026100001", "ref 12026100001"))
    }

    @Test fun `one transfer can pay several invoices and partial payments add up`() {
        val result = match(listOf(invoice("2026100001", "121.00"), invoice("2026100002", "60.50"), invoice("2026100003", "200.00")),
            credit("a", "181.50", "2026100001 en 2026100002"),
            credit("b", "150.00", "2026100003 deel 1"), credit("c", "50.00", "2026100003 deel 2"))
        assertTrue(result.values.all { it.status == BankInvoiceStatus.PAID })
        assertEquals(2, result.getValue("2026100003").payments.size)
    }

    @Test fun `wrong amount with invoice number needs a check and counts as overdue`() {
        val view = match(listOf(invoice("2026100001", "121.00")), credit("a", "100.00", "2026100001")).getValue("2026100001")
        assertEquals(BankInvoiceStatus.CHECK, view.status)
        assertTrue(view.overdue)
        assertTrue(view.reason.contains("€ 100,00"))
    }

    @Test fun `same amount and customer name is only likely and each transfer is used once`() {
        val result = match(listOf(invoice("2026100001", "121.00"), invoice("2026100002", "121.00", date = "2026-10-05"),
            invoice("2026100003", "121.00", company = "Marcel Sport", name = "Corina")),
            credit("a", "121.00", "bedankt", "Bakkerij J. Meijer / Bakker Meijer", "2026-10-12"),
            credit("b", "121.00", "ontwerp", "Gemeente Heemskerk", "2026-10-13"),
            credit("c", "121.00", "ontwerp", "J MEIJER", "2026-09-01"))
        assertEquals(BankInvoiceStatus.LIKELY, result.getValue("2026100001").status)
        assertEquals(BankInvoiceStatus.OPEN, result.getValue("2026100002").status)
        // Zonder naam blijft het een suggestie; een bijschrijving van vóór de factuurdatum telt nooit.
        assertEquals(listOf("b".padEnd(64, '0')), result.getValue("2026100003").candidates.map { it.transactionId })
        assertTrue(result.getValue("2026100003").overdue)
    }

    @Test fun `own decisions win and rejected transfers are not offered again`() {
        val tx = credit("a", "121.00", "bedankt", "Bakker Meijer")
        val rejected = invoice("2026100001", "121.00").copy(rejectedTransactionIds = listOf(tx.id))
        assertEquals(BankInvoiceStatus.OPEN, match(listOf(rejected), tx).getValue("2026100001").status)
        assertTrue(match(listOf(rejected), tx).getValue("2026100001").candidates.isEmpty())
        val manual = match(listOf(invoice("2026100001", "121.00").copy(manualPaid = true)), tx).getValue("2026100001")
        assertEquals(BankInvoiceStatus.PAID, manual.status)
        assertTrue(manual.manual)
        val export = match(listOf(invoice("2026100001", "121.00").copy(exportPaid = "121.00".toBigDecimal()))).getValue("2026100001")
        assertEquals("Volgens Rompslomp betaald", export.reason)
        // Uitgaven en interne overboekingen zijn geen betaling van een factuur.
        val internal = tx.copy(category = BankCategory.INTERNAL, description = "2026100001")
        assertEquals(BankInvoiceStatus.OPEN, match(listOf(invoice("2026100001", "121.00")), internal,
            credit("d", "-121.00", "2026100001")).getValue("2026100001").status)
    }

    private val owner = "owner@example.invalid"
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val store = MemoryBankStore(mapper)
    private val settings = BankSettings("test-app", "unused", "unused", owner, "https://example.invalid/callback")
    private val bank = BankService(settings, store, mock(BankingGateway::class.java))
    private val auth = mock(AuthService::class.java).also {
        `when`(it.requireAuthorization("Bearer owner")).thenReturn(owner)
        `when`(it.requireAuthorization("Bearer other")).thenReturn("other@example.invalid")
        `when`(it.requireAuthorization(null)).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED))
    }
    private val mvc = MockMvcBuilders.standaloneSetup(BankInvoiceController(auth, bank, BankInvoiceService(bank, store))).build()

    @Test fun `import report and decisions are private to the owner and survive a new export`() {
        val url = "/api/v1/banking/invoices"
        val file = invoiceXlsx(listOf(listOf("2026010001", "published", "46043.0", "Flyer", "181.5", "inheemskerk.nl", "Petra", "", "46073.0", "0")))
        val body = mapper.writeValueAsString(mapOf("content" to Base64.getEncoder().encodeToString(file)))
        mvc.perform(get(url)).andExpect(status().isUnauthorized)
        mvc.perform(get(url).header("Authorization", "Bearer other")).andExpect(status().isForbidden)
        mvc.perform(post("$url/import").header("Authorization", "Bearer other").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(post("$url/import").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
            .content("""{"content":"bm9wZQ=="}""")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").exists())
        mvc.perform(post("$url/import").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.invoices[0].number").value("2026010001")).andExpect(jsonPath("$.invoices[0].status").value("OPEN"))
            .andExpect(jsonPath("$.invoices[0].overdue").value(true)).andExpect(jsonPath("$.open.count").value(1))
            .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("Koppel de rekening")))
        store.write(owner, "month-2026-02", BankTransactions(listOf(BankTransaction("a".repeat(64), "account", "2026-02-03",
            "181.50".toBigDecimal(), "EUR", "Stichting InHeemskerk", "", "Factuur 2026010001"))))
        mvc.perform(get(url).header("Authorization", "Bearer owner")).andExpect(jsonPath("$.invoices[0].status").value("PAID"))
            .andExpect(jsonPath("$.paid.amount").value(181.5)).andExpect(jsonPath("$.open.count").value(0))
        val reject = """{"action":"REJECT","transactionId":"${"a".repeat(64)}"}"""
        mvc.perform(put("$url/2026010001").header("Authorization", "Bearer other").contentType(MediaType.APPLICATION_JSON).content(reject))
            .andExpect(status().isForbidden)
        mvc.perform(put("$url/2026010001").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON).content(reject))
            .andExpect(status().isOk).andExpect(jsonPath("$.invoices[0].status").value("OPEN"))
        mvc.perform(post("$url/import").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.invoices[0].status").value("OPEN")).andExpect(jsonPath("$.invoices.length()").value(1))
        mvc.perform(put("$url/2026010001").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
            .content("""{"action":"RESET"}""")).andExpect(jsonPath("$.invoices[0].status").value("PAID"))
        mvc.perform(put("$url/onbekend").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
            .content("""{"action":"PAID"}""")).andExpect(status().isNotFound)
    }
}
