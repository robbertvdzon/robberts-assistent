package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.MowerActionResult
import nl.vdzon.robbertsassistent.automower.MowerStatus
import nl.vdzon.robbertsassistent.automower.MowerStatusResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutomowerToolsTest {

    private class FakeAutomowerClient(
        private val statusResult: MowerStatusResult,
        private val startResult: MowerActionResult = MowerActionResult(true),
        private val parkResult: MowerActionResult = MowerActionResult(true),
    ) : AutomowerClient {
        var lastStartDuration: Int? = null
        override fun status(): MowerStatusResult = statusResult
        override fun startMowing(durationMinutes: Int): MowerActionResult {
            lastStartDuration = durationMinutes
            return startResult
        }
        override fun park(): MowerActionResult = parkResult
    }

    @Test
    fun `getMowerStatus toont naam, activiteit, status en batterij`() {
        val tools = AutomowerTools(
            FakeAutomowerClient(
                MowerStatusResult(
                    listOf(
                        MowerStatus(
                            name = "Testmaaier",
                            model = "Automower 310E",
                            mode = "MAIN_AREA",
                            activity = "MOWING",
                            state = "IN_OPERATION",
                            batteryPercent = 78,
                            errorCode = 0,
                            connected = true,
                        ),
                    ),
                ),
            ),
        )

        val result = tools.getMowerStatus()

        assertTrue(result.contains("Testmaaier"), result)
        assertTrue(result.contains("maait"), result)
        assertTrue(result.contains("batterij 78%"), result)
    }

    @Test
    fun `getMowerStatus geeft de foutmelding door als de client een error teruggeeft`() {
        val tools = AutomowerTools(FakeAutomowerClient(MowerStatusResult(emptyList(), "Kon niet ophalen")))

        assertEquals("Kon niet ophalen", tools.getMowerStatus())
    }

    @Test
    fun `startMowing geeft de duur door en meldt succes`() {
        val client = FakeAutomowerClient(MowerStatusResult(emptyList()))
        val tools = AutomowerTools(client)

        val result = tools.startMowing(30)

        assertEquals(30, client.lastStartDuration)
        assertTrue(result.contains("30 minuten"), result)
    }

    @Test
    fun `parkMower meldt succes`() {
        val tools = AutomowerTools(FakeAutomowerClient(MowerStatusResult(emptyList())))

        assertTrue(tools.parkMower().contains("laadstation"))
    }
}
