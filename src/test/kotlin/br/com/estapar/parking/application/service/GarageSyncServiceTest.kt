package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.simulator.dto.GarageConfigResponse
import io.micronaut.serde.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GarageSyncServiceTest {
    private val sessions = object : ParkingSessionRepositoryPort {
        override fun save(session: ParkingSession) = session
        override fun findActiveByLicensePlate(licensePlate: String): ParkingSession? = null
        override fun existsByLicensePlateAndEntryTime(licensePlate: String, entryTime: Instant) = false
        override fun countActiveSessions() = 0L
    }


    @Test
    fun `should fetch configuration outside transaction and persist sectors and spots atomically`() {
        val events = mutableListOf<String>()
        val expectedSector = Sector(code = "A", basePrice = BigDecimal("10.00"), maxCapacity = 100)
        val expectedSpot = ParkingSpot(
            id = 1L,
            sectorCode = "A",
            lat = BigDecimal("-23.561684"),
            lng = BigDecimal("-46.655981"),
        )

        val configProvider = object : GarageConfigProviderPort {
            override fun fetchConfiguration(): GarageConfiguration {
                events.add("fetch")
                return GarageConfiguration(sectors = listOf(expectedSector), spots = listOf(expectedSpot))
            }
        }
        val transactionPort = object : TransactionPort {
            override fun <T> executeInTransaction(block: () -> T): T {
                events.add("tx_begin")
                val result = block()
                events.add("tx_commit")
                return result
            }
        }
        val sectorPort = object : SectorRepositoryPort {
            override fun upsertAll(sectors: List<Sector>) {
                events.add("upsert_sectors:${sectors.size}")
            }
            override fun findAllForUpdate(): List<Sector> = emptyList()
            override fun findByCode(code: String): Sector? = null
            override fun findFirst(): Sector? = null
            override fun sumMaxCapacity(): Long = 0L
        }
        val spotPort = object : ParkingSpotRepositoryPort {
            override fun upsertAll(spots: List<ParkingSpot>) {
                events.add("upsert_spots:${spots.size}")
            }
            override fun findAll(): List<ParkingSpot> = emptyList()
            override fun findById(id: Long): ParkingSpot? = null
            override fun findByCoordinates(lat: BigDecimal, lng: BigDecimal): ParkingSpot? = null
            override fun updateOccupied(spotId: Long, occupied: Boolean) = Unit
            override fun countAll(): Long = 0L
        }

        val service = GarageSyncService(configProvider, sectorPort, spotPort, transactionPort, sessions)
        service.synchronize()

        assertEquals(
            listOf("fetch", "tx_begin", "upsert_sectors:1", "upsert_spots:1", "tx_commit"),
            events,
        )
    }

    @Test
    fun `should deserialize garage config JSON with both camelCase and snake_case basePrice`() {
        val objectMapper = ObjectMapper.getDefault()
        val json = """
            {
              "garage": [
                {"sector": "A", "basePrice": 10.0, "max_capacity": 100},
                {"sector": "B", "base_price": 15.5, "maxCapacity": 50}
              ],
              "spots": [
                {"id": 1, "sector": "A", "lat": -23.561684, "lng": -46.655981}
              ]
            }
        """.trimIndent()

        val parsed = objectMapper.readValue(json, GarageConfigResponse::class.java).toDomain()

        assertEquals(2, parsed.sectors.size)
        assertEquals("A", parsed.sectors[0].code)
        assertEquals(0, BigDecimal("10.0").compareTo(parsed.sectors[0].basePrice))
        assertEquals(100, parsed.sectors[0].maxCapacity)
        assertEquals("B", parsed.sectors[1].code)
        assertEquals(0, BigDecimal("15.5").compareTo(parsed.sectors[1].basePrice))
        assertEquals(50, parsed.sectors[1].maxCapacity)
        assertEquals(1, parsed.spots.size)
        assertEquals(1L, parsed.spots[0].id)
    }

    @Test
    fun `should not open transaction when fetching garage configuration fails`() {
        var transactionStarted = false
        val failingProvider = object : GarageConfigProviderPort {
            override fun fetchConfiguration(): GarageConfiguration {
                throw IllegalStateException("Simulator unavailable")
            }
        }
        val transactionPort = object : TransactionPort {
            override fun <T> executeInTransaction(block: () -> T): T {
                transactionStarted = true
                return block()
            }
        }
        val sectorPort = object : SectorRepositoryPort {
            override fun upsertAll(sectors: List<Sector>) = Unit
            override fun findAllForUpdate(): List<Sector> = emptyList()
            override fun findByCode(code: String): Sector? = null
            override fun findFirst(): Sector? = null
            override fun sumMaxCapacity(): Long = 0L
        }
        val spotPort = object : ParkingSpotRepositoryPort {
            override fun upsertAll(spots: List<ParkingSpot>) = Unit
            override fun findAll(): List<ParkingSpot> = emptyList()
            override fun findById(id: Long): ParkingSpot? = null
            override fun findByCoordinates(lat: BigDecimal, lng: BigDecimal): ParkingSpot? = null
            override fun updateOccupied(spotId: Long, occupied: Boolean) = Unit
            override fun countAll(): Long = 0L
        }

        val service = GarageSyncService(failingProvider, sectorPort, spotPort, transactionPort, sessions)

        assertThrows(IllegalStateException::class.java) {
            service.synchronize()
        }
        assertFalse(transactionStarted)
    }
}
