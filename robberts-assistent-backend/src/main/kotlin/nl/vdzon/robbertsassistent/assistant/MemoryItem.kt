package nl.vdzon.robbertsassistent.assistant

import java.time.Instant

/**
 * Eén los geheugen-item: vrije tekst met een feit/voorkeur/context over Robbert, gebruiker-breed
 * (niet per-conversatie). Zie [MemoryRepository] en `AssistantService.updateMemoryFromExchange`.
 */
data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
