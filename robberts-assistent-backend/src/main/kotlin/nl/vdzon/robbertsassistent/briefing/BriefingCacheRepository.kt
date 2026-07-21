package nl.vdzon.robbertsassistent.briefing

import java.util.concurrent.atomic.AtomicReference

/**
 * Opslag-poort voor de gecachete 'Morgen'-briefing: precies één document (laatste stand, geen
 * historie), net als `assistant.MemoryRepository`. Fallback is [InMemoryBriefingCacheRepository];
 * met Firebase geconfigureerd kiest [BriefingStoreConfig] de [FirestoreBriefingCacheRepository].
 */
interface BriefingCacheRepository {
    /** De laatst gecachete briefing, of `null` als er nog nooit een cache-refresh is geweest. */
    fun current(): BriefingResponse?

    /** Overschrijft de cache met [response] (bevat zelf al de `updatedAt`-tijdstip). */
    fun store(response: BriefingResponse)
}

class InMemoryBriefingCacheRepository : BriefingCacheRepository {
    private val cached = AtomicReference<BriefingResponse?>(null)

    override fun current(): BriefingResponse? = cached.get()

    override fun store(response: BriefingResponse) {
        cached.set(response)
    }
}
