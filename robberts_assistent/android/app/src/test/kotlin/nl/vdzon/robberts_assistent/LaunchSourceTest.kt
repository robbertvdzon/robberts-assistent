package nl.vdzon.robberts_assistent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test uitsluitend de pure [LaunchSource.classify]: die raakt bewust geen Android-classes, zodat
 * hij in een gewone JVM-unittest te draaien is. Wat Google Assistent/Gemini daadwerkelijk als
 * referrer meestuurt is alleen op een echt toestel vast te stellen (zie de APP_LAUNCH-logregels).
 */
class LaunchSourceTest {

    @Test
    fun `ontbrekende of lege referrer geeft UNKNOWN`() {
        assertEquals(LaunchSourceType.UNKNOWN, LaunchSource.classify(null))
        assertEquals(LaunchSourceType.UNKNOWN, LaunchSource.classify(""))
        assertEquals(LaunchSourceType.UNKNOWN, LaunchSource.classify("   "))
    }

    @Test
    fun `de bekende assistent-packages geven ASSISTANT`() {
        listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.googleassistant",
            "com.google.android.apps.bard",
            "com.google.android.apps.gemini",
        ).forEach { assertEquals(LaunchSourceType.ASSISTANT, LaunchSource.classify(it)) }
    }

    @Test
    fun `bekende launchers en alles op punt launcher geven LAUNCHER`() {
        assertEquals(LaunchSourceType.LAUNCHER, LaunchSource.classify("com.google.android.apps.nexuslauncher"))
        assertEquals(LaunchSourceType.LAUNCHER, LaunchSource.classify("com.android.launcher3"))
        assertEquals(LaunchSourceType.LAUNCHER, LaunchSource.classify("com.example.iets.launcher"))
    }

    @Test
    fun `elk ander package geeft OTHER`() {
        assertEquals(LaunchSourceType.OTHER, LaunchSource.classify("com.whatsapp"))
        assertEquals(LaunchSourceType.OTHER, LaunchSource.classify("nl.vdzon.notities"))
    }
}
