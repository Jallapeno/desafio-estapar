package br.com.estapar.parking.infrastructure.config

import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessExitUseCase
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.application.port.inbound.QueryRevenueUseCase
import br.com.estapar.parking.application.port.inbound.SyncGarageUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.application.service.EntryEventService
import br.com.estapar.parking.application.service.ExitEventService
import br.com.estapar.parking.application.service.GarageSyncService
import br.com.estapar.parking.application.service.ParkedEventService
import br.com.estapar.parking.application.service.RevenueQueryService
import br.com.estapar.parking.application.service.WebhookEventRouterService
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.RevenueRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.domain.service.PricingCalculator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.time.Clock

@Factory
class ApplicationUseCaseFactory {

    @Singleton
    fun syncGarageUseCase(
        garageConfigProviderPort: GarageConfigProviderPort,
        sectorRepositoryPort: SectorRepositoryPort,
        parkingSpotRepositoryPort: ParkingSpotRepositoryPort,
        transactionPort: TransactionPort,
        sessionRepositoryPort: ParkingSessionRepositoryPort,
    ): SyncGarageUseCase = GarageSyncService(
        sessionRepositoryPort = sessionRepositoryPort,
        garageConfigProviderPort = garageConfigProviderPort,
        sectorRepositoryPort = sectorRepositoryPort,
        parkingSpotRepositoryPort = parkingSpotRepositoryPort,
        transactionPort = transactionPort,
    )

    @Singleton
    fun processEntryUseCase(
        sectorRepositoryPort: SectorRepositoryPort,
        sessionRepositoryPort: ParkingSessionRepositoryPort,
        transactionPort: TransactionPort,
    ): ProcessEntryUseCase = EntryEventService(sectorRepositoryPort, sessionRepositoryPort, transactionPort)

    @Singleton
    fun processParkedUseCase(
        sectorRepositoryPort: SectorRepositoryPort,
        spotRepositoryPort: ParkingSpotRepositoryPort,
        sessionRepositoryPort: ParkingSessionRepositoryPort,
        transactionPort: TransactionPort,
    ): ProcessParkedUseCase = ParkedEventService(
        sectorRepositoryPort, spotRepositoryPort, sessionRepositoryPort, transactionPort,
    )

    @Singleton
    fun processExitUseCase(
        sectorRepositoryPort: SectorRepositoryPort,
        spotRepositoryPort: ParkingSpotRepositoryPort,
        sessionRepositoryPort: ParkingSessionRepositoryPort,
        transactionPort: TransactionPort,
    ): ProcessExitUseCase = ExitEventService(
        sectorRepositoryPort, spotRepositoryPort, sessionRepositoryPort, transactionPort, PricingCalculator(),
    )

    @Singleton
    fun processWebhookEventUseCase(
        processEntryUseCase: ProcessEntryUseCase,
        processParkedUseCase: ProcessParkedUseCase,
        processExitUseCase: ProcessExitUseCase,
    ): ProcessWebhookEventUseCase = WebhookEventRouterService(processEntryUseCase, processParkedUseCase, processExitUseCase)
    @Singleton
    fun revenueClock(): Clock = Clock.systemUTC()

    @Singleton
    fun queryRevenueUseCase(revenues: RevenueRepositoryPort, clock: Clock): QueryRevenueUseCase =
        RevenueQueryService(revenues, clock)

}
