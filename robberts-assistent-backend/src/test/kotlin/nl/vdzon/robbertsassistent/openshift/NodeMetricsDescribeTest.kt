package nl.vdzon.robbertsassistent.openshift

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeMetricsDescribeTest {

    @Test
    fun `beschrijft alle drie secties met gebruik en percentage`() {
        val metrics = NodeMetrics(
            memory = MemoryUsage(totalMb = 16000, usedMb = 8000, availableMb = 8000, usedPercent = 50.0),
            ssd = DiskUsage(totalGb = 240.0, usedGb = 120.0, freeGb = 120.0, usedPercent = 50.0),
            externalHdd = DiskUsage(totalGb = 4000.0, usedGb = 1000.0, freeGb = 3000.0, usedPercent = 25.0),
        )

        val result = metrics.describe()

        assertEquals("geheugen: 8000/16000 MB (50.0%), SSD: 120.0/240.0 GB (50.0%), externe HDD: 1000.0/4000.0 GB (25.0%)", result)
    }

    @Test
    fun `toont een foutmelding voor een mislukte sectie zonder de andere te raken`() {
        val metrics = NodeMetrics(
            memory = MemoryUsage(totalMb = 16000, usedMb = 8000, availableMb = 8000, usedPercent = 50.0),
            externalHdd = DiskUsage(error = "geen mount gevonden"),
        )

        val result = metrics.describe()

        assertEquals("geheugen: 8000/16000 MB (50.0%), externe HDD: onbekend (geen mount gevonden)", result)
    }

    @Test
    fun `laat een ontbrekende sectie gewoon weg`() {
        val metrics = NodeMetrics(memory = MemoryUsage(totalMb = 16000, usedMb = 8000, availableMb = 8000, usedPercent = 50.0))

        val result = metrics.describe()

        assertEquals("geheugen: 8000/16000 MB (50.0%)", result)
    }
}
