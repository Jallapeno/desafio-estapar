package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ParkedResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebhookEventRouterServiceTest {

    private val entries = mutableListOf<Pair<String, Instant>>()
    private val parked = mutableListOf<Triple<String, BigDecimal, BigDecimal>>()
    private val router = WebhookEventRouterService(ProcessEntryUseCase { plate, time ->
        entries.add(plate to time)
        EntryResult.ACCEPTED
    }, ProcessParkedUseCase { plate, lat, lng ->
        parked.add(Triple(plate, lat, lng))
        ParkedResult.PARKED
    })

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
        assertEquals(listOf("ZUL0001" to Instant.parse("2025-01-01T12:00:00.000Z")), entries)
        assertEquals(listOf(Triple("ZUL0001", BigDecimal("-23.561684"), BigDecimal("-46.655981"))), parked)
    }
}
