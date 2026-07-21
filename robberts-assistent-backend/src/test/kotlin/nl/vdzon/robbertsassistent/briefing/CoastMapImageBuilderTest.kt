package nl.vdzon.robbertsassistent.briefing

import java.awt.Color
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

    private fun slots() = listOf(
        WindMapSlot(label = "Ochtend", color = Color.ORANGE, speedKn = 12.0, directionDeg = 270.0, weatherCode = 0),
        WindMapSlot(label = "Middag", color = Color.BLUE, speedKn = 18.0, directionDeg = 200.0, weatherCode = 61),
    )

    @Test
    fun `drawOverlay crasht niet en levert een beschrijfbare afbeelding op`() {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots())

        assertEquals(400, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun `drawOverlay werkt ook met één dagdeel`() {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots().take(1))

        assertEquals(256, image.width)
    }

    @Test
    fun `drawOverlay tekent daadwerkelijk pixels voor beide dagdeel-kleuren`() {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots())

        val pixels = (0 until image.width).flatMap { x -> (0 until image.height).map { y -> image.getRGB(x, y) } }
        val orangeArgb = Color.ORANGE.rgb or (0xFF shl 24)
        val blueArgb = Color.BLUE.rgb or (0xFF shl 24)
        assertTrue(pixels.contains(orangeArgb), "verwacht oranje pixels voor de ochtend-pijl")
        assertTrue(pixels.contains(blueArgb), "verwacht blauwe pixels voor de middag-pijl")
    }

    @Test
    fun `weatherCategory geeft zon terug bij helder weer`() {
        assertEquals("zon", weatherCategory(0))
        assertEquals("zon", weatherCategory(1))
    }

    @Test
    fun `weatherCategory geeft onweerswolk terug bij onweer`() {
        assertEquals("onweerswolk", weatherCategory(95))
        assertEquals("onweerswolk", weatherCategory(99))
    }

    @Test
    fun `weatherCategory geeft regenwolk terug bij regen`() {
        assertEquals("regenwolk", weatherCategory(61))
        assertEquals("regenwolk", weatherCategory(80))
    }

    @Test
    fun `weatherCategory geeft wolk terug als terugvaloptie`() {
        assertEquals("wolk", weatherCategory(3))
    }

    @Test
    fun `StubCoastMapImageBuilder levert geldige PNG-bytes zonder netwerk-call`() {
        val bytes = StubCoastMapImageBuilder().build(slots())

        assertTrue(bytes.isNotEmpty())
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }
}
