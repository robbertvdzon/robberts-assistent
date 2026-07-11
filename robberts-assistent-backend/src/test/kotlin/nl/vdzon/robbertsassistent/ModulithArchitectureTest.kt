package nl.vdzon.robbertsassistent

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModulithArchitectureTest {
    @Test
    fun `application modules are valid`() {
        ApplicationModules.of(RobbertsAssistentBackendApplication::class.java).verify()
    }
}
