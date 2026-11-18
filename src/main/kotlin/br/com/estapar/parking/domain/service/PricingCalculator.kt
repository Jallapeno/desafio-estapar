package br.com.estapar.parking.domain.service

import br.com.estapar.parking.domain.exception.InvalidStayDurationException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** Pure billing calculation using the multiplier captured at entry, without reassessing occupancy. */
class PricingCalculator {
    fun calculate(
        entryTime: Instant,
        exitTime: Instant,
        basePrice: BigDecimal,
        dynamicMultiplier: BigDecimal,
    ): BigDecimal {
        val duration = Duration.between(entryTime, exitTime)
        if (duration.isNegative) throw InvalidStayDurationException()
        require(basePrice >= BigDecimal.ZERO) { "Base price must not be negative" }
        require(dynamicMultiplier > BigDecimal.ZERO) { "Dynamic multiplier must be positive" }
        if (duration <= Duration.ofMinutes(30)) return BigDecimal("0.00")

        // Charge from entry, including the first hour. Preserve even a nanosecond beyond a full hour.
        val completeHours = duration.seconds / 3600
        val hasPartialHour = duration.seconds % 3600 != 0L || duration.nano != 0
        val billableHours = completeHours + if (hasPartialHour) 1L else 0L
        return basePrice.multiply(dynamicMultiplier)
            .multiply(BigDecimal.valueOf(billableHours))
            .setScale(2, RoundingMode.HALF_UP)
    }
}
