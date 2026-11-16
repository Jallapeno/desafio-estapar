package br.com.estapar.parking

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone

class RuntimeConfigTest {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `should set default JVM timezone to UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))

        configureUtcTimeZone()

        assertEquals("UTC", TimeZone.getDefault().id)
    }

    @Test
    fun `should configure default server port 3003 and UTC datasource in application yaml`() {
        val yaml = javaClass.classLoader.getResource("application.yml")!!.readText()

        assertTrue(yaml.contains("port: \${SERVER_PORT:3003}"))
        assertTrue(yaml.contains("connectionTimeZone=UTC"))
        assertTrue(yaml.contains("forceConnectionTimeZoneToSession=true"))
        assertTrue(yaml.contains("enabled: true"))
        assertFalse(Regex("(username|password):\\s*\\$\\{[^:}]+:[^}]+\\}").containsMatchIn(yaml))
    }
}
