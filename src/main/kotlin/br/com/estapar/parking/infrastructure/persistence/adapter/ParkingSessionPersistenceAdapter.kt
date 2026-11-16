package br.com.estapar.parking.infrastructure.persistence.adapter

import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.entity.ParkingSessionEntity
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import jakarta.inject.Singleton

@Singleton
open class ParkingSessionPersistenceAdapter(
    private val parkingSessionJdbcRepository: ParkingSessionJdbcRepository,
) : ParkingSessionRepositoryPort {

    override fun save(session: ParkingSession): ParkingSession {
        val entity = ParkingSessionEntity.fromDomain(session)
        val persisted = if (entity.id == null) {
            parkingSessionJdbcRepository.save(entity)
        } else {
            parkingSessionJdbcRepository.update(entity)
        }
        return persisted.toDomain()
    }

    override fun findActiveByLicensePlate(licensePlate: String): ParkingSession? =
        parkingSessionJdbcRepository.findActiveByLicensePlate(licensePlate)?.toDomain()

    override fun countActiveSessions(): Long =
        parkingSessionJdbcRepository.countActiveSessions()
}
