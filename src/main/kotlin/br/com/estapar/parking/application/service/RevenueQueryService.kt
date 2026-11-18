package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.QueryRevenueUseCase
import br.com.estapar.parking.application.port.inbound.RevenueSummary
import br.com.estapar.parking.domain.port.outbound.RevenueRepositoryPort
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class RevenueQueryService(private val revenues: RevenueRepositoryPort, private val clock: Clock) : QueryRevenueUseCase {
    override fun query(sector: String, date: LocalDate?): RevenueSummary {
        val code = sector.trim()
        require(code.isNotEmpty() && code.length <= 20) { "sector must contain 1 to 20 characters" }
        val now = clock.instant()
        val day = date ?: LocalDate.ofInstant(now, ZoneOffset.UTC)
        require(day >= LocalDate.of(1000, 1, 1) && day < LocalDate.of(9999, 12, 31)) {
            "date is outside the supported database range"
        }
        val start = day.atStartOfDay().toInstant(ZoneOffset.UTC)
        val end = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val amount = revenues.sumBySectorAndExitTime(code, start, end, now).setScale(2)
        return RevenueSummary(amount, now)
    }
}
