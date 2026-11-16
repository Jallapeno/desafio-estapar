package br.com.estapar.parking.application.port.inbound

import java.math.BigDecimal
import java.time.Instant

/**
 * Webhook event types emitted by the garage simulator.
 */
enum class WebhookEventType {
    ENTRY,
    PARKED,
    EXIT,
}

/**
 * Normalized application command representing an incoming parking webhook event.
 */
data class WebhookEventCommand(
    val eventType: WebhookEventType,
    val licensePlate: String,
    val entryTime: Instant? = null,
    val exitTime: Instant? = null,
    val lat: BigDecimal? = null,
    val lng: BigDecimal? = null,
)

/**
 * Inbound use case port for routing and processing parking webhook events.
 */
interface ProcessWebhookEventUseCase {
    fun process(command: WebhookEventCommand)
}
