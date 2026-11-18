package br.com.estapar.parking.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ParkingSessionDomainModelTest {

    @Test
    fun `should reject invalid invariants when creating parking session`() {
        val now = Instant.parse("2025-01-01T12:00:00.123Z")

        assertThrows(IllegalArgumentException::class.java) {
            ParkingSession(
                licensePlate = " ",
                status = SessionStatus.ENTERED,
                entryTime = now,
                occupancyRateAtEntry = BigDecimal("0.1000"),
                dynamicMultiplier = BigDecimal("0.90"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParkingSession(
                licensePlate = "A".repeat(21),
                status = SessionStatus.ENTERED,
                entryTime = now,
                occupancyRateAtEntry = BigDecimal("0.1000"),
                dynamicMultiplier = BigDecimal("0.90"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParkingSession(
                licensePlate = "ZUL0001",
                status = SessionStatus.ENTERED,
                entryTime = now,
                occupancyRateAtEntry = BigDecimal("-0.01"),
                dynamicMultiplier = BigDecimal("0.90"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParkingSession(
                licensePlate = "ZUL0001",
                status = SessionStatus.ENTERED,
                entryTime = now,
                occupancyRateAtEntry = BigDecimal("0.1000"),
                dynamicMultiplier = BigDecimal.ZERO,
            )
        }
    }

    @Test
    fun `should transition session from ENTERED to PARKED and EXITED`() {
        val entryTime = Instant.parse("2025-01-01T12:00:00.123Z")
        val exitTime = Instant.parse("2025-01-01T13:00:00.456Z")
        val entered = ParkingSession(
            id = 1L,
            licensePlate = "ZUL0001",
            status = SessionStatus.ENTERED,
            entryTime = entryTime,
            occupancyRateAtEntry = BigDecimal("0.2000"),
            dynamicMultiplier = BigDecimal("0.90"),
        )

        val parked = entered.park(spotId = 10L, sectorCode = "A")
        assertEquals(SessionStatus.PARKED, parked.status)
        assertEquals(10L, parked.spotId)
        assertEquals("A", parked.sectorCode)

        val exited = parked.exit(exitTime = exitTime, chargedAmount = BigDecimal("9.00"))
        assertEquals(SessionStatus.EXITED, exited.status)
        assertEquals(exitTime, exited.exitTime)
        assertEquals(BigDecimal("9.00"), exited.chargedAmount)
    }

    @Test
    fun `should reject invalid state transitions and negative stay duration or amount`() {
        val entryTime = Instant.parse("2025-01-01T12:00:00.000Z")
        val entered = ParkingSession(
            licensePlate = "ZUL0001",
            status = SessionStatus.ENTERED,
            entryTime = entryTime,
            occupancyRateAtEntry = BigDecimal("0.2000"),
            dynamicMultiplier = BigDecimal("0.90"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            entered.park(spotId = 1L, sectorCode = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            entered.exit(exitTime = entryTime.minusSeconds(1), chargedAmount = BigDecimal.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            entered.exit(exitTime = entryTime.plusSeconds(60), chargedAmount = BigDecimal("-1.00"))
        }

        val exited = entered.exit(
            exitTime = entryTime.plusSeconds(60),
            chargedAmount = BigDecimal.ZERO,
            fallbackSectorCode = "A",
        )
        assertEquals("A", exited.sectorCode)

        assertThrows(IllegalArgumentException::class.java) {
            exited.park(spotId = 1L, sectorCode = "A")
        }
        assertThrows(IllegalArgumentException::class.java) {
            exited.exit(exitTime = entryTime.plusSeconds(120), chargedAmount = BigDecimal.ZERO)
        }
    }
    @Test
    fun `should allow parked replay but reject moving to another spot or sector`() {
        val parked = ParkingSession(
            licensePlate = "ZUL0001",
            status = SessionStatus.ENTERED,
            entryTime = Instant.parse("2025-01-01T12:00:00Z"),
            occupancyRateAtEntry = BigDecimal.ZERO,
            dynamicMultiplier = BigDecimal("0.90"),
        ).park(1L, "A")
        assertEquals(parked, parked.park(1L, "A"))
        assertThrows(IllegalArgumentException::class.java) { parked.park(2L, "A") }
        assertThrows(IllegalArgumentException::class.java) { parked.park(1L, "B") }
    }

}
