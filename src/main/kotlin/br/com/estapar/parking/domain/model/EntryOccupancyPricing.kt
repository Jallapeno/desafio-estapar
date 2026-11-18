package br.com.estapar.parking.domain.model

import java.math.BigDecimal

/** Selects the entry multiplier from exact counts, before rounding the recorded rate. */
object EntryOccupancyPricing {
    fun multiplier(activeSessions: Long, totalCapacity: Long): BigDecimal {
        require(totalCapacity > 0 && activeSessions >= 0 && activeSessions < totalCapacity) {
            "Entry pricing requires available capacity and non-negative occupancy"
        }
        val active = BigDecimal.valueOf(activeSessions)
        val capacity = BigDecimal.valueOf(totalCapacity)
        return when {
            active.multiply(BigDecimal("4")) < capacity -> BigDecimal("0.90")
            active.multiply(BigDecimal("2")) <= capacity -> BigDecimal("1.00")
            active.multiply(BigDecimal("4")) <= capacity.multiply(BigDecimal("3")) -> BigDecimal("1.10")
            else -> BigDecimal("1.25")
        }
    }
}
