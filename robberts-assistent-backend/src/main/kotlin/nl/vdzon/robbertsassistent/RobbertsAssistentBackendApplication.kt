package nl.vdzon.robbertsassistent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class RobbertsAssistentBackendApplication

fun main(args: Array<String>) {
    runApplication<RobbertsAssistentBackendApplication>(*args)
}
