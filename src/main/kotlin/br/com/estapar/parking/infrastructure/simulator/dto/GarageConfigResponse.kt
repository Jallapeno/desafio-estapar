package br.com.estapar.parking.infrastructure.simulator.dto

import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal

@Serdeable
data class GarageConfigResponse(
    val garage: List<GarageSectorDto> = emptyList(),
    val spots: List<GarageSpotDto> = emptyList(),
) {
    fun toDomain(): GarageConfiguration = GarageConfiguration(
        sectors = garage.map { it.toDomain() },
        spots = spots.map { it.toDomain() },
    )
}

@Serdeable
data class GarageSectorDto(
    val sector: String,
    @field:JsonProperty("basePrice")
    @field:JsonAlias("base_price")
    val basePrice: BigDecimal,
    @field:JsonProperty("max_capacity")
    @field:JsonAlias("maxCapacity")
    val maxCapacity: Int,
) {
    fun toDomain(): Sector = Sector(
        code = sector,
        basePrice = basePrice,
        maxCapacity = maxCapacity,
    )
}

@Serdeable
data class GarageSpotDto(
    val id: Long,
    val sector: String,
    val lat: BigDecimal,
    val lng: BigDecimal,
) {
    fun toDomain(): ParkingSpot = ParkingSpot(
        id = id,
        sectorCode = sector,
        lat = lat,
        lng = lng,
        occupied = false,
    )
}
