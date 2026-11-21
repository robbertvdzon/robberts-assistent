package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.assistant.ai.*
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class RuntimeBankClassifierTest {
    @Test fun `kleding kan apart van extra via het beslissingsschema worden ingedeeld`() {
        val mapper = jacksonObjectMapper()
        val runtime = object : AgentRuntimeGateway {
            override fun ping() = true to "test"
            override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode {
                val categories = resultSchema.path("properties").path("decisions").path("items")
                    .path("properties").path("category").path("enum").map { it.asText() }
                assertTrue("CLOTHING" in categories)
                assertTrue(instruction.contains("CLOTHING is voor kleding en schoenen"))
                return mapper.readTree("""{"decisions":[{"id":"clothes","category":"CLOTHING","confidence":0.95,"needsUser":false,"reason":"Herkenbare kledingwinkel","question":""}]}""")
            }
        }
        val decisions = RuntimeBankClassifier(runtime, mapper, mock(AppSecrets::class.java))
            .assess(listOf(BankAiItem("clothes", "Test Kledingzaak", "Kleding", listOf("-50.00"))))
        assertEquals(BankCategory.CLOTHING, decisions.single().category)
        assertFalse(decisions.single().needsUser)
    }

    @Test fun `runtime krijgt geminimaliseerde gegevens en een beperkt beslissingsschema`() {
        val mapper = jacksonObjectMapper()
        var prompt = ""
        val runtime = object : AgentRuntimeGateway {
            override fun ping() = true to "test"
            override fun runStructured(instruction: String, resultSchema: JsonNode, attachments: List<RuntimeAttachment>): JsonNode {
                prompt = instruction
                assertTrue(attachments.isEmpty())
                assertFalse(resultSchema.toString().contains("INTERNAL"))
                return mapper.readTree("""{"decisions":[{"id":"item-0","category":"EXTRA","confidence":0.6,"needsUser":true,"reason":"Doel onduidelijk","question":"Wat heb je gekocht?"}]}""")
            }
        }
        val classifier = RuntimeBankClassifier(runtime, mapper, mock(AppSecrets::class.java))
        val decisions = classifier.assess(listOf(BankAiItem("item-0", "Testwinkel", "Factuur 1234567890 NL91ABNA0417164300", listOf("-50.00"))))
        assertFalse(prompt.contains("1234567890"))
        assertFalse(prompt.contains("NL91ABNA0417164300"))
        assertTrue(prompt.contains("ONBETROUWBARE DATA"))
        assertTrue(decisions.single().needsUser)
        assertEquals("Wat heb je gekocht?", decisions.single().question)
    }
}
