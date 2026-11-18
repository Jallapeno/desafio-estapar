package br.com.estapar.parking.domain.port.outbound

import br.com.estapar.parking.domain.model.GarageConfiguration

/**
 * Outbound port for fetching initial garage topology and pricing configuration.
 */
interface GarageConfigProviderPort {
    fun fetchConfiguration(): GarageConfiguration
}
