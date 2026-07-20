package nl.vdzon.robbertsassistent.nightlychecks

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import java.time.Instant

/**
 * Plant elke [NightlyCheck] in op zijn EIGEN cron-schema (in plaats van één gezamenlijke
 * ochtend-run) via Spring's dynamische [SchedulingConfigurer]-API — `@Scheduled(cron = "...")`
 * ondersteunt geen per-bean-instantie-lijst met elk een ander schema. Slaat elke uitvoering op
 * (nooit een exception omhoog — een crashende check mag de scheduler niet meenemen).
 */
@Configuration
class NightlyCheckScheduler(
    private val checks: List<NightlyCheck>,
    private val repository: NightlyCheckRepository,
) : SchedulingConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        checks.forEach { check ->
            taskRegistrar.addTriggerTask({ runAndStore(check) }, CronTrigger(check.cronSchedule))
        }
    }

    internal fun runAndStore(check: NightlyCheck) {
        val result = runCatching { check.run() }
            .getOrElse {
                logger.warn("Nightly check '{}' faalde", check.id, it)
                CheckResult(ok = false, summary = it.message ?: "Onbekende fout")
            }
        repository.save(CheckRun(check.id, Instant.now(), result))
    }
}
