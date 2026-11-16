package br.com.estapar.parking.domain.port.outbound

import br.com.estapar.parking.domain.model.Sector

/**
 * Outbound port for managing garage sector persistence and capacity locks.
 */
interface SectorRepositoryPort {
    fun upsertAll(sectors: List<Sector>)
    fun findAllForUpdate(): List<Sector>
    fun findByCode(code: String): Sector?
    fun findFirst(): Sector?
    fun sumMaxCapacity(): Long
}
