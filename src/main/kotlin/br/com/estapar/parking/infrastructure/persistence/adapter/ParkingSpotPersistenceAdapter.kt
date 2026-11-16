package br.com.estapar.parking.infrastructure.persistence.adapter

import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import jakarta.inject.Singleton
import java.math.BigDecimal

@Singleton
open class ParkingSpotPersistenceAdapter(
    private val parkingSpotJdbcRepository: ParkingSpotJdbcRepository,
) : ParkingSpotRepositoryPort {

    override fun upsertAll(spots: List<ParkingSpot>) {
        spots.forEach { spot ->
            parkingSpotJdbcRepository.upsert(
                id = spot.id,
                sectorCode = spot.sectorCode,
                lat = spot.lat,
                lng = spot.lng,
                occupied = spot.occupied,
            )
        }
    }

    override fun findById(id: Long): ParkingSpot? =
        parkingSpotJdbcRepository.findById(id).orElse(null)?.toDomain()

    override fun findByCoordinates(lat: BigDecimal, lng: BigDecimal): ParkingSpot? =
        parkingSpotJdbcRepository.findByLatAndLng(lat, lng)?.toDomain()

    override fun findNearestAvailable(lat: BigDecimal, lng: BigDecimal): ParkingSpot? =
        parkingSpotJdbcRepository.findNearestAvailable(lat, lng)?.toDomain()

    override fun updateOccupied(spotId: Long, occupied: Boolean) {
        parkingSpotJdbcRepository.updateOccupied(spotId, occupied)
    }

    override fun countAll(): Long =
        parkingSpotJdbcRepository.count()
}
