package br.com.estapar.parking.infrastructure.web.dto

import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal
import java.time.Instant

@Serdeable
data class RevenueResponse(val amount: BigDecimal, val currency: String, val timestamp: Instant)
