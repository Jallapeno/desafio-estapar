package br.com.estapar.parking.domain.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class GarageConfigurationTest {
    private val sector = Sector("A", BigDecimal("10.00"), 100)
    private val spot = ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))

    @Test
    fun `should accept a partial spot listing and decimal values with trailing zeroes`() {
        assertDoesNotThrow {
            GarageConfiguration(listOf(sector), listOf(spot.copy(lat = BigDecimal("-23.5616840"))))
        }
    }

    @TestFactory
    fun `should reject invalid topology before persistence`() = listOf<Pair<String, () -> GarageConfiguration>>(
        "no sectors" to { GarageConfiguration(emptyList(), listOf(spot)) },
        "no spots" to { GarageConfiguration(listOf(sector), emptyList()) },
        "duplicate sectors" to { GarageConfiguration(listOf(sector, sector), listOf(spot)) },
        "case insensitive sector collision" to {
            GarageConfiguration(listOf(sector, sector.copy(code = "a")), listOf(spot))
        },
        "duplicate ids" to {
            GarageConfiguration(listOf(sector), listOf(spot, spot.copy(lat = BigDecimal.ZERO)))
        },
        "duplicate coordinates with different scales" to {
            GarageConfiguration(listOf(sector), listOf(spot, spot.copy(id = 2L, lat = BigDecimal("-23.5616840"))))
        },
        "unknown sector" to { GarageConfiguration(listOf(sector), listOf(spot.copy(sectorCode = "B"))) },
        "latitude outside range" to { GarageConfiguration(listOf(sector), listOf(spot.copy(lat = BigDecimal("90.1")))) },
        "longitude outside range" to { GarageConfiguration(listOf(sector), listOf(spot.copy(lng = BigDecimal("-180.1")))) },
        "coordinate rounding" to { GarageConfiguration(listOf(sector), listOf(spot.copy(lat = BigDecimal("-23.5616841")))) },
        "price rounding" to { GarageConfiguration(listOf(sector.copy(basePrice = BigDecimal("10.001"))), listOf(spot)) },
        "price overflow" to { GarageConfiguration(listOf(sector.copy(basePrice = BigDecimal("100000000"))), listOf(spot)) },
        "sector code overflow" to { GarageConfiguration(listOf(sector.copy(code = "A".repeat(21))), listOf(spot)) },
    ).map { (name, configuration) ->
        dynamicTest(name) { assertThrows(IllegalArgumentException::class.java) { configuration() } }
    }
}
