package br.com.estapar.parking.domain.model

import java.math.BigDecimal

/**
 * Physical parking spot geolocated within a garage sector.
 */
data class ParkingSpot(
    val id: Long,
    val sectorCode: String,
    val lat: BigDecimal,
    val lng: BigDecimal,
    val occupied: Boolean = false,
) {
    init {
        require(sectorCode.isNotBlank()) { "Parking spot sectorCode must not be blank" }
    }

    fun occupy(): ParkingSpot = copy(occupied = true)

    fun release(): ParkingSpot = copy(occupied = false)
}
