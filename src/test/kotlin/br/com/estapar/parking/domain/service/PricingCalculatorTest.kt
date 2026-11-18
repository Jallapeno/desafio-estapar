package br.com.estapar.parking.domain.service

import br.com.estapar.parking.domain.exception.InvalidStayDurationException
import br.com.estapar.parking.domain.model.EntryOccupancyPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

class PricingCalculatorTest {
    private val calculator = PricingCalculator()
    private val entry = Instant.parse("2025-01-01T12:00:00.123Z")

    private fun amount(duration: Duration, multiplier: String = "1.00", basePrice: String = "10.00") =
        calculator.calculate(entry, entry.plus(duration), BigDecimal(basePrice), BigDecimal(multiplier))

    private data class StayCase(val id: String, val duration: String, val multiplier: String, val expected: String)

    @TestFactory
    fun `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up`() = listOf(
        StayCase("CT-01", "PT0S", "0.90", "0.00"),
        StayCase("CT-02", "PT15M", "1.25", "0.00"),
        StayCase("CT-03", "PT30M", "1.00", "0.00"),
        StayCase("CT-04", "PT30M1S", "1.00", "10.00"),
        StayCase("CT-05", "PT31M", "1.00", "10.00"),
        StayCase("CT-06", "PT60M", "1.00", "10.00"),
        StayCase("CT-07", "PT60M1S", "1.00", "20.00"),
        StayCase("CT-08", "PT2H15M", "1.00", "30.00"),
    ).map { case ->
        dynamicTest("${case.id}: ${case.duration} -> ${case.expected}") {
            assertEquals(BigDecimal(case.expected), amount(Duration.parse(case.duration), case.multiplier))
        }
    }

    private data class OccupancyCase(val id: String, val active: Long, val capacity: Long, val expected: String)

    @TestFactory
    fun `CT-09 to CT-17 should apply existing entry multiplier to hourly price`() = listOf(
        OccupancyCase("CT-09", 0, 100, "9.00"),
        OccupancyCase("CT-10", 24, 100, "9.00"),
        OccupancyCase("CT-11", 2499, 10000, "9.00"),
        OccupancyCase("CT-12", 25, 100, "10.00"),
        OccupancyCase("CT-13", 50, 100, "10.00"),
        OccupancyCase("CT-14", 101, 200, "11.00"),
        OccupancyCase("CT-15", 75, 100, "11.00"),
        OccupancyCase("CT-16", 151, 200, "12.50"),
        OccupancyCase("CT-17", 99, 100, "12.50"),
    ).map { case ->
        dynamicTest("${case.id}: ${case.active}/${case.capacity} -> ${case.expected}") {
            val savedMultiplier = EntryOccupancyPricing.multiplier(case.active, case.capacity)
            assertEquals(BigDecimal(case.expected), calculator.calculate(
                entry, entry.plusSeconds(3600), BigDecimal("10.00"), savedMultiplier,
            ))
        }
    }

    @TestFactory
    fun `should preserve fractions at tolerance and hourly boundaries`() = listOf(
        "PT29M59.999999999S" to "0.00",
        "PT30M0.000000001S" to "10.00",
        "PT30M0.001S" to "10.00",
        "PT59M59.999999999S" to "10.00",
        "PT60M0.000000001S" to "20.00",
        "PT60M0.001S" to "20.00",
        "PT2H" to "20.00",
        "PT2H0.000000001S" to "30.00",
        "PT24H1M" to "250.00",
    ).map { (duration, expected) ->
        dynamicTest("$duration -> $expected") {
            assertEquals(BigDecimal(expected), amount(Duration.parse(duration)))
        }
    }

    @Test
    fun `should round monetary value only after multiplying all hours`() {
        assertEquals(BigDecimal("11.06"), amount(Duration.ofHours(1), "1.10", "10.05"))
        assertEquals(BigDecimal("22.11"), amount(Duration.ofHours(2), "1.10", "10.05"))
        assertEquals(BigDecimal("11.01"), amount(Duration.ofHours(1), "1.10", "10.01"))
        assertEquals(BigDecimal("0.00"), amount(Duration.ofHours(1), "1.25", "0.00"))
    }

    @Test
    fun `should reject exit before entry even by a nanosecond`() {
        assertThrows(InvalidStayDurationException::class.java) {
            calculator.calculate(entry, entry.minusNanos(1), BigDecimal.TEN, BigDecimal.ONE)
        }
    }

    @Test
    fun `should reject negative price or nonpositive multiplier even during free period`() {
        listOf("-0.01" to "1.00", "10.00" to "0.00", "10.00" to "-0.90").forEach { (price, multiplier) ->
            assertThrows(IllegalArgumentException::class.java) { amount(Duration.ZERO, multiplier, price) }
        }
    }

    @Test
    fun `should calculate elapsed time from instants across date and offset boundaries`() {
        val start = OffsetDateTime.parse("2025-01-01T23:30:00-03:00").toInstant()
        val end = Instant.parse("2025-01-02T03:30:00Z")
        assertEquals(BigDecimal("9.00"), calculator.calculate(start, end, BigDecimal.TEN, BigDecimal("0.90")))
    }
}
