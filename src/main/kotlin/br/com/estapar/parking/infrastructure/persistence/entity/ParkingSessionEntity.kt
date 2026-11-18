package br.com.estapar.parking.infrastructure.persistence.entity

import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.SessionStatus
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.math.BigDecimal
import java.time.Instant

@MappedEntity("parking_sessions")
data class ParkingSessionEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    @field:MappedProperty("license_plate")
    val licensePlate: String,
    val status: SessionStatus,
    @field:MappedProperty("entry_time")
    val entryTime: Instant,
    @field:MappedProperty("exit_time")
    val exitTime: Instant? = null,
    @field:MappedProperty("spot_id")
    val spotId: Long? = null,
    @field:MappedProperty("sector_code")
    val sectorCode: String? = null,
    @field:MappedProperty("occupancy_rate_at_entry")
    val occupancyRateAtEntry: BigDecimal,
    @field:MappedProperty("dynamic_multiplier")
    val dynamicMultiplier: BigDecimal,
    @field:MappedProperty("charged_amount")
    val chargedAmount: BigDecimal? = null,
) {
    fun toDomain(): ParkingSession = ParkingSession(
        id = id,
        licensePlate = licensePlate,
        status = status,
        entryTime = entryTime,
        exitTime = exitTime,
        spotId = spotId,
        sectorCode = sectorCode,
        occupancyRateAtEntry = occupancyRateAtEntry,
        dynamicMultiplier = dynamicMultiplier,
        chargedAmount = chargedAmount,
    )

    companion object {
        fun fromDomain(session: ParkingSession): ParkingSessionEntity = ParkingSessionEntity(
            id = session.id,
            licensePlate = session.licensePlate,
            status = session.status,
            entryTime = session.entryTime,
            exitTime = session.exitTime,
            spotId = session.spotId,
            sectorCode = session.sectorCode,
            occupancyRateAtEntry = session.occupancyRateAtEntry,
            dynamicMultiplier = session.dynamicMultiplier,
            chargedAmount = session.chargedAmount,
        )
    }
}
