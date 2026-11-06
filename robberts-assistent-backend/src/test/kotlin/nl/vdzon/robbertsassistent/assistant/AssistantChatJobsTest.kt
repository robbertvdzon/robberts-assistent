package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test de achtergrond-uitvoering van chat-beurten: direct een job-id, daarna status en antwoord. */
class AssistantChatJobsTest {
    private var jobs: AssistantChatJobs? = null

    @AfterTest
    fun stop() {
        jobs?.shutdown()
    }

    private fun newJobs(chatModel: ChatModel): AssistantChatJobs {
        val client = ChatClient.builder(chatModel).build()
        val mock = ChatClient.builder(MockChatModel()).build()
        val secrets = AppSecrets(
            rememberSecret = "test-remember-secret",
            googleClientId = "test-client-id.apps.googleusercontent.com",
            allowedEmails = setOf("robbert@vdzon.com"),
            mockAi = true,
        )
        val service = AssistantService(client, mock, mock, secrets, InMemoryConversationRepository(), InMemoryPhotoStorage(), InMemoryMemoryRepository())
        return AssistantChatJobs(service).also { jobs = it }
    }

    private fun awaitDone(jobs: AssistantChatJobs, id: String): ChatJob {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val job = jobs.get(id)
            if (job != null && job.status != ChatJobStatus.RUNNING) return job
            Thread.sleep(20)
        }
        error("job $id is niet op tijd klaar")
    }

    @Test
    fun `submit geeft direct een job-id terug terwijl de beurt nog loopt`() {
        val release = CountDownLatch(1)
        val slow = object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse {
                release.await(10, TimeUnit.SECONDS)
                return MockChatModel().call(prompt)
            }
            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
        }
        val jobs = newJobs(slow)

        val id = jobs.submit(null, "hoi", emptyList(), voice = false)

        assertEquals(ChatJobStatus.RUNNING, jobs.get(id)?.status)
        assertNull(jobs.get(id)?.result)
        release.countDown()
        val done = awaitDone(jobs, id)
        assertEquals(ChatJobStatus.DONE, done.status)
        assertNotNull(done.result)
        assertTrue(done.result!!.reply.contains("hoi"))
        assertEquals(2, done.result!!.messages.size)
    }

    @Test
    fun `een falende beurt eindigt als FAILED met de foutmelding`() {
        val broken = object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse = throw IllegalStateException("runtime stuk")
            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(IllegalStateException("runtime stuk"))
        }
        val jobs = newJobs(broken)

        val done = awaitDone(jobs, jobs.submit(null, "hoi", emptyList(), voice = false))

        assertEquals(ChatJobStatus.FAILED, done.status)
        assertTrue(done.error!!.contains("runtime stuk"))
        assertNull(done.result)
    }

    @Test
    fun `een onbekend job-id geeft null`() {
        assertNull(newJobs(MockChatModel()).get("bestaat-niet"))
    }
}
