package nl.vdzon.robbertsassistent.briefing

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
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Bouwt een kaartbeeld van de kust IJmuiden-Egmond met een windrichtingspijl, windsnelheid (kn) en
 * weer-icoon erover, voor [WeatherMapSectionProvider]. [OsmCoastMapImageBuilder] is de altijd-
 * actieve, keyless implementatie (OpenStreetMap-tegels, geen betaalde kaarten-API — zelfde
 * "Fase 0"-stijl als `weather.OpenMeteoWindForecastClient`); [StubCoastMapImageBuilder] bestaat
 * alleen voor tests (geen netwerk-call).
 */
interface CoastMapImageBuilder {
    /** [directionDeg] is de richting waar de wind VANDAAN komt (0-360, meteorologische conventie). */
    fun build(speedKn: Double, directionDeg: Double, weatherCode: Int): ByteArray
}

@Component
class OsmCoastMapImageBuilder : CoastMapImageBuilder {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    override fun build(speedKn: Double, directionDeg: Double, weatherCode: Int): ByteArray {
        val map = fetchMap()
        drawOverlay(map, speedKn, directionDeg, weatherCode)
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

/** Tekent de windpijl/windsnelheid/weer-icoon over een reeds opgebouwd kaartbeeld — los testbaar zonder netwerk. */
internal fun drawOverlay(image: BufferedImage, speedKn: Double, directionDeg: Double, weatherCode: Int) {
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val centerX = image.width / 2.0
    val centerY = image.height / 2.0
    val halfLength = 30.0
    val headSize = 14

    // De pijl wijst de richting op waar de wind NAARTOE waait (tegenovergesteld aan `directionDeg`,
    // dat de richting is waar de wind vandaan komt).
    val transform = AffineTransform()
    transform.translate(centerX, centerY)
    transform.rotate(Math.toRadians(directionDeg + 180.0))
    g.transform = transform
    g.color = Color(0x15, 0x65, 0xC0)
    g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.drawLine(0, halfLength.toInt(), 0, -halfLength.toInt())
    g.fillPolygon(
        intArrayOf(0, -headSize, headSize),
        intArrayOf(-halfLength.toInt() - headSize, -halfLength.toInt(), -halfLength.toInt()),
        3,
    )
    g.transform = AffineTransform()

    g.color = Color.WHITE
    g.fillRoundRect((centerX - 55).toInt(), (centerY + 40).toInt(), 110, 30, 10, 10)
    g.color = Color.BLACK
    g.font = Font("SansSerif", Font.BOLD, 18)
    val label = "${speedKn.toInt()} kn"
    val metrics = g.fontMetrics
    g.drawString(label, (centerX - metrics.stringWidth(label) / 2.0).toInt(), (centerY + 62).toInt())

    g.font = Font("SansSerif", Font.PLAIN, 26)
    g.drawString(weatherIcon(weatherCode), (centerX - 14).toInt(), 34)
    g.dispose()
}

/** Nederlandse categorieën: zon / (regen/onweers)wolk, op basis van de Open-Meteo WMO-code. */
internal fun weatherIcon(code: Int): String = when {
    code == 0 || code == 1 -> "☀️" // zon
    code in setOf(95, 96, 99) -> "⛈️" // onweerswolk
    code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) -> "🌧️" // regenwolk
    else -> "☁️" // wolk
}

class StubCoastMapImageBuilder : CoastMapImageBuilder {
    override fun build(speedKn: Double, directionDeg: Double, weatherCode: Int): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
