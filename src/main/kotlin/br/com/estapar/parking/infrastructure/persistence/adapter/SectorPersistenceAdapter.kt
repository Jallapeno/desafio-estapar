package br.com.estapar.parking.infrastructure.persistence.adapter

import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import jakarta.inject.Singleton

@Singleton
open class SectorPersistenceAdapter(
    private val sectorJdbcRepository: SectorJdbcRepository,
) : SectorRepositoryPort {

    override fun upsertAll(sectors: List<Sector>) {
        sectors.forEach { sector ->
            sectorJdbcRepository.upsert(
                sector = sector.code,
                basePrice = sector.basePrice,
                maxCapacity = sector.maxCapacity,
            )
        }
    }

    override fun findAllForUpdate(): List<Sector> =
        sectorJdbcRepository.findAllForUpdate().map { it.toDomain() }

    override fun findByCode(code: String): Sector? =
        sectorJdbcRepository.findById(code).orElse(null)?.toDomain()

    override fun findFirst(): Sector? =
        sectorJdbcRepository.findFirstOrdered()?.toDomain()

    override fun sumMaxCapacity(): Long =
        sectorJdbcRepository.sumMaxCapacity()
}
