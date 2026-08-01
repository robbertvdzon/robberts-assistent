package nl.vdzon.robbertsassistent.watches

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.WriteResult
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifieert dat het inmiddels vervallen `frequency`-veld niet meer wordt weggeschreven en dat
 * bestaande documenten mét of zónder dat veld gewoon inlezen (geen migratie nodig).
 */
class FirestoreWatchRepositoryTest {
    private val collection = mock(CollectionReference::class.java)
    private val document = mock(DocumentReference::class.java)
    private val firestore = mock(Firestore::class.java).also {
        `when`(it.collection("watches")).thenReturn(collection)
    }
    private val repository = FirestoreWatchRepository(firestore)

    private fun <T> future(value: T): ApiFuture<T> {
        @Suppress("UNCHECKED_CAST")
        val f = mock(ApiFuture::class.java) as ApiFuture<T>
        `when`(f.get()).thenReturn(value)
        return f
    }

    private fun snapshot(withOldFrequency: Boolean): DocumentSnapshot {
        val snapshot = mock(DocumentSnapshot::class.java)
        `when`(snapshot.id).thenReturn("watch-1")
        `when`(snapshot.getString("title")).thenReturn("Kaarten")
        `when`(snapshot.getString("url")).thenReturn("https://example.com")
        `when`(snapshot.getString("instruction")).thenReturn("zoek twee kaarten")
        `when`(snapshot.getString("frequency")).thenReturn(if (withOldFrequency) "KANTOORUREN" else null)
        `when`(snapshot.getString("status")).thenReturn("NIET_GEVONDEN")
        `when`(snapshot.getString("statusDescription")).thenReturn("Nog niet gevonden.")
        `when`(snapshot.getBoolean("notifyOnFound")).thenReturn(true)
        `when`(snapshot.getBoolean("active")).thenReturn(true)
        `when`(snapshot.getLong("lastCheckedAtEpochMillis")).thenReturn(null)
        return snapshot
    }

    private fun stubFindById(snapshot: DocumentSnapshot) {
        // Mocks bewust vooraf opbouwen: een mock aanmaken/stubben binnen een lopende
        // `when(...)`-aanroep laat Mockito met UnfinishedStubbing afbreken.
        val snapshotFuture = future(snapshot)
        `when`(collection.document("watch-1")).thenReturn(document)
        `when`(document.get()).thenReturn(snapshotFuture)
    }

    @Test
    fun `een bestaand document met een oud frequency-veld leest gewoon in`() {
        stubFindById(snapshot(withOldFrequency = true))

        val watch = repository.findById("watch-1")

        assertNotNull(watch)
        assertEquals("watch-1", watch.id)
        assertEquals("Kaarten", watch.title)
        assertEquals("https://example.com", watch.url)
        assertEquals("zoek twee kaarten", watch.instruction)
        assertEquals(WatchStatus.NIET_GEVONDEN, watch.status)
        assertTrue(watch.notifyOnFound)
        assertTrue(watch.active)
    }

    @Test
    fun `een document zonder frequency-veld leest ook gewoon in`() {
        stubFindById(snapshot(withOldFrequency = false))

        assertNotNull(repository.findById("watch-1"))
    }

    @Test
    fun `opslaan schrijft geen frequency meer weg`() {
        val writeFuture = future(mock(WriteResult::class.java))
        `when`(collection.document("watch-1")).thenReturn(document)
        var written: Map<*, *>? = null
        `when`(document.set(anyMap())).thenAnswer {
            written = it.getArgument<Map<*, *>>(0)
            writeFuture
        }

        repository.save(
            Watch("watch-1", "Kaarten", "https://example.com", "zoek twee kaarten", true),
        )

        val map = assertNotNull(written)
        assertFalse(map.containsKey("frequency"))
        assertEquals("Kaarten", map["title"])
        assertEquals(true, map["notifyOnFound"])
    }
}
