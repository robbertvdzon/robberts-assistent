package nl.vdzon.robbertsassistent.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.storage.Bucket
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import com.google.firebase.messaging.FirebaseMessaging
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Gedeelde toegang tot Firebase (Firestore + Cloud Storage). Initialiseert de [FirebaseApp] lui
 * (pas bij het eerste gebruik, dus niets bij startup als Firebase niet geconfigureerd is) en
 * levert een [Firestore] op de named database + de Storage-[Bucket]. Gedeeld door de reminders- en
 * gardenchat-modules, zodat er één FirebaseApp-instantie is.
 */
@Component
class FirebaseProvider(private val secrets: AppSecrets) {

    val isConfigured: Boolean get() = secrets.firebaseConfigured

    private val app: FirebaseApp by lazy { initApp() }

    /** Firestore op de geconfigureerde named database (of de default als er geen id gezet is). */
    fun firestore(): Firestore {
        val databaseId = secrets.firebaseDatabaseId
        return if (databaseId.isNullOrBlank()) {
            FirestoreClient.getFirestore(app)
        } else {
            FirestoreClient.getFirestore(app, databaseId)
        }
    }

    /** De Cloud Storage-bucket voor o.a. de moestuin-foto's. */
    fun bucket(): Bucket = StorageClient.getInstance(app).bucket(secrets.firebaseStorageBucket)

    /** FCM voor push-notificaties naar de app. */
    fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance(app)

    private fun initApp(): FirebaseApp {
        if (FirebaseApp.getApps().any { it.name == APP_NAME }) return FirebaseApp.getInstance(APP_NAME)
        val builder = FirebaseOptions.builder()
            .setCredentials(credentials())
            .setProjectId(secrets.firebaseProjectId)
        secrets.firebaseStorageBucket?.takeIf { it.isNotBlank() }?.let { builder.setStorageBucket(it) }
        return FirebaseApp.initializeApp(builder.build(), APP_NAME)
    }

    private fun credentials(): GoogleCredentials {
        secrets.firebaseCredentialsJson?.takeIf { it.isNotBlank() }?.let {
            return ByteArrayInputStream(it.toByteArray()).use { stream -> GoogleCredentials.fromStream(stream) }
        }
        secrets.firebaseCredentialsFile?.takeIf { it.isNotBlank() }?.let {
            return FileInputStream(it).use { stream -> GoogleCredentials.fromStream(stream) }
        }
        error("Firebase-credentials ontbreken (RA_FIREBASE_CREDENTIALS_JSON of _FILE)")
    }

    private companion object {
        const val APP_NAME = "robberts-assistent"
    }
}
