package br.com.estapar.parking.application.port.inbound

/**
 * Inbound use case for synchronizing garage sectors and parking spots.
 */
interface SyncGarageUseCase {
    fun synchronize()
}
