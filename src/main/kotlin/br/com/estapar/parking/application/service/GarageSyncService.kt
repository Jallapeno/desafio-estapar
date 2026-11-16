package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.SyncGarageUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import org.slf4j.LoggerFactory

/**
 * Orchestrates fetching the garage topology outside DB transactions and persisting it atomically.
 */
class GarageSyncService(
    private val garageConfigProviderPort: GarageConfigProviderPort,
    private val sectorRepositoryPort: SectorRepositoryPort,
    private val parkingSpotRepositoryPort: ParkingSpotRepositoryPort,
    private val transactionPort: TransactionPort,
) : SyncGarageUseCase {

    private val logger = LoggerFactory.getLogger(GarageSyncService::class.java)

    override fun synchronize() {
        val configuration = garageConfigProviderPort.fetchConfiguration()

        transactionPort.executeInTransaction {
            sectorRepositoryPort.upsertAll(configuration.sectors)
            parkingSpotRepositoryPort.upsertAll(configuration.spots)
        }

        logger.info(
            "Synchronized garage configuration: sectors={}, spots={}",
            configuration.sectors.size,
            configuration.spots.size,
        )
    }
}
