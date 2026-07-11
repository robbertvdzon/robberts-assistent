package nl.vdzon.robbertsassistent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RobbertsAssistentBackendApplication

fun main(args: Array<String>) {
    runApplication<RobbertsAssistentBackendApplication>(*args)
}
