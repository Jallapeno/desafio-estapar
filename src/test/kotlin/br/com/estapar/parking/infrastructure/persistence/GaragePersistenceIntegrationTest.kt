package br.com.estapar.parking.infrastructure.persistence

import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Connection

@MicronautTest(transactional = false)
class GaragePersistenceIntegrationTest {

    @Inject
    lateinit var sectorRepositoryPort: SectorRepositoryPort

    @Inject
    lateinit var parkingSpotRepositoryPort: ParkingSpotRepositoryPort

    @Inject
    lateinit var sectorJdbcRepository: SectorJdbcRepository

    @Inject
    lateinit var parkingSpotJdbcRepository: ParkingSpotJdbcRepository

    @Inject
    lateinit var jdbcOperations: JdbcOperations

    @Inject
    lateinit var transactionOperations: TransactionOperations<Connection>

    @BeforeEach
    fun cleanup() {
        parkingSpotJdbcRepository.deleteAll()
        sectorJdbcRepository.deleteAll()
    }

    @Test
    fun `should apply V1 migration with DECIMAL(10,6) precision for coordinates`() {
        val columnTypes = transactionOperations.executeRead {
            jdbcOperations.prepareStatement(
                """
                SELECT COLUMN_NAME, COLUMN_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'parking_spots'
                  AND COLUMN_NAME IN ('lat', 'lng')
                """.trimIndent(),
            ) { statement ->
                statement.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getString("COLUMN_NAME").lowercase(), rs.getString("COLUMN_TYPE").lowercase())
                        }
                    }
                }
            }
        }

        assertEquals("decimal(10,6)", columnTypes["lat"])
        assertEquals("decimal(10,6)", columnTypes["lng"])
    }

    @Test
    fun `should persist sectors and spots and find spot by exact coordinates`() {
        val sectorA = Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = 100)
        val spot1 = ParkingSpot(
            id = 1L,
            sectorCode = "A",
            lat = BigDecimal("-23.561684"),
            lng = BigDecimal("-46.655981"),
        )

        sectorRepositoryPort.upsertAll(listOf(sectorA))
        parkingSpotRepositoryPort.upsertAll(listOf(spot1))

        val found = parkingSpotRepositoryPort.findByCoordinates(
            lat = BigDecimal("-23.561684"),
            lng = BigDecimal("-46.655981"),
        )

        assertNotNull(found)
        assertEquals(1L, found!!.id)
        assertEquals("A", found.sectorCode)
        assertFalse(found.occupied)
        assertEquals(100L, sectorRepositoryPort.sumMaxCapacity())
    }

    @Test
    fun `should preserve spot occupancy on idempotent upsert and find nearest available spot`() {
        val sectorA = Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = 50)
        val sectorB = Sector(code = "B", basePrice = BigDecimal("15.00"), maxCapacity = 50)
        sectorRepositoryPort.upsertAll(listOf(sectorA, sectorB))

        val spot1 = ParkingSpot(
            id = 1L,
            sectorCode = "A",
            lat = BigDecimal("-23.561684"),
            lng = BigDecimal("-46.655981"),
        )
        val spot2 = ParkingSpot(
            id = 2L,
            sectorCode = "B",
            lat = BigDecimal("-23.561700"),
            lng = BigDecimal("-46.656000"),
        )
        parkingSpotRepositoryPort.upsertAll(listOf(spot1, spot2))
        parkingSpotRepositoryPort.updateOccupied(spotId = 1L, occupied = true)

        // Resync should update metadata without resetting occupied flag on spot 1
        parkingSpotRepositoryPort.upsertAll(listOf(spot1, spot2))

        val reloadedSpot1 = parkingSpotRepositoryPort.findById(1L)
        assertNotNull(reloadedSpot1)
        assertTrue(reloadedSpot1!!.occupied)

        val nearest = parkingSpotRepositoryPort.findNearestAvailable(
            lat = BigDecimal("-23.561685"),
            lng = BigDecimal("-46.655982"),
        )
        assertNotNull(nearest)
        assertEquals(2L, nearest!!.id)
        assertEquals("B", nearest.sectorCode)
    }

    @Test
    fun `should lock and return all sectors for update inside transaction`() {
        sectorRepositoryPort.upsertAll(
            listOf(
                Sector(code = "B", basePrice = BigDecimal("12.00"), maxCapacity = 40),
                Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = 60),
            ),
        )

        val lockedSectors = transactionOperations.executeWrite {
            sectorRepositoryPort.findAllForUpdate()
        }

        assertEquals(listOf("A", "B"), lockedSectors.map { it.code })
        assertEquals(100L, sectorRepositoryPort.sumMaxCapacity())
    }

    @Test
    fun `should find sector by code, first ordered sector, count spots and release occupied spot`() {
        sectorRepositoryPort.upsertAll(
            listOf(
                Sector(code = "B", basePrice = BigDecimal("15.00"), maxCapacity = 30),
                Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = 20),
            ),
        )
        parkingSpotRepositoryPort.upsertAll(
            listOf(
                ParkingSpot(
                    id = 10L,
                    sectorCode = "A",
                    lat = BigDecimal("-23.561684"),
                    lng = BigDecimal("-46.655981"),
                ),
                ParkingSpot(
                    id = 20L,
                    sectorCode = "B",
                    lat = BigDecimal("-23.561700"),
                    lng = BigDecimal("-46.656000"),
                ),
            ),
        )

        val sectorB = sectorRepositoryPort.findByCode("B")
        val firstSector = sectorRepositoryPort.findFirst()
        assertNotNull(sectorB)
        assertEquals(BigDecimal("15.00"), sectorB!!.basePrice)
        assertNotNull(firstSector)
        assertEquals("A", firstSector!!.code)
        assertEquals(2L, parkingSpotRepositoryPort.countAll())

        parkingSpotRepositoryPort.updateOccupied(spotId = 10L, occupied = true)
        assertTrue(parkingSpotRepositoryPort.findById(10L)!!.occupied)

        parkingSpotRepositoryPort.updateOccupied(spotId = 10L, occupied = false)
        assertFalse(parkingSpotRepositoryPort.findById(10L)!!.occupied)
    }
}
