package nl.vdzon.robbertsassistent.summary

data class SummaryItem(val key: String, val title: String, val text: String)
data class SummaryResponse(val items: List<SummaryItem>)
