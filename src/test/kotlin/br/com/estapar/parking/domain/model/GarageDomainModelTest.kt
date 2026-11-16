package br.com.estapar.parking.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GarageDomainModelTest {

    @Test
    fun `should reject blank sector code or negative basePrice or maxCapacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sector(code = " ", basePrice = BigDecimal("10.00"), maxCapacity = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Sector(code = "A", basePrice = BigDecimal("-1.00"), maxCapacity = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = -1)
        }
    }

    @Test
    fun `should toggle parking spot occupancy immutably`() {
        val spot = ParkingSpot(
            id = 1L,
            sectorCode = "A",
            lat = BigDecimal("-23.561684"),
            lng = BigDecimal("-46.655981"),
        )

        assertFalse(spot.occupied)

        val occupiedSpot = spot.occupy()
        assertTrue(occupiedSpot.occupied)
        assertFalse(spot.occupied)

        val releasedSpot = occupiedSpot.release()
        assertFalse(releasedSpot.occupied)
    }
}
