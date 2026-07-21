package nl.vdzon.robbertsassistent.briefing

import java.time.LocalDate

/**
 * Algoritmische berekening van Nederlandse feestdagen (geen externe koppeling of hardcoded lijst
 * per jaar nodig) — gebruikt door [KiteSectionProvider] om te bepalen of morgen een werkdag is.
 * Pasen wordt berekend met de Meeus/Jones/Butcher-Gregoriaanse-algoritme; de overige feestdagen
 * zijn er in dagen vanaf afgeleid (Goede Vrijdag -2, Paasmaandag +1, Hemelvaart +39,
 * Pinkstermaandag +50) of hebben een vaste datum. Koningsdag schuift naar 26 april als 27 april op
 * een zondag valt (regel sinds 2014).
 */
object Holidays {

    /** Of [date] een Nederlandse feestdag is (rijksfeestdagen + de gangbare christelijke feestdagen). */
    fun isHoliday(date: LocalDate): Boolean = holidaysOf(date.year).contains(date)

    /** Alle Nederlandse feestdagen in [year]. */
    fun holidaysOf(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        return setOf(
            LocalDate.of(year, 1, 1), // Nieuwjaarsdag
            easter.minusDays(2), // Goede Vrijdag
            easter, // Eerste Paasdag
            easter.plusDays(1), // Tweede Paasdag
            kingsDay(year), // Koningsdag
            LocalDate.of(year, 5, 5), // Bevrijdingsdag
            easter.plusDays(39), // Hemelvaartsdag
            easter.plusDays(49), // Eerste Pinksterdag
            easter.plusDays(50), // Tweede Pinksterdag
            LocalDate.of(year, 12, 25), // Eerste Kerstdag
            LocalDate.of(year, 12, 26), // Tweede Kerstdag
        )
    }

    /** 27 april, tenzij dat een zondag is — dan 26 april (regel sinds 2014). */
    private fun kingsDay(year: Int): LocalDate {
        val default = LocalDate.of(year, 4, 27)
        return if (default.dayOfWeek == java.time.DayOfWeek.SUNDAY) default.minusDays(1) else default
    }

    /** Meeus/Jones/Butcher-algoritme (Gregoriaanse kalender). */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
