package br.com.estapar.parking.domain.model

import java.math.BigDecimal

/**
 * Logical sector configuration inside a parking garage.
 */
data class Sector(
    val code: String,
    val basePrice: BigDecimal,
    val maxCapacity: Int,
) {
    init {
        require(code.isNotBlank()) { "Sector code must not be blank" }
        require(basePrice >= BigDecimal.ZERO) { "Sector basePrice must not be negative" }
        require(maxCapacity >= 0) { "Sector maxCapacity must not be negative" }
    }
}
