package nl.vdzon.robbertsassistent.banking

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.ObjectMapper

@Component
class BankSettings(
    @Value("\${RA_BANKING_APP_ID:}") val appId: String,
    @Value("\${RA_BANKING_PRIVATE_KEY_FILE:}") val privateKeyFile: String,
    @Value("\${RA_BANKING_STORAGE_KEY:}") val storageKey: String,
    @Value("\${RA_BANKING_OWNER:}") val owner: String,
    @Value("\${RA_BANKING_REDIRECT_URL:https://robberts-assistent.vdzonsoftware.nl/api/v1/banking/callback}") val redirectUrl: String,
    @Value("\${RA_BANKING_LOGIN_EMAILS:}") val loginEmails: String = "",
) {
    val authorizedEmails get() = (loginEmails.split(",") + owner).map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val configured get() = listOf(appId, privateKeyFile, storageKey, owner).all { it.isNotBlank() }
}
@Configuration
class BankConfig {
    @Bean fun bankStore(settings: BankSettings, firebase: FirebaseProvider, mapper: ObjectMapper, secrets: AppSecrets): BankStore {
        if (!settings.configured) return MemoryBankStore(mapper)
        check(!secrets.previewSkipGoogleAuth) { "Echte banktoegang is verboden met preview-authenticatie" }
        check(firebase.isConfigured) { "Bankgegevens vereisen duurzame Firestore-opslag" }
        return FirestoreBankStore(firebase.firestore(), mapper, settings.storageKey)
    }
    @Bean fun bankingGateway(settings: BankSettings, mapper: ObjectMapper): BankingGateway =
        if (settings.configured) EnableBankingClient(settings, mapper) else DisabledBankingGateway()
}
