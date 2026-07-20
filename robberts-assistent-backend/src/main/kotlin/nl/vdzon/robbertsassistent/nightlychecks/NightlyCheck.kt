package nl.vdzon.robbertsassistent.nightlychecks

import java.time.Instant

/**
 * SPI voor precies één nachtelijke check (bv. "is de OpenShift-cluster gezond", en later "moet de
 * tuin water hebben", "kan ik morgen kiten", "doen de zonnepanelen het nog"). Elke module die zo'n
 * check aanbiedt, registreert een `@Component` die dit implementeert; [NightlyCheckScheduler] pikt
 * alle implementaties automatisch op via Spring's `List<NightlyCheck>`-injectie — zelfde SPI-patroon
 * als `couplings.CouplingProbe`. Een nieuwe check toevoegen betekent dus alleen een nieuwe
 * `NightlyCheck`-implementatie in de eigen module, geen wijziging in het framework.
 */
interface NightlyCheck {
    val id: String
    val name: String
    val description: String

    /**
     * Spring cron-expressie (`seconde minuut uur dag-van-maand maand dag-van-week`) — elke check
     * heeft zijn eigen schema in plaats van één gezamenlijke ochtend-run, want de ene check (bv.
     * OpenShift-gezondheid) is prima elk uur, terwijl een andere (bv. "moet de tuin water hebben")
     * maar één keer per ochtend hoeft.
     */
    val cronSchedule: String

    /** Voert de check uit. Mag een netwerk-call doen; gooit geen exception (zie [NightlyCheckScheduler]). */
    fun run(): CheckResult
}

/** Resultaat van één keer een check draaien. */
data class CheckResult(
    val ok: Boolean,
    val summary: String,
    val detail: String? = null,
)

/** Eén opgeslagen uitvoering van een check — voor de historie. */
data class CheckRun(
    val checkId: String,
    val ranAt: Instant,
    val result: CheckResult,
)
