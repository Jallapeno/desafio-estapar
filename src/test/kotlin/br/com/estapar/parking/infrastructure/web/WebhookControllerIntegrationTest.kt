package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import io.micronaut.context.annotation.Replaces
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

@MicronautTest(transactional = false)
class WebhookControllerIntegrationTest {

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    private val recordedCommands = mutableListOf<WebhookEventCommand>()

    @MockBean(ProcessWebhookEventUseCase::class)
    @Replaces(ProcessWebhookEventUseCase::class)
    fun mockProcessWebhookEventUseCase(): ProcessWebhookEventUseCase =
        object : ProcessWebhookEventUseCase {
            override fun process(command: WebhookEventCommand) {
                recordedCommands.add(command)
            }
        }

    @BeforeEach
    fun reset() {
        recordedCommands.clear()
    }

    @Test
    fun `should deserialize ENTRY, PARKED and EXIT payloads in snake_case with millisecond precision`() {
        val entryPayload = """
            {
              "license_plate": "ZUL0001",
              "entry_time": "2025-01-01T12:00:00.123Z",
              "event_type": "ENTRY"
            }
        """.trimIndent()
        val parkedPayload = """
            {
              "license_plate": "ZUL0001",
              "lat": -23.561684,
              "lng": -46.655981,
              "event_type": "PARKED"
            }
        """.trimIndent()
        val exitPayload = """
            {
              "license_plate": "ZUL0001",
              "exit_time": "2025-01-01T13:15:00.456Z",
              "event_type": "EXIT"
            }
        """.trimIndent()

        listOf(entryPayload, parkedPayload, exitPayload).forEach { json ->
            val request = HttpRequest.POST("/webhook", json).contentType(MediaType.APPLICATION_JSON_TYPE)
            val response = httpClient.toBlocking().exchange(request, Void::class.java)
            assertEquals(HttpStatus.OK, response.status)
        }

        assertEquals(3, recordedCommands.size)
        assertEquals(WebhookEventType.ENTRY, recordedCommands[0].eventType)
        assertEquals("ZUL0001", recordedCommands[0].licensePlate)
        assertEquals(Instant.parse("2025-01-01T12:00:00.123Z"), recordedCommands[0].entryTime)

        assertEquals(WebhookEventType.PARKED, recordedCommands[1].eventType)
        assertEquals(BigDecimal("-23.561684"), recordedCommands[1].lat)
        assertEquals(BigDecimal("-46.655981"), recordedCommands[1].lng)

        assertEquals(WebhookEventType.EXIT, recordedCommands[2].eventType)
        assertEquals(Instant.parse("2025-01-01T13:15:00.456Z"), recordedCommands[2].exitTime)
    }

    @Test
    fun `should return 400 Bad Request for blank license_plate, unknown event_type or missing event fields`() {
        val invalidPayloads = listOf(
            """{"license_plate": "   ", "entry_time": "2025-01-01T12:00:00.000Z", "event_type": "ENTRY"}""",
            """{"license_plate": "ZUL0001", "event_type": "INVALID_TYPE"}""",
            """{"license_plate": "ZUL0001", "event_type": "ENTRY"}""",
            """{"license_plate": "ZUL0001", "lat": -23.561684, "event_type": "PARKED"}""",
            """{"license_plate": "ZUL0001", "event_type": "EXIT"}""",
        )

        invalidPayloads.forEach { json ->
            val request = HttpRequest.POST("/webhook", json).contentType(MediaType.APPLICATION_JSON_TYPE)
            val exception = assertThrows(HttpClientResponseException::class.java) {
                httpClient.toBlocking().exchange(request, String::class.java)
            }
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        assertEquals(0, recordedCommands.size)
    }
}
