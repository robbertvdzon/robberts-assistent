package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.waste.WasteClient
import nl.vdzon.robbertsassistent.waste.WastePickup
import nl.vdzon.robbertsassistent.waste.WasteSchedule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WasteSectionProviderTest {

    private class FixedWasteClient(private val schedule: WasteSchedule) : WasteClient {
        var calls = 0

        override fun upcomingPickups(): WasteSchedule {
            calls++
            return schedule
        }
    }

    @Test
    fun `section toont ophaalmomenten binnen 7 dagen oplopend op datum`() {
        val today = LocalDate.now()
        val provider = WasteSectionProvider(
            FixedWasteClient(
                WasteSchedule(
                    listOf(
                        WastePickup("gft & etensresten", today.plusDays(2)),
                        WastePickup("plastic, blik & drinkpakken", today.plusDays(6)),
                        WastePickup("restafval", today.plusDays(10)),
                    ),
                ),
            ),
        )

        val section = provider.section()

        assertEquals("waste", section.key)
        assertTrue(section.text.contains("gft & etensresten"))
        assertTrue(section.text.contains("plastic, blik & drinkpakken"))
        assertTrue(!section.text.contains("restafval"))
        val gftIndex = section.text.indexOf("gft & etensresten")
        val plasticIndex = section.text.indexOf("plastic, blik & drinkpakken")
        assertTrue(gftIndex < plasticIndex)
        assertEquals(BriefingStatus.GOED, section.status)
        assertEquals("gft", section.tileLabel)
    }

    @Test
    fun `section toont neutrale tekst zonder ophaalmomenten binnen 7 dagen`() {
        val today = LocalDate.now()
        val provider = WasteSectionProvider(
            FixedWasteClient(WasteSchedule(listOf(WastePickup("restafval", today.plusDays(10))))),
        )

        val section = provider.section()

        assertTrue(section.text.contains("Geen ophaalmomenten"))
        assertEquals(BriefingStatus.GOED, section.status)
        assertEquals("geen", section.tileLabel)
    }

    @Test
    fun `section degradeert stil bij een fout`() {
        val provider = WasteSectionProvider(
            FixedWasteClient(WasteSchedule(emptyList(), error = "kapot")),
        )

        val section = provider.section()

        assertTrue(section.text.contains("kapot"))
        assertNull(section.status)
        assertNull(section.tileLabel)
    }

    @Test
    fun `section geeft let op voor vandaag en morgen`() {
        for (days in 0L..1L) {
            val provider = WasteSectionProvider(
                FixedWasteClient(
                    WasteSchedule(listOf(WastePickup("restafval", LocalDate.now().plusDays(days)))),
                ),
            )

            val section = provider.section()

            assertEquals(BriefingStatus.LET_OP, section.status)
            assertEquals("rest", section.tileLabel)
        }
    }

    @Test
    fun `section voegt bakken op de eerstvolgende datum kort samen en haalt planning eenmaal op`() {
        val today = LocalDate.now()
        val client = FixedWasteClient(
            WasteSchedule(
                listOf(
                    WastePickup("papier", today.plusDays(4)),
                    WastePickup("gft & etensresten", today.plusDays(2)),
                    WastePickup("plastic, blik & drinkpakken", today.plusDays(2)),
                ),
            ),
        )

        val section = WasteSectionProvider(client).section()

        assertEquals(BriefingStatus.GOED, section.status)
        assertEquals("gft + pbd", section.tileLabel)
        assertEquals(1, client.calls)
    }

    @Test
    fun `section biedt geen tegel als ophalen een exception gooit`() {
        val provider = WasteSectionProvider(object : WasteClient {
            override fun upcomingPickups(): WasteSchedule = error("boom")
        })

        val section = provider.section()

        assertEquals("Kon de afvalkalender niet ophalen.", section.text)
        assertNull(section.status)
        assertNull(section.tileLabel)
    }

    @Test
    fun `shortSummary geeft ophaal van morgen terug`() {
        val tomorrow = LocalDate.now().plusDays(1)
        val provider = WasteSectionProvider(
            FixedWasteClient(WasteSchedule(listOf(WastePickup("gft & etensresten", tomorrow)))),
        )

        assertEquals("Zet vanavond de gft & etensresten buiten", provider.shortSummary())
    }

    @Test
    fun `shortSummary voegt meerdere types morgen samen`() {
        val tomorrow = LocalDate.now().plusDays(1)
        val provider = WasteSectionProvider(
            FixedWasteClient(
                WasteSchedule(
                    listOf(
                        WastePickup("gft & etensresten", tomorrow),
                        WastePickup("plastic, blik & drinkpakken", tomorrow),
                    ),
                ),
            ),
        )

        assertEquals("Zet vanavond de gft & etensresten & plastic, blik & drinkpakken buiten", provider.shortSummary())
    }

    @Test
    fun `shortSummary is null zonder ophaal morgen`() {
        val provider = WasteSectionProvider(
            FixedWasteClient(WasteSchedule(listOf(WastePickup("restafval", LocalDate.now().plusDays(3))))),
        )

        assertNull(provider.shortSummary())
    }

    @Test
    fun `shortSummary is null bij een fout`() {
        val provider = WasteSectionProvider(
            FixedWasteClient(WasteSchedule(emptyList(), error = "kapot")),
        )

        assertNull(provider.shortSummary())
    }
}
