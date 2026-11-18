package br.com.estapar.parking.domain.port.outbound

import java.math.BigDecimal
import java.time.Instant

fun interface RevenueRepositoryPort {
    fun sumBySectorAndExitTime(sector: String, start: Instant, end: Instant, asOf: Instant): BigDecimal
}
