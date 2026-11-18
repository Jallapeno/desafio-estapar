package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.ExitResult
import br.com.estapar.parking.application.port.inbound.ProcessExitUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.domain.service.PricingCalculator
import java.time.Instant

class ExitEventService(
    private val sectorRepository: SectorRepositoryPort,
    private val spotRepository: ParkingSpotRepositoryPort,
    private val sessionRepository: ParkingSessionRepositoryPort,
    private val transactionPort: TransactionPort,
    private val pricingCalculator: PricingCalculator,
) : ProcessExitUseCase {
    override fun exit(licensePlate: String, exitTime: Instant): ExitResult =
        transactionPort.executeInTransaction {
            // Use the same lock as ENTRY/PARKED before reading sessions or changing occupancy.
            val sectors = sectorRepository.findAllForUpdate()
            val session = sessionRepository.findActiveByLicensePlate(licensePlate)
                ?: return@executeInTransaction ExitResult.NO_ACTIVE_SESSION
            val spot = if (session.status == SessionStatus.PARKED) {
                val occupiedSpot = checkNotNull(session.spotId?.let(spotRepository::findById)) {
                    "Parked session must reference an existing spot"
                }
                check(occupiedSpot.occupied && occupiedSpot.sectorCode == session.sectorCode) {
                    "Parked session and spot occupancy must be consistent"
                }
                occupiedSpot
            } else null
            // US-08 fallback policy: a vehicle without PARKED uses the first configured sector by code.
            val sector = checkNotNull(
                if (spot != null) sectors.find { it.code == session.sectorCode } else sectors.firstOrNull(),
            ) { "Cannot bill a session without a configured sector" }
            val amount = pricingCalculator.calculate(
                session.entryTime, exitTime, sector.basePrice, session.dynamicMultiplier,
            )
            val exited = session.exit(exitTime, amount, fallbackSectorCode = sector.code)
            if (spot != null) spotRepository.updateOccupied(spot.id, false)
            sessionRepository.save(exited)
            ExitResult.EXITED
        }
}
