package br.com.estapar.parking.infrastructure.web.dto

import io.micronaut.context.annotation.Secondary
import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.exceptions.SerdeException
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

// Applied only to webhook timestamps; other Instant properties keep the default deserializer.
@Singleton
@Secondary
class WebhookTimestampDeserializer : Deserializer<Instant> {
    override fun deserialize(decoder: Decoder, context: Deserializer.DecoderContext, type: Argument<in Instant>): Instant {
        val value = decoder.decodeString()
        return try {
            Instant.parse(value)
        } catch (ex: DateTimeParseException) {
            try {
                // The supplied garage-sim image emits UTC local datetimes without an offset.
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (invalid: DateTimeParseException) {
                throw SerdeException("Webhook timestamp must be an ISO date-time; absent offset means UTC", invalid)
            }
        }
    }
}
