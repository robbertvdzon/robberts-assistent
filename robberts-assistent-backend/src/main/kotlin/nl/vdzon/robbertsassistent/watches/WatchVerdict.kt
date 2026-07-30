package nl.vdzon.robbertsassistent.watches

/** Geparste AI-beoordeling van een watch-check (zie [WatchScheduler]/`WatchAiConfig`). */
data class WatchVerdict(val status: WatchStatus, val statusText: String) {
    companion object {
        /**
         * Defensieve parse van het AI-antwoord: regel 1 = `GEVONDEN`/`NIET GEVONDEN`, regel 2 = een
         * korte statuszin. Bij een onverwacht of leeg antwoord (bv. `MockChatModel` onder
         * `RA_MOCK_AI`) valt de status terug op [WatchStatus.ONBEKEND] zonder te crashen.
         */
        fun parse(response: String): WatchVerdict {
            val lines = response.trim().lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) return WatchVerdict(WatchStatus.ONBEKEND, "")
            val first = lines.first().uppercase()
            val statusText = lines.getOrNull(1) ?: ""
            val status = when {
                first.contains("NIET GEVONDEN") -> WatchStatus.NIET_GEVONDEN
                first.contains("GEVONDEN") -> WatchStatus.GEVONDEN
                else -> WatchStatus.ONBEKEND
            }
            return WatchVerdict(status, statusText)
        }
    }
}
