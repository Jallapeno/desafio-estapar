package br.com.estapar.parking.application.port.inbound

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RevenueSummary(val amount: BigDecimal, val timestamp: Instant)

fun interface QueryRevenueUseCase {
    fun query(sector: String, date: LocalDate?): RevenueSummary
}
