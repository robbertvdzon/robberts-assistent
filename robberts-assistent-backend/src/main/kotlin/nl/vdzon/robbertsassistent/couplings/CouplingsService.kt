package nl.vdzon.robbertsassistent.couplings

import org.springframework.stereotype.Service

/**
 * Rapporteert de status van alle externe koppelingen en kan ze live testen — voedt het
 * "Koppelingen"-scherm in de app. Weet zelf niets van een specifieke koppeling: Spring injecteert
 * elke [CouplingProbe]-bean uit elke module hier automatisch in (zie die interface voor waarom).
 *
 * [statuses] leest alleen [CouplingProbe.configured]/[CouplingProbe.mode] (geen netwerk-calls).
 * [testAll] draait per koppeling [CouplingProbe.test] (parallel, niet-destructief), zodat je kunt
 * zien of een koppeling nog werkt.
 */
@Service
class CouplingsService(private val probes: List<CouplingProbe>) {

    /** Statuslijst zonder live-test (snel, geen netwerk). */
    fun statuses(): List<CouplingStatus> = probes.map { it.toStatus() }

    /** Statuslijst mét live-test per koppeling; tests draaien parallel. */
    fun testAll(): List<CouplingStatus> =
        probes.parallelStream().map { it.toStatus(withTest = true) }.toList()

    private fun CouplingProbe.toStatus(withTest: Boolean = false): CouplingStatus =
        CouplingStatus(
            id = id,
            name = name,
            description = description,
            configured = configured,
            mode = mode,
            test = if (withTest) timed { test() } else null,
        )

    private inline fun timed(block: () -> Pair<Boolean, String>): TestResult {
        val start = System.nanoTime()
        val (ok, detail) = try {
            block()
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        }
        return TestResult(ok, detail, (System.nanoTime() - start) / 1_000_000)
    }
}
