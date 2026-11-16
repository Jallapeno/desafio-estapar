package br.com.estapar.parking.infrastructure.config

import br.com.estapar.parking.application.port.inbound.SyncGarageUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.application.service.GarageSyncService
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class ApplicationUseCaseFactory {

    @Singleton
    fun syncGarageUseCase(
        garageConfigProviderPort: GarageConfigProviderPort,
        sectorRepositoryPort: SectorRepositoryPort,
        parkingSpotRepositoryPort: ParkingSpotRepositoryPort,
        transactionPort: TransactionPort,
    ): SyncGarageUseCase = GarageSyncService(
        garageConfigProviderPort = garageConfigProviderPort,
        sectorRepositoryPort = sectorRepositoryPort,
        parkingSpotRepositoryPort = parkingSpotRepositoryPort,
        transactionPort = transactionPort,
    )
}
