package nl.vdzon.wind

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke-tests op de gedeelde antwoord-teksten. Draaien via `./gradlew test`.
 */
class WindAnswersTest {

    @Test
    fun windSpeedAnswerIsNotBlankAndMentionsUnit() {
        assertTrue(WindAnswers.WIND_SPEED.isNotBlank())
        assertTrue(WindAnswers.WIND_SPEED.lowercase().contains("knopen"))
    }

    @Test
    fun forecastAnswerIsNotBlank() {
        assertTrue(WindAnswers.FORECAST.isNotBlank())
        assertTrue(WindAnswers.FORECAST.lowercase().contains("verwachting"))
    }

    @Test
    fun answersDiffer() {
        assertNotEquals(WindAnswers.WIND_SPEED, WindAnswers.FORECAST)
    }
}
