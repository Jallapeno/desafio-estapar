package br.com.estapar.parking.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * Represents a single vehicle stay lifecycle inside the parking garage.
 */
data class ParkingSession(
    val id: Long? = null,
    val licensePlate: String,
    val status: SessionStatus,
    val entryTime: Instant,
    val exitTime: Instant? = null,
    val spotId: Long? = null,
    val sectorCode: String? = null,
    val occupancyRateAtEntry: BigDecimal,
    val dynamicMultiplier: BigDecimal,
    val chargedAmount: BigDecimal? = null,
) {
    init {
        require(licensePlate.isNotBlank()) { "License plate must not be blank" }
        require(occupancyRateAtEntry >= BigDecimal.ZERO) { "Occupancy rate must not be negative" }
        require(dynamicMultiplier > BigDecimal.ZERO) { "Dynamic multiplier must be positive" }
    }

    fun park(spotId: Long, sectorCode: String): ParkingSession {
        require(status != SessionStatus.EXITED) { "Cannot park an already exited session" }
        require(sectorCode.isNotBlank()) { "Sector code must not be blank" }
        return copy(
            status = SessionStatus.PARKED,
            spotId = spotId,
            sectorCode = sectorCode,
        )
    }

    fun exit(exitTime: Instant, chargedAmount: BigDecimal, fallbackSectorCode: String? = null): ParkingSession {
        require(status != SessionStatus.EXITED) { "Session is already exited" }
        require(!exitTime.isBefore(entryTime)) { "Exit time cannot be before entry time" }
        require(chargedAmount >= BigDecimal.ZERO) { "Charged amount must not be negative" }
        return copy(
            status = SessionStatus.EXITED,
            exitTime = exitTime,
            sectorCode = sectorCode ?: fallbackSectorCode,
            chargedAmount = chargedAmount,
        )
    }
}
