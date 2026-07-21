package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.tides.TideExtreme
import nl.vdzon.robbertsassistent.tides.TideType
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.time.Instant
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
        WindMapSlot(label = "Avond", color = Color.BLUE, speedKn = 18.0, directionDeg = 200.0, weatherCode = 61),
    )

    private fun tideExtremes() = listOf(
        TideExtreme(Instant.parse("2026-07-22T04:00:00Z"), 95, TideType.HOOGWATER),
        TideExtreme(Instant.parse("2026-07-22T10:00:00Z"), -70, TideType.LAAGWATER),
    )

    @Test
    fun `drawOverlay crasht niet en levert een beschrijfbare afbeelding op`() {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots(), dayWeatherCode = 0, tideExtremes = tideExtremes())

        assertEquals(400, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun `drawOverlay werkt ook met één dagdeel`() {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots().take(1), dayWeatherCode = 0, tideExtremes = emptyList())

        assertEquals(256, image.width)
    }

    @Test
    fun `drawOverlay werkt ook zonder getijdata`() {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots(), dayWeatherCode = 0, tideExtremes = emptyList())

        assertEquals(256, image.width)
    }

    @Test
    fun `drawOverlay tekent daadwerkelijk pixels voor beide dagdeel-kleuren aan de linkerkant`() {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)

        drawOverlay(image, slots(), dayWeatherCode = 0, tideExtremes = tideExtremes())

        val leftHalf = (0 until image.width / 2).flatMap { x -> (0 until image.height).map { y -> image.getRGB(x, y) } }
        val orangeArgb = Color.ORANGE.rgb or (0xFF shl 24)
        val blueArgb = Color.BLUE.rgb or (0xFF shl 24)
        assertTrue(leftHalf.contains(orangeArgb), "verwacht oranje pixels voor de ochtend-pijl aan de linkerkant")
        assertTrue(leftHalf.contains(blueArgb), "verwacht blauwe pixels voor de avond-pijl aan de linkerkant")
    }

    @Test
    fun `drawDaySummary kader blijft binnen de kaartbreedte bij veel getijmomenten`() {
        val image = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        val manyExtremes = listOf(
            TideExtreme(Instant.parse("2026-07-22T02:00:00Z"), 95, TideType.HOOGWATER),
            TideExtreme(Instant.parse("2026-07-22T08:00:00Z"), -70, TideType.LAAGWATER),
            TideExtreme(Instant.parse("2026-07-22T14:00:00Z"), 100, TideType.HOOGWATER),
            TideExtreme(Instant.parse("2026-07-22T20:00:00Z"), -65, TideType.LAAGWATER),
        )

        drawOverlay(image, slots(), dayWeatherCode = 0, tideExtremes = manyExtremes)

        val boxArgb = Color(255, 255, 255, 220).rgb
        val bottomRows = 0 until image.height
        val leftEdgePixels = bottomRows.map { y -> image.getRGB(0, y) }
        val rightEdgePixels = bottomRows.map { y -> image.getRGB(image.width - 1, y) }
        val middleBottomPixels = (150 until 362).flatMap { x ->
            (image.height - 90 until image.height).map { y -> image.getRGB(x, y) }
        }

        assertTrue(!leftEdgePixels.contains(boxArgb), "kader mag de linkerrand van het canvas niet raken")
        assertTrue(!rightEdgePixels.contains(boxArgb), "kader mag de rechterrand van het canvas niet raken")
        assertTrue(middleBottomPixels.contains(boxArgb), "kader moet ergens onderin het midden van het canvas getekend zijn")
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
        val bytes = StubCoastMapImageBuilder().build(slots(), dayWeatherCode = 0, tideExtremes = tideExtremes())

        assertTrue(bytes.isNotEmpty())
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }
}
