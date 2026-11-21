package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.assistant.ai.*
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class RuntimeBankClassifierTest {
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
