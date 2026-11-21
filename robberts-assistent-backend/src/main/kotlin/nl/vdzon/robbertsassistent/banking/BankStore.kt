package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.firestore.Firestore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Iedere record heeft een eigenaar. Callback-state wordt atomair eenmaal opgebruikt. */
interface BankStore {
    fun <T> read(owner: String, key: String, type: Class<T>): T?
    fun write(owner: String, key: String, value: Any)
    fun keys(owner: String): List<String>
    fun consumeState(state: String): PendingBankAuth?
    fun <T> exclusive(owner: String, action: () -> T): T
}
class MemoryBankStore(private val mapper: ObjectMapper) : BankStore {
    private val records = ConcurrentHashMap<String, String>()
    override fun <T> exclusive(owner: String, action: () -> T): T = synchronized(records) { action() }
    override fun <T> read(owner: String, key: String, type: Class<T>): T? =
        records["${bankHash(owner)}/$key"]?.let { mapper.readValue(it, type) }
    override fun write(owner: String, key: String, value: Any) { records["${bankHash(owner)}/$key"] = mapper.writeValueAsString(value) }
    override fun keys(owner: String): List<String> = records.keys.filter { it.startsWith(bankHash(owner) + "/") }.map { it.substringAfter("/") }
    override fun consumeState(state: String): PendingBankAuth? = records.remove("${bankHash("pending")}/${bankHash(state)}")
        ?.let { mapper.readValue(it, PendingBankAuth::class.java) }
}
/** AES-GCM beschermt ook banktoegang; documentpad is authenticated associated data. */
class BankCipher(key: String) {
    private val secret = SecretKeySpec(Base64.getDecoder().decode(key).also { require(it.size == 32) }, "AES")
    fun encrypt(plain: String, context: String): String {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, nonce))
        cipher.updateAAD(context.toByteArray())
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain.toByteArray()))
    }
    fun decrypt(value: String, context: String): String {
        val bytes = Base64.getDecoder().decode(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(context.toByteArray())
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}
class FirestoreBankStore(private val db: Firestore, private val mapper: ObjectMapper, key: String) : BankStore {
    private val cipher = BankCipher(key)
    override fun <T> exclusive(owner: String, action: () -> T): T {
        val lease = db.collection("bankingLeases").document(bankHash(owner))
        val token = java.util.UUID.randomUUID().toString()
        db.runTransaction<Unit> { tx ->
            val until = tx.get(lease).get().getLong("until") ?: 0L
            if (until > System.currentTimeMillis()) throw BankBusyException()
            tx.set(lease, mapOf("token" to token, "until" to System.currentTimeMillis() + 30 * 60_000))
        }.get()
        try { return action() } finally {
            db.runTransaction<Unit> { tx ->
                if (tx.get(lease).get().getString("token") == token) tx.delete(lease)
            }.get()
        }
    }
    private fun document(owner: String, key: String) = db.collection("banking").document(bankHash(owner))
        .collection("records").document(key)
    override fun <T> read(owner: String, key: String, type: Class<T>): T? {
        val doc = document(owner, key)
        return doc.get().get().getString("encrypted")?.let { mapper.readValue(cipher.decrypt(it, doc.path), type) }
    }
    override fun write(owner: String, key: String, value: Any) {
        val doc = document(owner, key)
        val encrypted = cipher.encrypt(mapper.writeValueAsString(value), doc.path)
        require(encrypted.length < 950_000) { "Bankrecord te groot; synchronisatie afgebroken" }
        val fields = mutableMapOf<String, Any>("encrypted" to encrypted)
        if (value is PendingBankAuth) fields["expiresAt"] = com.google.cloud.Timestamp.ofTimeSecondsAndNanos(
            value.expiresAt.epochSecond, value.expiresAt.nano)
        doc.set(fields).get()
    }
    override fun keys(owner: String): List<String> = db.collection("banking").document(bankHash(owner))
        .collection("records").select(*emptyArray<String>()).get().get().documents.map { it.id }
    override fun consumeState(state: String): PendingBankAuth? {
        val doc = document("pending", bankHash(state))
        return db.runTransaction<PendingBankAuth?> { tx ->
            val encrypted = tx.get(doc).get().getString("encrypted") ?: return@runTransaction null
            val pending = mapper.readValue(cipher.decrypt(encrypted, doc.path), PendingBankAuth::class.java)
            tx.delete(doc)
            pending
        }.get()
    }
}

class BankBusyException : RuntimeException("Bankzaken worden al bijgewerkt. Probeer straks opnieuw.")

class BankNoAccountsException : RuntimeException()
