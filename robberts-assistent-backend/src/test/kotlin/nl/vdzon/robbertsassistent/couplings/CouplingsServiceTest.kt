package nl.vdzon.robbertsassistent.couplings

import nl.vdzon.robbertsassistent.airquality.AirQualityCouplingProbe
import nl.vdzon.robbertsassistent.airquality.StubAirQualityClient
import nl.vdzon.robbertsassistent.assistant.ai.OpenAiCouplingProbe
import nl.vdzon.robbertsassistent.automower.AutomowerCouplingProbe
import nl.vdzon.robbertsassistent.automower.StubAutomowerClient
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.firebase.FirestoreCouplingProbe
import nl.vdzon.robbertsassistent.firebase.StorageCouplingProbe
import nl.vdzon.robbertsassistent.google.GoogleCouplingProbe
import nl.vdzon.robbertsassistent.google.StubCalendarClient
import nl.vdzon.robbertsassistent.news.NewsCouplingProbe
import nl.vdzon.robbertsassistent.news.StubNewsClient
import nl.vdzon.robbertsassistent.notifier.TelegramCouplingProbe
import nl.vdzon.robbertsassistent.openshift.OpenShiftCouplingProbe
import nl.vdzon.robbertsassistent.openshift.StubOpenShiftClient
import nl.vdzon.robbertsassistent.push.FcmCouplingProbe
import nl.vdzon.robbertsassistent.push.InMemoryFcmTokenStore
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryCouplingProbe
import nl.vdzon.robbertsassistent.softwarefactory.StubSoftwareFactoryClient
import nl.vdzon.robbertsassistent.strava.StravaCouplingProbe
import nl.vdzon.robbertsassistent.strava.StubStravaClient
import nl.vdzon.robbertsassistent.tides.StubTideClient
import nl.vdzon.robbertsassistent.tides.TideCouplingProbe
import nl.vdzon.robbertsassistent.waste.StubWasteClient
import nl.vdzon.robbertsassistent.waste.WasteCouplingProbe
import nl.vdzon.robbertsassistent.weather.StubWeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherCouplingProbe
import kotlin.test.Test
import kotlin.test.assertEquals

class CouplingsServiceTest {

    /** Zelfde probes als in productie, maar keyless clients zijn stubs waar dat scheelt. */
    private fun service(secrets: AppSecrets): CouplingsService {
        val firebase = FirebaseProvider(secrets)
        return CouplingsService(
            listOf(
                OpenAiCouplingProbe(secrets),
                TelegramCouplingProbe(secrets),
                FirestoreCouplingProbe(firebase),
                StorageCouplingProbe(secrets, firebase),
                GoogleCouplingProbe(secrets, StubCalendarClient()),
                FcmCouplingProbe(secrets, firebase, InMemoryFcmTokenStore()),
                AutomowerCouplingProbe(secrets, StubAutomowerClient()),
                StravaCouplingProbe(secrets, StubStravaClient()),
                SoftwareFactoryCouplingProbe(secrets, StubSoftwareFactoryClient()),
                OpenShiftCouplingProbe(secrets, StubOpenShiftClient()),
                WeatherCouplingProbe(StubWeatherClient()),
                TideCouplingProbe(StubTideClient()),
                AirQualityCouplingProbe(StubAirQualityClient()),
                NewsCouplingProbe(StubNewsClient()),
                WasteCouplingProbe(StubWasteClient()),
            ),
        )
    }

    private val bareSecrets = AppSecrets(
        rememberSecret = "s",
        googleClientId = "c",
        allowedEmails = setOf("robbert@vdzon.com"),
    )

    private val keylessIds = setOf("weather", "tides", "airquality", "news", "waste")

    @Test
    fun `zonder secrets staan de secret-koppelingen op fallback, de keyless koppelingen altijd op echt`() {
        val statuses = service(bareSecrets).statuses()
        val byId = statuses.associateBy { it.id }

        assertEquals(
            setOf(
                "openai", "telegram", "firestore", "storage", "google", "fcm", "automower", "strava",
                "softwarefactory", "openshift",
            ) + keylessIds,
            statuses.map { it.id }.toSet(),
        )
        val secretBacked = byId.filterKeys { it !in keylessIds }.values
        assertEquals(true, secretBacked.all { !it.configured }, "geen enkele secret-koppeling zou geconfigureerd moeten zijn")
        assertEquals(true, secretBacked.all { it.mode == "fallback" }, "secret-koppelingen zouden op fallback moeten staan")
        assertEquals(true, keylessIds.all { byId.getValue(it).configured && byId.getValue(it).mode == "echt" })
        assertEquals(true, statuses.all { it.test == null }, "de lijst-weergave doet geen live-test")
    }

    @Test
    fun `met secrets gaan koppelingen op echt`() {
        val configured = bareSecrets.copy(
            openAiApiKey = "sk-test",
            telegramBotToken = "bot",
            telegramChatId = "123",
            firebaseCredentialsJson = "{}",
            firebaseProjectId = "tuinbewatering",
            firebaseStorageBucket = "tuinbewatering.firebasestorage.app",
            googleOAuthClientId = "gid",
            googleOAuthClientSecret = "gsecret",
            googleOAuthRefreshToken = "grefresh",
            husqvarnaAppKey = "hkey",
            husqvarnaAppSecret = "hsecret",
            stravaClientId = "sid",
            stravaClientSecret = "ssecret",
            stravaRefreshToken = "srefresh",
            softwareFactoryGoogleClientSecret = "sfsecret",
            softwareFactoryGoogleRefreshToken = "sfrefresh",
            openShiftHealthEnabled = true,
        )

        val byId = service(configured).statuses().associateBy { it.id }

        assertEquals("echt", byId.getValue("openai").mode)
        assertEquals("echt", byId.getValue("telegram").mode)
        assertEquals("echt", byId.getValue("firestore").mode)
        assertEquals("echt", byId.getValue("storage").mode)
        assertEquals("echt", byId.getValue("google").mode)
        assertEquals("echt", byId.getValue("fcm").mode)
        assertEquals("echt", byId.getValue("automower").mode)
        assertEquals("echt", byId.getValue("strava").mode)
        assertEquals("echt", byId.getValue("softwarefactory").mode)
        assertEquals("echt", byId.getValue("openshift").mode)
        assertEquals(true, byId.values.all { it.configured })
    }

    @Test
    fun `testAll geeft ok terug voor de keyless koppelingen op basis van de stubs`() {
        val byId = service(bareSecrets).testAll().associateBy { it.id }

        keylessIds.forEach { id ->
            assertEquals(true, byId.getValue(id).test?.ok, "$id zou een geslaagde live-test moeten geven")
        }
    }
}
