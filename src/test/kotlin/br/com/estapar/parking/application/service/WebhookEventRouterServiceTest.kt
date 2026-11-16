package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class WebhookEventRouterServiceTest {

    private val router = WebhookEventRouterService()

    @Test
    fun `should route ENTRY, PARKED and EXIT commands cleanly`() {
        val commands = listOf(
            WebhookEventCommand(
                eventType = WebhookEventType.ENTRY,
                licensePlate = "ZUL0001",
                entryTime = Instant.parse("2025-01-01T12:00:00.000Z"),
            ),
            WebhookEventCommand(
                eventType = WebhookEventType.PARKED,
                licensePlate = "ZUL0001",
                lat = BigDecimal("-23.561684"),
                lng = BigDecimal("-46.655981"),
            ),
            WebhookEventCommand(
                eventType = WebhookEventType.EXIT,
                licensePlate = "ZUL0001",
                exitTime = Instant.parse("2025-01-01T13:00:00.000Z"),
            ),
        )

        commands.forEach { command ->
            assertDoesNotThrow { router.process(command) }
        }
    }
}
