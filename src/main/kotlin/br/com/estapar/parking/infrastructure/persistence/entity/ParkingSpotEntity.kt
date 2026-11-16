package br.com.estapar.parking.infrastructure.persistence.entity

import br.com.estapar.parking.domain.model.ParkingSpot
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.math.BigDecimal

@MappedEntity("parking_spots")
data class ParkingSpotEntity(
    @field:Id
    val id: Long,
    @field:MappedProperty("sector_code")
    val sectorCode: String,
    val lat: BigDecimal,
    val lng: BigDecimal,
    val occupied: Boolean = false,
) {
    fun toDomain(): ParkingSpot = ParkingSpot(
        id = id,
        sectorCode = sectorCode,
        lat = lat,
        lng = lng,
        occupied = occupied,
    )

    companion object {
        fun fromDomain(spot: ParkingSpot): ParkingSpotEntity = ParkingSpotEntity(
            id = spot.id,
            sectorCode = spot.sectorCode,
            lat = spot.lat,
            lng = spot.lng,
            occupied = spot.occupied,
        )
    }
}
