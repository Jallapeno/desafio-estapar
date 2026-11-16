package br.com.estapar.parking.domain.port.outbound

import br.com.estapar.parking.domain.model.ParkingSpot
import java.math.BigDecimal

/**
 * Outbound port for managing physical parking spot persistence and spatial lookups.
 */
interface ParkingSpotRepositoryPort {
    fun upsertAll(spots: List<ParkingSpot>)
    fun findById(id: Long): ParkingSpot?
    fun findByCoordinates(lat: BigDecimal, lng: BigDecimal): ParkingSpot?
    fun findNearestAvailable(lat: BigDecimal, lng: BigDecimal): ParkingSpot?
    fun updateOccupied(spotId: Long, occupied: Boolean)
    fun countAll(): Long
}
