package br.com.estapar.parking.domain.port.outbound

import br.com.estapar.parking.domain.model.ParkingSession

/**
 * Outbound port for persisting and querying vehicle parking sessions.
 */
interface ParkingSessionRepositoryPort {
    fun save(session: ParkingSession): ParkingSession
    fun findActiveByLicensePlate(licensePlate: String): ParkingSession?
    fun countActiveSessions(): Long
}
