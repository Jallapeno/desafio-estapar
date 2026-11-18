package br.com.estapar.parking.infrastructure.persistence.repository

import br.com.estapar.parking.infrastructure.persistence.entity.ParkingSpotEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal

@JdbcRepository(dialect = Dialect.MYSQL)
interface ParkingSpotJdbcRepository : CrudRepository<ParkingSpotEntity, Long> {

    fun findByLatAndLng(lat: BigDecimal, lng: BigDecimal): ParkingSpotEntity?

    @Query("UPDATE parking_spots SET occupied = :occupied WHERE id = :id")
    fun updateOccupied(id: Long, occupied: Boolean)

    @Query(
        """
        INSERT INTO parking_spots (id, sector_code, lat, lng, occupied)
        VALUES (:id, :sectorCode, :lat, :lng, :occupied) AS incoming
        ON DUPLICATE KEY UPDATE
            sector_code = incoming.sector_code,
            lat = incoming.lat,
            lng = incoming.lng
        """,
    )
    fun upsert(id: Long, sectorCode: String, lat: BigDecimal, lng: BigDecimal, occupied: Boolean)
}
