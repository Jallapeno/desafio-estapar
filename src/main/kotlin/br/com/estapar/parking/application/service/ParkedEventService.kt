package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.ParkedResult
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import java.math.BigDecimal

class ParkedEventService(
    private val sectorRepository: SectorRepositoryPort,
    private val spotRepository: ParkingSpotRepositoryPort,
    private val sessionRepository: ParkingSessionRepositoryPort,
    private val transactionPort: TransactionPort,
) : ProcessParkedUseCase {
    override fun park(licensePlate: String, lat: BigDecimal, lng: BigDecimal): ParkedResult {
        require(lat >= BigDecimal("-90") && lat <= BigDecimal("90")) { "Invalid latitude" }
        require(lng >= BigDecimal("-180") && lng <= BigDecimal("180")) { "Invalid longitude" }
        require(lat.stripTrailingZeros().scale() <= 6 && lng.stripTrailingZeros().scale() <= 6) {
            "Coordinates must be representable without rounding to six decimal places"
        }
        return transactionPort.executeInTransaction {
            // Share ENTRY's lock, before any snapshot read, to serialize changes to sessions and spots.
            sectorRepository.findAllForUpdate()
            val session = sessionRepository.findActiveByLicensePlate(licensePlate)
                ?: return@executeInTransaction ParkedResult.NO_ACTIVE_SESSION
            val spot = spotRepository.findByCoordinates(lat, lng)
                ?: return@executeInTransaction ParkedResult.UNKNOWN_SPOT
            if (session.status == SessionStatus.PARKED) {
                return@executeInTransaction if (session.spotId == spot.id && session.sectorCode == spot.sectorCode) {
                    ParkedResult.ALREADY_PARKED
                } else {
                    ParkedResult.DIFFERENT_SPOT
                }
            }
            if (spot.occupied) return@executeInTransaction ParkedResult.SPOT_OCCUPIED

            spotRepository.updateOccupied(spot.id, true)
            sessionRepository.save(session.park(spot.id, spot.sectorCode))
            ParkedResult.PARKED
        }
    }
}
