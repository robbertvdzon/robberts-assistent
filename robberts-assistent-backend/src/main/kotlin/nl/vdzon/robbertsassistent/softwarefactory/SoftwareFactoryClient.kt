package nl.vdzon.robbertsassistent.softwarefactory

/** Eén story (feature/bugfix) uit de software factory. */
data class FactoryStory(
    val key: String,
    val summary: String,
    val phase: String?,
    val paused: Boolean,
    val error: String?,
    val merged: Boolean,
)

/**
 * Resultaat van een stories-ophaal-poging. Bij een netwerk-/auth-fout is [stories] leeg en
 * [error] gezet — de aanroeper (`SoftwareFactoryTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class FactoryStoriesResult(
    val stories: List<FactoryStory>,
    val error: String? = null,
)

/** Eén item dat op Robberts actie wacht (goedkeuring, open vraag), met de story waar het bij hoort. */
data class FactoryActionItem(
    val storyKey: String,
    val storySummary: String,
    val question: String?,
)

data class FactoryMyActionsResult(
    val items: List<FactoryActionItem>,
    val error: String? = null,
)

/**
 * Bridge naar de software-factory-dashboard (build-/deploy-status, stories, worklogs) — dezelfde
 * REST-API die de software-factory-frontend zelf gebruikt, cluster-intern bereikbaar (beide
 * draaien op dezelfde OpenShift-cluster). Auth verloopt via een Google ID-token (zelfde
 * OAuth-client als de app-login, `AppSecrets.googleClientId`) dat wordt ingewisseld voor een
 * sessie-token — analoog aan hoe [nl.vdzon.robbertsassistent.google.GoogleOAuthService] werkt
 * voor Agenda/Docs. Actief zodra `RA_SOFTWAREFACTORY_CLIENT_SECRET` + `_REFRESH_TOKEN` gezet zijn;
 * anders [StubSoftwareFactoryClient].
 */
interface SoftwareFactoryClient {
    fun stories(): FactoryStoriesResult
    fun myActions(): FactoryMyActionsResult
}
