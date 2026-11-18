package br.com.estapar.parking.infrastructure.web.dto

import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

@Serdeable
@Introspected
data class WebhookEventRequest(
    @field:NotBlank(message = "license_plate must not be blank")
    @field:Size(max = 20, message = "license_plate must be at most 20 characters")
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
        val plate = licensePlate?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
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
            WebhookEventType.ENTRY -> validateTimestamp(entryTime, "entry_time")
            WebhookEventType.PARKED -> {
                requireNotNull(lat) { "lat is required for PARKED events" }
                requireNotNull(lng) { "lng is required for PARKED events" }
            }
            WebhookEventType.EXIT -> validateTimestamp(exitTime, "exit_time")
        }
    }

    private fun validateTimestamp(value: Instant?, field: String) {
        requireNotNull(value) { "$field is required" }
        require(value.nano % 1_000_000 == 0) { "$field must have millisecond precision" }
    }

}
