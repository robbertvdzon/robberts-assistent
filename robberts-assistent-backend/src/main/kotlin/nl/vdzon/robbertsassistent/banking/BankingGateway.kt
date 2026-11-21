package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate

interface BankingGateway {
    fun start(state: String): String
    fun session(code: String): JsonNode
    fun transactions(uid: String, from: LocalDate, to: LocalDate): List<JsonNode>
    fun check(): Boolean
}
class DisabledBankingGateway : BankingGateway {
    override fun start(state: String): String = error("Bankkoppeling is nog niet ingesteld")
    override fun session(code: String): JsonNode = error("Bankkoppeling is nog niet ingesteld")
    override fun transactions(uid: String, from: LocalDate, to: LocalDate): List<JsonNode> = error("Bankkoppeling is nog niet ingesteld")
    override fun check() = false
}
