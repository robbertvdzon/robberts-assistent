package nl.vdzon.robbertsassistent.softwarefactory

/**
 * Vaste, deterministische stories/actiepunten — puur voor tests, zodat `SoftwareFactoryTools`
 * zonder netwerk-call getest kan worden (zelfde patroon als `StubCalendarClient`).
 * [SoftwareFactoryClientConfig] kiest deze zodra `RA_SOFTWAREFACTORY_CLIENT_SECRET`/
 * `_REFRESH_TOKEN` ontbreken.
 */
class StubSoftwareFactoryClient : SoftwareFactoryClient {
    override fun stories(): FactoryStoriesResult = FactoryStoriesResult(
        listOf(
            FactoryStory(
                key = "SF-1",
                summary = "Voorbeeldstory (stub)",
                phase = "developing",
                paused = false,
                error = null,
                merged = false,
            ),
            FactoryStory(
                key = "SF-2",
                summary = "Afgeronde voorbeeldstory (stub)",
                phase = "done",
                paused = false,
                error = null,
                merged = true,
            ),
        ),
    )

    override fun myActions(): FactoryMyActionsResult = FactoryMyActionsResult(
        listOf(FactoryActionItem(storyKey = "SF-1", storySummary = "Voorbeeldstory (stub)", question = "Mag ik dit zo mergen?")),
    )
}
