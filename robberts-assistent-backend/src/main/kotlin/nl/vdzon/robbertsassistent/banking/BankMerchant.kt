package nl.vdzon.robbertsassistent.banking

private val cardPrefix = Regex("(?i)^BEA,\\s*(?:Google Pay|Apple Pay|Betaalpas)\\s+")
private val cardReference = Regex("(?i)(?:,\\s*PAS\\d+\\s+|\\s+)NR:\\s*[A-Z0-9]+(?:\\s*,\\s*|\\s+)\\d{2}\\.\\d{2}\\.\\d{2}/\\d{2}[:.]\\d{2}")
private val foreignCardReference = Regex("(?i)\\s+\\d{2}\\.\\d{2}\\.\\d{2}/\\d{2}:\\d{2}\\s+[^,\\r\\n]+,\\s*LAND:\\s*[A-Z]{3}\\s+KAARTNUMMER:")

/** ABN card bookings may omit the structured payee. Only read its bank-generated card header. */
fun bankCardMerchant(description: String): String {
    val prefix = cardPrefix.find(description) ?: return ""
    val body = description.substring(prefix.range.last + 1)
    val reference = cardReference.find(body) ?: foreignCardReference.find(body) ?: return ""
    val merchant = body.substring(0, reference.range.first).trim().replace(Regex("\\s+"), " ")
    // Keep processor prefixes and truncated names exactly as supplied; never guess the full name.
    return merchant.takeIf { it.length in 1..120 }.orEmpty()
}
