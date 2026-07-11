package nl.vdzon.robbertsassistent.auth

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthConfig {
    @Bean
    fun googleIdTokenVerifier(secrets: AppSecrets): GoogleIdTokenVerifier =
        NimbusGoogleIdTokenVerifier(secrets.googleClientId)
}
