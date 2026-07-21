package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.tides.TideExtreme
import nl.vdzon.robbertsassistent.tides.TideType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Eén dagdeel zijn wind-/weerdata voor de weerkaart-overlay: [directionDeg] is de richting waar de
 * wind VANDAAN komt (0-360, meteorologische conventie), [color] onderscheidt het dagdeel visueel
 * (bv. oranje = ochtend, blauw = avond) en [weatherCode] is de Open-Meteo WMO-code voor het
 * getekende weer-icoon (zie `drawWeatherIcon`).
 */
data class WindMapSlot(
    val label: String,
    val color: Color,
    val speedKn: Double,
    val directionDeg: Double,
    val weatherCode: Int,
)

/**
 * Bouwt één kaartbeeld van de kust IJmuiden-Egmond met daarover, per opgegeven dagdeel, een
 * windrichtingspijl (in de eigen [WindMapSlot.color], verticaal gestapeld aan de linkerkant),
 * windsnelheid (kn) en een écht getekend weer-icoon, plus een legenda die de kleur-dagdeel-
 * koppeling toont, en onderin een dag-brede weersymbool + de hoog-/laagwatertijden — voor
 * [WeatherMapSectionProvider]. [OsmCoastMapImageBuilder] is de altijd-actieve, keyless
 * implementatie (OpenStreetMap-tegels, geen betaalde kaarten-API — zelfde "Fase 0"-stijl als
 * `weather.OpenMeteoWindForecastClient`); [StubCoastMapImageBuilder] bestaat alleen voor tests
 * (geen netwerk-call).
 */
interface CoastMapImageBuilder {
    fun build(slots: List<WindMapSlot>, dayWeatherCode: Int, tideExtremes: List<TideExtreme>): ByteArray
}

@Component
class OsmCoastMapImageBuilder : CoastMapImageBuilder {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    override fun build(slots: List<WindMapSlot>, dayWeatherCode: Int, tideExtremes: List<TideExtreme>): ByteArray {
        val map = fetchMap()
        drawOverlay(map, slots, dayWeatherCode, tideExtremes)
        val out = ByteArrayOutputStream()
        ImageIO.write(map, "png", out)
        return out.toByteArray()
    }

    private fun fetchMap(): BufferedImage {
        val minX = lonToTileX(MIN_LON, ZOOM)
        val maxX = lonToTileX(MAX_LON, ZOOM)
        val minY = latToTileY(MAX_LAT, ZOOM)
        val maxY = latToTileY(MIN_LAT, ZOOM)
        val tilesWide = maxX - minX + 1
        val tilesHigh = maxY - minY + 1
        val canvas = BufferedImage(tilesWide * TILE_SIZE, tilesHigh * TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val tile = fetchTile(x, y) ?: continue
                g.drawImage(tile, (x - minX) * TILE_SIZE, (y - minY) * TILE_SIZE, null)
            }
        }
        g.dispose()
        return canvas
    }

    private fun fetchTile(x: Int, y: Int): BufferedImage? = runCatching {
        val request = HttpRequest.newBuilder(URI.create("https://tile.openstreetmap.org/$ZOOM/$x/$y.png"))
            .header("User-Agent", USER_AGENT)
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) return null
        ImageIO.read(response.body().inputStream())
    }.onFailure { logger.warn("OSM-tegel ophalen faalde ({}, {}): {}", x, y, it.message) }.getOrNull()

    private companion object {
        const val ZOOM = 10
        const val TILE_SIZE = 256

        // Kust IJmuiden t/m Egmond aan Zee, met wat marge.
        const val MIN_LAT = 52.44
        const val MAX_LAT = 52.63
        const val MIN_LON = 4.53
        const val MAX_LON = 4.68
        const val USER_AGENT = "robberts-assistent/1.0 (+https://robberts-assistent.vdzonsoftware.nl)"

        fun lonToTileX(lon: Double, zoom: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

        fun latToTileY(lat: Double, zoom: Int): Int {
            val latRad = Math.toRadians(lat)
            return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
        }
    }
}

/**
 * Tekent, per opgegeven dagdeel, een windpijl (in de eigen kleur) met windsnelheidslabel en een
 * echt getekend weer-icoon over een reeds opgebouwd kaartbeeld, plus een legenda die de
 * kleur-dagdeel-koppeling toont — los testbaar zonder netwerk. De pijlen worden verticaal
 * gestapeld aan de linkerkant van de kaart geplaatst (één per dagdeel) zodat ze bij twee dagdelen
 * niet overlappen en de rest van de kaart vrij blijft. Onderin komt een dag-breed weersymbool +
 * de hoog-/laagwatertijden te staan (zie [drawDaySummary]).
 */
internal fun drawOverlay(image: BufferedImage, slots: List<WindMapSlot>, dayWeatherCode: Int, tideExtremes: List<TideExtreme>) {
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val arrowX = image.width * 0.15
    val topMargin = image.height * 0.15
    val bottomMargin = image.height * 0.75
    val spacing = (bottomMargin - topMargin) / (slots.size + 1.0)
    val halfLength = 30.0
    val headSize = 14

    slots.forEachIndexed { index, slot ->
        val arrowY = topMargin + spacing * (index + 1)

        // De pijl wijst de richting op waar de wind NAARTOE waait (tegenovergesteld aan
        // `directionDeg`, dat de richting is waar de wind vandaan komt).
        val transform = AffineTransform()
        transform.translate(arrowX, arrowY)
        transform.rotate(Math.toRadians(slot.directionDeg + 180.0))
        g.transform = transform
        g.color = slot.color
        g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine(0, halfLength.toInt(), 0, -halfLength.toInt())
        g.fillPolygon(
            intArrayOf(0, -headSize, headSize),
            intArrayOf(-halfLength.toInt() - headSize, -halfLength.toInt(), -halfLength.toInt()),
            3,
        )
        g.transform = AffineTransform()

        g.color = Color.WHITE
        g.fillRoundRect((arrowX - 55).toInt(), (arrowY + 40).toInt(), 110, 30, 10, 10)
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.BOLD, 18)
        val label = "${slot.speedKn.toInt()} kn"
        val metrics = g.fontMetrics
        g.drawString(label, (arrowX - metrics.stringWidth(label) / 2.0).toInt(), (arrowY + 62).toInt())

        drawWeatherIcon(g, slot.weatherCode, arrowX, arrowY - halfLength - headSize - 24, 20.0)
    }

    drawLegend(g, slots)
    drawDaySummary(g, image.width, image.height, dayWeatherCode, tideExtremes)
    g.dispose()
}

/**
 * Tekent onderin de kaart een dag-breed (niet per-dagdeel) weersymbool — in dezelfde
 * `java.awt`-vormenstijl als [drawWeatherIcon] — plus de hoog-/laagwatertijden van die dag
 * (IJmuiden) als tekst, in een halfdoorzichtig kader zodat het leesbaar blijft op de kaart. Het
 * kader (en de erin getekende tekst) wordt begrensd op de kaartbreedte: bij te veel getijmomenten
 * om op één regel te passen wordt de tekst over meerdere regels verdeeld i.p.v. dat het kader
 * buiten het canvas uitsteekt.
 */
private fun drawDaySummary(g: java.awt.Graphics2D, width: Int, height: Int, dayWeatherCode: Int, tideExtremes: List<TideExtreme>) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    val entries = tideExtremes.sortedBy { it.time }.map { extreme ->
        val label = if (extreme.type == TideType.HOOGWATER) "Hoogwater" else "Laagwater"
        "$label ${formatter.format(extreme.time)}"
    }

    g.font = Font("SansSerif", Font.BOLD, 18)
    val metrics = g.fontMetrics
    val iconRadius = 20.0
    val margin = 16
    val maxBoxWidth = width - margin * 2
    val iconAreaWidth = (iconRadius * 2 + 24).toInt()
    val textRightPadding = 24
    val maxTextWidth = (maxBoxWidth - iconAreaWidth - textRightPadding).coerceAtLeast(60)

    val lines = wrapTideLines(entries, metrics, maxTextWidth)
    val lineHeight = metrics.height + 4
    val textBlockHeight = lines.size * lineHeight
    val boxHeight = maxOf((iconRadius * 2 + 16).toInt(), textBlockHeight + 16)
    val textWidth = lines.maxOf { metrics.stringWidth(it) }
    val boxWidth = (iconAreaWidth + textWidth + textRightPadding).coerceAtMost(maxBoxWidth)
    val boxX = ((width - boxWidth) / 2.0).toInt()
    val boxY = height - boxHeight - margin

    g.color = Color(255, 255, 255, 220)
    g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12)

    val iconCenterX = boxX + 16 + iconRadius
    val iconCenterY = boxY + boxHeight / 2.0
    drawWeatherIcon(g, dayWeatherCode, iconCenterX, iconCenterY, iconRadius)

    g.color = Color.BLACK
    val textStartX = (iconCenterX + iconRadius + 16).toInt()
    val blockTop = boxY + (boxHeight - textBlockHeight) / 2
    lines.forEachIndexed { index, line ->
        val textY = blockTop + index * lineHeight + metrics.ascent
        g.drawString(line, textStartX, textY)
    }
}

/**
 * Verdeelt de getijmomenten-teksten greedy over zo min mogelijk regels die elk binnen
 * [maxWidth] passen (een enkel te lang moment wordt niet verder afgebroken). Levert
 * `["Geen getijdata"]` bij een lege lijst.
 */
private fun wrapTideLines(entries: List<String>, metrics: java.awt.FontMetrics, maxWidth: Int): List<String> {
    if (entries.isEmpty()) return listOf("Geen getijdata")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (entry in entries) {
        val candidate = if (current.isEmpty()) entry else "$current   $entry"
        if (current.isEmpty() || metrics.stringWidth(candidate) <= maxWidth) {
            current = StringBuilder(candidate)
        } else {
            lines.add(current.toString())
            current = StringBuilder(entry)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

/**
 * Tekent één weer-icoon met java.awt-vormen (geen tekst/emoji, dus altijd zichtbaar op de server):
 * een cirkel voor zon, ellipsen voor een wolk, en daaronder regendruppellijntjes bij regen/onweer.
 * [centerX]/[centerY] is het midden van het icoon, [radius] de basisgrootte.
 */
private fun drawWeatherIcon(g: java.awt.Graphics2D, weatherCode: Int, centerX: Double, centerY: Double, radius: Double) {
    when {
        weatherCode == 0 || weatherCode == 1 -> {
            g.color = Color(0xFF, 0xA0, 0x00)
            g.fillOval((centerX - radius).toInt(), (centerY - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
        }
        weatherCode in setOf(95, 96, 99) || weatherCode in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) -> {
            drawCloud(g, centerX, centerY, radius)
            g.color = Color(0x15, 0x65, 0xC0)
            g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in -1..1) {
                val dropX = (centerX + i * radius * 0.7).toInt()
                g.drawLine(dropX, (centerY + radius * 0.6).toInt(), dropX - 3, (centerY + radius * 1.2).toInt())
            }
        }
        else -> drawCloud(g, centerX, centerY, radius)
    }
}

private fun drawCloud(g: java.awt.Graphics2D, centerX: Double, centerY: Double, radius: Double) {
    g.color = Color(0xC0, 0xC0, 0xC8)
    g.fillOval((centerX - radius).toInt(), (centerY - radius * 0.3).toInt(), (radius * 1.3).toInt(), (radius * 1.1).toInt())
    g.fillOval((centerX - radius * 0.3).toInt(), (centerY - radius * 0.8).toInt(), (radius * 1.4).toInt(), (radius * 1.2).toInt())
    g.fillOval((centerX + radius * 0.4).toInt(), (centerY - radius * 0.3).toInt(), (radius * 1.2).toInt(), (radius * 1.0).toInt())
}

/** Klein legendaatje linksboven dat de kleur van elke pijl aan het bijbehorende dagdeel-label koppelt. */
private fun drawLegend(g: java.awt.Graphics2D, slots: List<WindMapSlot>) {
    val padding = 8
    val lineHeight = 20
    val boxSize = 12
    g.font = Font("SansSerif", Font.PLAIN, 14)
    val metrics = g.fontMetrics
    val width = slots.maxOf { metrics.stringWidth(it.label) } + boxSize + padding * 3
    val height = slots.size * lineHeight + padding

    g.color = Color(255, 255, 255, 220)
    g.fillRoundRect(padding, padding, width, height, 8, 8)

    slots.forEachIndexed { index, slot ->
        val y = padding + index * lineHeight + padding / 2
        g.color = slot.color
        g.fillRect(padding * 2, y, boxSize, boxSize)
        g.color = Color.BLACK
        g.drawString(slot.label, padding * 2 + boxSize + padding, y + boxSize)
    }
}

/** Nederlandse categorieën: zon / (regen/onweers)wolk, op basis van de Open-Meteo WMO-code. */
internal fun weatherCategory(code: Int): String = when {
    code == 0 || code == 1 -> "zon"
    code in setOf(95, 96, 99) -> "onweerswolk"
    code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) -> "regenwolk"
    else -> "wolk"
}

class StubCoastMapImageBuilder : CoastMapImageBuilder {
    override fun build(slots: List<WindMapSlot>, dayWeatherCode: Int, tideExtremes: List<TideExtreme>): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
