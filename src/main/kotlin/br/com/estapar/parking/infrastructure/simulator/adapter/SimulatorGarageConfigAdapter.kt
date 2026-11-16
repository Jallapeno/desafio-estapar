package br.com.estapar.parking.infrastructure.simulator.adapter

import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.infrastructure.simulator.client.SimulatorHttpClient
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton

@Singleton
open class SimulatorGarageConfigAdapter(
    private val simulatorHttpClient: SimulatorHttpClient,
) : GarageConfigProviderPort {

    @Retryable(
        attempts = "\${simulator.retry.attempts:5}",
        delay = "\${simulator.retry.delay:1s}",
        multiplier = "\${simulator.retry.multiplier:2.0}",
    )
    override fun fetchConfiguration(): GarageConfiguration =
        simulatorHttpClient.fetchGarageConfiguration().toDomain()
}
