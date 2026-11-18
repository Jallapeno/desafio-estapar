package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.domain.model.EntryOccupancyPricing
import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class EntryEventService(
    private val sectorRepository: SectorRepositoryPort,
    private val sessionRepository: ParkingSessionRepositoryPort,
    private val transactionPort: TransactionPort,
) : ProcessEntryUseCase {
    override fun enter(licensePlate: String, entryTime: Instant): EntryResult =
        transactionPort.executeInTransaction {
            // Lock before any session read: the subsequent snapshot must include the previous entry's commit.
            val sectors = sectorRepository.findAllForUpdate()
            if (sessionRepository.findActiveByLicensePlate(licensePlate) != null) {
                return@executeInTransaction EntryResult.ALREADY_ACTIVE
            }
            val capacity = sectors.sumOf { it.maxCapacity.toLong() }
            val active = sessionRepository.countActiveSessions()
            if (capacity <= 0 || active >= capacity) {
                return@executeInTransaction EntryResult.FULL
            }
            sessionRepository.save(
                ParkingSession(
                    licensePlate = licensePlate,
                    status = SessionStatus.ENTERED,
                    entryTime = entryTime,
                    occupancyRateAtEntry = BigDecimal.valueOf(active)
                        .divide(BigDecimal.valueOf(capacity), 4, RoundingMode.HALF_UP),
                    dynamicMultiplier = EntryOccupancyPricing.multiplier(active, capacity),
                ),
            )
            EntryResult.ACCEPTED
        }
}
