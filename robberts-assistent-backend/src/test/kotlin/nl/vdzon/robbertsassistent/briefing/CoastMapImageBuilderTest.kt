package nl.vdzon.robbertsassistent.briefing

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dekt de pure overlay-/icoonlogica zonder netwerk-call (`OsmCoastMapImageBuilder.fetchMap` doet
 * een echte OSM-tegel-HTTP-call en wordt daarom niet in een unit-test aangeroepen — zelfde
 * "pure functie zonder HTTP"-patroon als `OpenMeteoWindForecastClientTest`).
 */
class CoastMapImageBuilderTest {

    @Test
    fun `drawOverlay crasht niet en levert een beschrijfbare afbeelding op`() {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, speedKn = 24.0, directionDeg = 270.0, weatherCode = 0)

        assertEquals(256, image.width)
        assertEquals(256, image.height)
    }

    @Test
    fun `weatherIcon geeft zon terug bij helder weer`() {
        assertEquals("☀️", weatherIcon(0))
        assertEquals("☀️", weatherIcon(1))
    }

    @Test
    fun `weatherIcon geeft onweerswolk terug bij onweer`() {
        assertEquals("⛈️", weatherIcon(95))
        assertEquals("⛈️", weatherIcon(99))
    }

    @Test
    fun `weatherIcon geeft regenwolk terug bij regen`() {
        assertEquals("🌧️", weatherIcon(61))
        assertEquals("🌧️", weatherIcon(80))
    }

    @Test
    fun `weatherIcon geeft wolk terug als terugvaloptie`() {
        assertEquals("☁️", weatherIcon(3))
    }

    @Test
    fun `StubCoastMapImageBuilder levert geldige PNG-bytes zonder netwerk-call`() {
        val bytes = StubCoastMapImageBuilder().build(speedKn = 24.0, directionDeg = 270.0, weatherCode = 0)

        assertTrue(bytes.isNotEmpty())
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }
}
