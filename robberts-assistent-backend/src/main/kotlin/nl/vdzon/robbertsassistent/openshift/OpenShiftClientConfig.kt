package nl.vdzon.robbertsassistent.openshift

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de OpenShift-client: de echte [KubernetesApiOpenShiftClient] zodra
 * `RA_OPENSHIFT_HEALTH_ENABLED=true` staat, anders de stub. In tegenstelling tot de andere
 * koppelingen is dit geen secret-aanwezigheid maar een expliciete schakelaar — de in-cluster
 * ServiceAccount-token bestaat altijd (elke pod krijgt er automatisch een), maar de RBAC om er
 * iets nuttigs mee te bevragen nog niet (zie `docs/nightly-checks.md`).
 */
@Configuration
class OpenShiftClientConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun openShiftClient(secrets: AppSecrets): OpenShiftClient =
        if (secrets.openShiftHealthEnabled) {
            logger.info("OpenShift-health: echte client actief")
            KubernetesApiOpenShiftClient()
        } else {
            logger.info("OpenShift-health: stub (RA_OPENSHIFT_HEALTH_ENABLED niet aan)")
            StubOpenShiftClient()
        }
}
