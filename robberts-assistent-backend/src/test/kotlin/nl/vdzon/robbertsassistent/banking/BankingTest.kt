package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

class BankingTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val owner = "test@example.invalid"
    private val settings = BankSettings("test-app", "unused", "unused", owner, "https://example.invalid/callback")
    private val store = MemoryBankStore(mapper)
    private val gateway = mock(BankingGateway::class.java)
    private val service = BankService(settings, store, gateway)
    private val today = LocalDate.now(java.time.ZoneId.of("Europe/Amsterdam"))
    private val date = today.minusDays(3).toString()
    private val month = date.take(7)
    private val account = BankAccount("stable-account", "00000000-0000-0000-0000-000000000001", "NL00TEST0000000001", "Testrekening", "secret-session", Instant.now().plusSeconds(3600))
    private fun transaction(ref: String = "entry-1") = mapper.readTree("""
        {"entry_reference":"$ref","transaction_amount":{"amount":"10.05","currency":"EUR"},
        "booking_date":"$date","credit_debit_indicator":"DBIT","status":"BOOK",
        "creditor":{"name":"Testwinkel"},"creditor_account":{"iban":"NL00TEST0000000002"},"remittance_information":["Aankoop test"]}
    """)
    private fun seed() = store.write(owner, "connection", BankConnection(listOf(account)))
    private fun row(id: String, amount: String, category: BankCategory = BankCategory.REVIEW) =
        BankTransaction(id, account.id, date, amount.toBigDecimal(), "EUR", "Test", "", "", category)

    @Test fun `interne overboekingen tellen niet mee en onbekend is geen extra`() {
        val report = BankLedger.month(month, listOf(row("1", "-1000.00", BankCategory.INTERNAL), row("2", "-20.10"),
            row("3", "-40.10", BankCategory.EXTRA), row("4", "10.05", BankCategory.EXTRA), row("5", "100.00", BankCategory.INCOME)), emptyList())
        assertEquals("60.20", report.spent.toPlainString())
        assertEquals("110.05", report.received.toPlainString())
        assertEquals("30.05", report.extra.toPlainString())
        assertEquals("20.10", report.review.toPlainString())
    }
    @Test fun `eigen rekening en regels bepalen categorie maar handmatige correctie blijft`() {
        val t = row("1", "-10.00").copy(counterpartyIban = account.iban, counterparty = "Testwinkel")
        assertEquals(BankCategory.INTERNAL, BankLedger.classify(t, listOf(account), emptyList()).category)
        val rule = BankRule("test", "Testwinkel", "", BankCategory.EXTRA)
        assertEquals(BankCategory.EXTRA, BankLedger.classify(t.copy(counterpartyIban = ""), emptyList(), listOf(rule)).category)
        assertEquals(BankCategory.HEALTH, BankLedger.classify(t.copy(manual = true, category = BankCategory.HEALTH), listOf(account), listOf(rule)).category)
    }
    @Test fun `herhaald ophalen telt niet dubbel en bewaart correcties`() {
        seed()
        `when`(gateway.transactions(account.uid, today.minusYears(1), today)).thenReturn(listOf(transaction(), transaction()))
        `when`(gateway.transactions(account.uid, today.minusDays(89), today)).thenReturn(listOf(transaction()))
        service.sync(owner)
        val first = service.month(owner, month).transactions.single()
        service.classify(owner, month, first.id, BankCategory.EXTRA)
        service.sync(owner)
        val report = service.month(owner, month)
        assertEquals(1, report.transactions.size)
        assertEquals("10.05", report.extra.toPlainString())
    }
    @Test fun `extra bankregels buiten de periode blokkeren geldige transacties niet`() {
        seed()
        fun dated(ref: String, date: LocalDate) = (transaction(ref).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>())
            .put("booking_date", date.toString())
        `when`(gateway.transactions(account.uid, today.minusYears(1), today)).thenReturn(listOf(
            dated("too-old", today.minusYears(1).minusDays(1)),
            dated("first-day", today.minusYears(1)),
            transaction("inside"), dated("last-day", today), dated("future", today.plusDays(1))))
        service.sync(owner)
        val imported = listOf(today.minusYears(1).toString().take(7), month, today.toString().take(7)).distinct()
            .flatMap { service.month(owner, it).transactions }
        assertEquals(3, imported.size)
        assertTrue(imported.all { it.date >= today.minusYears(1).toString() && it.date <= today.toString() })
        assertNotNull(service.connection(owner).lastSync)
    }
    @Test fun `twee identieke betalingen zonder referentie blijven twee betalingen`() {
        val counts = mutableMapOf<String, Int>()
        val first = parseTransaction(account.id, transaction(""), counts)
        val second = parseTransaction(account.id, transaction(""), counts)
        assertNotEquals(first.id, second.id)
        assertFalse(first.stableReference)
        assertEquals(first.id, parseTransaction(account.id, transaction(""), mutableMapOf()).id)
    }
    @Test fun `importfout bewaart oude gegevens en laatste succesdatum`() {
        seed()
        store.write(owner, "month-$month", BankTransactions(listOf(row("old", "-12.00"))))
        `when`(gateway.transactions(account.uid, today.minusYears(1), today)).thenThrow(BankUnavailableException())
        assertThrows(BankUnavailableException::class.java) { service.sync(owner) }
        assertNull(service.connection(owner).lastSync)
        assertEquals("old", service.month(owner, month).transactions.single().id)
    }
    @Test fun `toegang is aan eigenaar gebonden`() {
        seed()
        assertThrows(ResponseStatusException::class.java) { service.month("another@example.invalid", month) }
        assertNull(store.read("another@example.invalid", "connection", BankConnection::class.java))
        verifyNoInteractions(gateway)
    }
    @Test fun `callback weigert onbekende verlopen en hergebruikte state voor codeuitwisseling`() {
        assertFalse(service.callback("a".repeat(43), "code", null))
        store.write("pending", bankHash("b".repeat(43)), PendingBankAuth(owner, Instant.now().minusSeconds(1)))
        assertFalse(service.callback("b".repeat(43), "code", null))
        assertFalse(service.callback("b".repeat(43), "code", null))
        store.write("pending", bankHash("c".repeat(43)), PendingBankAuth(owner, Instant.now().plusSeconds(600)))
        assertFalse(service.callback("c".repeat(43), null, "access_denied"))
        verifyNoInteractions(gateway)
    }
    @Test fun `succesvolle callback wisselt code eenmaal uit`() {
        val state = "s".repeat(43)
        store.write("pending", bankHash(state), PendingBankAuth(owner, Instant.now().plusSeconds(600)))
        `when`(gateway.session("one-time-code")).thenReturn(mapper.readTree("""{"session_id":"secret-session","access":{"valid_until":"${account.validUntil}"},
            "accounts":[{"account_id":{"iban":"${account.iban}"},"name":"Testrekening","uid":"${account.uid}","identification_hash":"stable-hash"}]}"""))
        `when`(gateway.transactions(account.uid, today.minusYears(1), today)).thenReturn(emptyList())
        assertTrue(service.callback(state, "one-time-code", null))
        assertFalse(service.callback(state, "one-time-code", null))
        verify(gateway, times(1)).session("one-time-code")
        assertEquals(1, service.connection(owner).accounts.size)
    }
    @Test fun `versleuteling voorkomt plaintext en omwisselen tussen eigenaars`() {
        val cipher = BankCipher(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val encrypted = cipher.encrypt("private banktransactie", "owner/record")
        assertFalse(encrypted.contains("banktransactie"))
        assertEquals("private banktransactie", cipher.decrypt(encrypted, "owner/record"))
        assertThrows(Exception::class.java) { cipher.decrypt(encrypted, "other/record") }
        assertNotEquals(encrypted, cipher.encrypt("private banktransactie", "owner/record"))
    }
    @Test fun `bankgegenereerde SEPA metadata vult ontbrekend tegenrekeningveld aan`() {
        val raw = transaction().deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        raw.remove("creditor_account")
        raw.putArray("remittance_information").add("SEPA Periodieke overb. IBAN: NL00TEST0000000002 BIC: TESTNL2A Naam: Eigen rekening")
        assertEquals("NL00TEST0000000002", parseTransaction(account.id, raw, mutableMapOf()).counterpartyIban)
        raw.putArray("remittance_information").add("Aankoop met tekst IBAN: NL00TEST0000000002 BIC: TESTNL2A")
        assertEquals("", parseTransaction(account.id, raw, mutableMapOf()).counterpartyIban)
    }
    @Test fun `afwijkende valuta wordt niet stilzwijgend bij euro opgeteld`() {
        assertThrows(IllegalArgumentException::class.java) { parseTransaction(account.id,
            mapper.readTree(transaction().toString().replace("EUR", "USD")), mutableMapOf()) }
    }
    @Test fun `lege regel mag niet alles als extra rubriceren`() {
        assertThrows(IllegalArgumentException::class.java) { service.saveRules(owner, BankRules(listOf(BankRule("empty", "", "", BankCategory.EXTRA)))) }
    }
}
