package br.com.estapar.parking

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.TimeZone

@MicronautTest
class EstaparParkingTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupTimezone() {
            configureUtcTimeZone()
        }
    }

    @Inject
    lateinit var application: EmbeddedApplication<*>

    @Test
    fun `should start micronaut context with UTC timezone`() {
        assertTrue(application.isRunning)
        assertEquals("UTC", TimeZone.getDefault().id)
    }
}
