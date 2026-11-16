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

    @Query(
        """
        SELECT id, sector_code, lat, lng, occupied
        FROM parking_spots
        WHERE occupied = FALSE
        ORDER BY POW(lat - :lat, 2) + POW(lng - :lng, 2) ASC
        LIMIT 1
        """,
    )
    fun findNearestAvailable(lat: BigDecimal, lng: BigDecimal): ParkingSpotEntity?

    @Query("UPDATE parking_spots SET occupied = :occupied WHERE id = :id")
    fun updateOccupied(id: Long, occupied: Boolean)

    @Query(
        """
        INSERT INTO parking_spots (id, sector_code, lat, lng, occupied)
        VALUES (:id, :sectorCode, :lat, :lng, :occupied)
        ON DUPLICATE KEY UPDATE
            sector_code = VALUES(sector_code),
            lat = VALUES(lat),
            lng = VALUES(lng)
        """,
    )
    fun upsert(id: Long, sectorCode: String, lat: BigDecimal, lng: BigDecimal, occupied: Boolean)
}
