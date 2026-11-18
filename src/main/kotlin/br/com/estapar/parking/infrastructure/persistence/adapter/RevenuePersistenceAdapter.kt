package br.com.estapar.parking.infrastructure.persistence.adapter

import br.com.estapar.parking.domain.port.outbound.RevenueRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant

@Singleton
class RevenuePersistenceAdapter(private val sessions: ParkingSessionJdbcRepository) : RevenueRepositoryPort {
    override fun sumBySectorAndExitTime(sector: String, start: Instant, end: Instant, asOf: Instant): BigDecimal =
        sessions.sumRevenue(sector, start, end, asOf)
}
