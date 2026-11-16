package br.com.estapar.parking.infrastructure.web.dto

import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

@Serdeable
@Introspected
data class WebhookEventRequest(
    @field:NotBlank(message = "license_plate must not be blank")
    @field:JsonProperty("license_plate")
    val licensePlate: String? = null,
    @field:NotNull(message = "event_type is required")
    @field:JsonProperty("event_type")
    val eventType: WebhookEventType? = null,
    @field:JsonProperty("entry_time")
    val entryTime: Instant? = null,
    @field:JsonProperty("exit_time")
    val exitTime: Instant? = null,
    val lat: BigDecimal? = null,
    val lng: BigDecimal? = null,
) {
    fun toCommand(): WebhookEventCommand {
        val type = requireNotNull(eventType) { "event_type is required" }
        val plate = licensePlate?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("license_plate must not be blank")

        validateEventFields(type)

        return WebhookEventCommand(
            eventType = type,
            licensePlate = plate,
            entryTime = entryTime,
            exitTime = exitTime,
            lat = lat,
            lng = lng,
        )
    }

    private fun validateEventFields(type: WebhookEventType) {
        when (type) {
            WebhookEventType.ENTRY -> requireNotNull(entryTime) { "entry_time is required for ENTRY events" }
            WebhookEventType.PARKED -> {
                requireNotNull(lat) { "lat is required for PARKED events" }
                requireNotNull(lng) { "lng is required for PARKED events" }
            }
            WebhookEventType.EXIT -> requireNotNull(exitTime) { "exit_time is required for EXIT events" }
        }
    }
}
