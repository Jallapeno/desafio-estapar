package br.com.estapar.parking.infrastructure.persistence.entity

import br.com.estapar.parking.domain.model.Sector
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.math.BigDecimal

@MappedEntity("sectors")
data class SectorEntity(
    @field:Id
    val sector: String,
    @field:MappedProperty("base_price")
    val basePrice: BigDecimal,
    @field:MappedProperty("max_capacity")
    val maxCapacity: Int,
) {
    fun toDomain(): Sector = Sector(
        code = sector,
        basePrice = basePrice,
        maxCapacity = maxCapacity,
    )

    companion object {
        fun fromDomain(sector: Sector): SectorEntity = SectorEntity(
            sector = sector.code,
            basePrice = sector.basePrice,
            maxCapacity = sector.maxCapacity,
        )
    }
}
