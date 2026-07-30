package nl.vdzon.robbertsassistent.watches

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchVerdictTest {
    @Test
    fun `parseert GEVONDEN met statuszin`() {
        val verdict = WatchVerdict.parse("GEVONDEN\nNu op voorraad.")
        assertEquals(WatchStatus.GEVONDEN, verdict.status)
        assertEquals("Nu op voorraad.", verdict.statusText)
    }

    @Test
    fun `parseert NIET GEVONDEN ook al bevat het de substring GEVONDEN`() {
        val verdict = WatchVerdict.parse("NIET GEVONDEN\nNog steeds uitverkocht.")
        assertEquals(WatchStatus.NIET_GEVONDEN, verdict.status)
        assertEquals("Nog steeds uitverkocht.", verdict.statusText)
    }

    @Test
    fun `is ongevoelig voor hoofdletters en omliggende witruimte`() {
        val verdict = WatchVerdict.parse("  gevonden  \n  Beschikbaar!  ")
        assertEquals(WatchStatus.GEVONDEN, verdict.status)
        assertEquals("Beschikbaar!", verdict.statusText)
    }

    @Test
    fun `valt terug op ONBEKEND bij een leeg antwoord`() {
        val verdict = WatchVerdict.parse("")
        assertEquals(WatchStatus.ONBEKEND, verdict.status)
        assertEquals("", verdict.statusText)
    }

    @Test
    fun `valt terug op ONBEKEND bij een onverwacht antwoord (bv MockChatModel)`() {
        val verdict = WatchVerdict.parse("Mock-antwoord (geen echte AI in deze omgeving) op: \"...\"")
        assertEquals(WatchStatus.ONBEKEND, verdict.status)
    }

    @Test
    fun `pakt de tweede regel als statuszin ook als er meer regels zijn`() {
        val verdict = WatchVerdict.parse("GEVONDEN\nEindelijk beschikbaar\nnog wat extra tekst")
        assertEquals(WatchStatus.GEVONDEN, verdict.status)
        assertEquals("Eindelijk beschikbaar", verdict.statusText)
    }
}
