package nl.vdzon.robbertsassistent.briefing

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HolidaysTest {

    @Test
    fun `berekent Pasen 2026 correct`() {
        // Eerste Paasdag 2026 valt op 5 april (bekende referentiedatum).
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 4, 5)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 4, 6))) // Tweede Paasdag
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 4, 3))) // Goede Vrijdag
    }

    @Test
    fun `kent de vaste feestdagen`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 1, 1)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 5, 5)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 12, 25)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 12, 26)))
    }

    @Test
    fun `koningsdag schuift naar 26 april als 27 april op zondag valt`() {
        // 27 april 2025 is een zondag.
        assertEquals(LocalDate.of(2025, 4, 26), Holidays.holidaysOf(2025).single { it.month.value == 4 && it.dayOfMonth in 26..27 })
        assertFalse(Holidays.isHoliday(LocalDate.of(2025, 4, 27)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2025, 4, 26)))
    }

    @Test
    fun `koningsdag is normaal 27 april`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 4, 27)))
    }

    @Test
    fun `gewone werkdag is geen feestdag`() {
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 7, 22)))
    }
}
