package br.com.estapar.parking.domain.port.outbound

import br.com.estapar.parking.domain.model.ParkingSession
import java.time.Instant

/**
 * Outbound port for persisting and querying vehicle parking sessions.
 */
interface ParkingSessionRepositoryPort {
    fun save(session: ParkingSession): ParkingSession
    fun findActiveByLicensePlate(licensePlate: String): ParkingSession?
    fun existsByLicensePlateAndEntryTime(licensePlate: String, entryTime: Instant): Boolean
    fun countActiveSessions(): Long
}
