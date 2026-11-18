package br.com.estapar.parking.infrastructure.persistence.repository

import br.com.estapar.parking.infrastructure.persistence.entity.ParkingSessionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.Instant

@JdbcRepository(dialect = Dialect.MYSQL)
interface ParkingSessionJdbcRepository : CrudRepository<ParkingSessionEntity, Long> {

    @Query(
        """
        SELECT id, license_plate, status, entry_time, exit_time, spot_id, sector_code,
               occupancy_rate_at_entry, dynamic_multiplier, charged_amount
        FROM parking_sessions
        WHERE license_plate = :licensePlate
          AND status IN ('ENTERED', 'PARKED')
        LIMIT 1
        """,
    )
    fun findActiveByLicensePlate(licensePlate: String): ParkingSessionEntity?

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE license_plate = :licensePlate AND entry_time = :entryTime")
    fun countByLicensePlateAndEntryTime(licensePlate: String, entryTime: Instant): Long

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE status IN ('ENTERED', 'PARKED')")
    fun countActiveSessions(): Long

    @Query(
        """
        SELECT COALESCE(SUM(charged_amount), 0.00)
        FROM parking_sessions
        WHERE sector_code = :sector
          AND status = 'EXITED'
          AND exit_time >= :start
          AND exit_time < :end
          AND exit_time <= :asOf
        """,
    )
    fun sumRevenue(sector: String, start: Instant, end: Instant, asOf: Instant): BigDecimal

}
