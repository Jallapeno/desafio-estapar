package br.com.estapar.parking.domain.service

import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GarageTopologyPolicyTest {
    private val sector = Sector("A", BigDecimal.TEN, 2)
    private val spot = ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))

    @Test fun `should allow bootstrap and equivalent coordinates with preserved occupancy`() {
        val incoming = GarageConfiguration(listOf(sector), listOf(spot.copy(lat = BigDecimal("-23.5616840"))))
        assertDoesNotThrow { GarageTopologyPolicy.validate(emptyList(), emptyList(), incoming, 0) }
        assertDoesNotThrow { GarageTopologyPolicy.validate(listOf(sector), listOf(spot.copy(occupied = true)), incoming, 1) }
    }

    @Test fun `should reject removed identities and relocated spots`() {
        val b = Sector("B", BigDecimal.TEN, 2)
        val invalid = listOf(
            GarageConfiguration(listOf(b), listOf(spot.copy(id = 2L, sectorCode = "B"))),
            GarageConfiguration(listOf(sector), listOf(spot.copy(id = 2L))),
            GarageConfiguration(listOf(sector), listOf(spot.copy(lat = BigDecimal.ZERO))),
            GarageConfiguration(listOf(sector, b), listOf(spot.copy(sectorCode = "B"))),
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { GarageTopologyPolicy.validate(listOf(sector), listOf(spot), it, 0) }
        }
    }

    @Test fun `should allow capacity equal to occupancy and reject smaller capacity`() {
        assertDoesNotThrow {
            GarageTopologyPolicy.validate(listOf(sector), listOf(spot), GarageConfiguration(listOf(sector), listOf(spot)), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GarageTopologyPolicy.validate(listOf(sector), listOf(spot), GarageConfiguration(listOf(sector.copy(maxCapacity = 1)), listOf(spot)), 2)
        }
    }
}
