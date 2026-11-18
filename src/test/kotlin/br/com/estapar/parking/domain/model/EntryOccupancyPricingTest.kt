package br.com.estapar.parking.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class EntryOccupancyPricingTest {
    @TestFactory
    fun `should classify exact occupancy boundaries without rounding first`() = listOf(
        Triple(0L, 100L, "0.90"),
        Triple(2499L, 10000L, "0.90"),
        Triple(4999L, 20000L, "0.90"),
        Triple(25L, 100L, "1.00"),
        Triple(50L, 100L, "1.00"),
        Triple(10001L, 20000L, "1.10"),
        Triple(505L, 1000L, "1.10"),
        Triple(75L, 100L, "1.10"),
        Triple(15001L, 20000L, "1.25"),
        Triple(755L, 1000L, "1.25"),
        Triple(99L, 100L, "1.25"),
        Triple(Long.MAX_VALUE - 1, Long.MAX_VALUE, "1.25"),
    ).map { (active, capacity, expected) ->
        dynamicTest("$active/$capacity -> $expected") {
            assertEquals(BigDecimal(expected), EntryOccupancyPricing.multiplier(active, capacity))
        }
    }

    @Test
    fun `should reject invalid occupancy or unavailable capacity`() {
        listOf(-1L to 100L, 0L to 0L, 100L to 100L, 101L to 100L).forEach { (active, capacity) ->
            assertThrows(IllegalArgumentException::class.java) {
                EntryOccupancyPricing.multiplier(active, capacity)
            }
        }
    }
}
