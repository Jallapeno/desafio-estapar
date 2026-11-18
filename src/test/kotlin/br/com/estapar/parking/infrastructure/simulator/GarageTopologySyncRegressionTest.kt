package br.com.estapar.parking.infrastructure.simulator

import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessExitUseCase
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.application.service.GarageSyncService
import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.port.outbound.GarageConfigProviderPort
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class GarageTopologySyncRegressionTest {
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var spots: ParkingSpotRepositoryPort
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var tx: TransactionPort
    @Inject lateinit var entries: ProcessEntryUseCase
    @Inject lateinit var parked: ProcessParkedUseCase
    @Inject lateinit var exits: ProcessExitUseCase
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    private val sector = Sector("A", BigDecimal("10.00"), 2)
    private val spot = ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))
    private val initial = GarageConfiguration(listOf(sector), listOf(spot))
    private val time = Instant.parse("2025-01-01T12:00:00Z")

    private fun sync(config: GarageConfiguration, repository: ParkingSpotRepositoryPort = spots) {
        val provider = object : GarageConfigProviderPort { override fun fetchConfiguration() = config }
        GarageSyncService(provider, sectors, repository, tx, sessions).synchronize()
    }

    private fun cleanup() { sessionJdbc.deleteAll(); spotJdbc.deleteAll(); sectorJdbc.deleteAll() }
    @BeforeEach fun setup() { cleanup(); sync(initial) }
    @AfterEach fun teardown() { cleanup() }

    @Test fun `should reject replacement snapshot before stale capacity or duplicate coordinates can coexist`() {
        entries.enter("HISTORY", time)
        exits.exit("HISTORY", time.plusSeconds(3600))
        val history = sessionJdbc.findAll().toList()
        val replacement = GarageConfiguration(listOf(Sector("B", BigDecimal("20.00"), 1)),
            listOf(spot.copy(id = 2L, sectorCode = "B")))
        assertThrows(IllegalArgumentException::class.java) { sync(replacement) }
        assertEquals(2L, sectors.sumMaxCapacity())
        assertEquals(1L, spots.countAll())
        assertNull(sectors.findByCode("B"))
        assertEquals(1L, spots.findByCoordinates(spot.lat, spot.lng)!!.id)
        assertEquals("A", spots.findByCoordinates(spot.lat, spot.lng)!!.sectorCode)
        assertEquals(history, sessionJdbc.findAll().toList())
    }

    @Test fun `should reject removed spot without partially updating sector metadata`() {
        val replacement = GarageConfiguration(listOf(sector.copy(basePrice = BigDecimal("99.00"), maxCapacity = 10)),
            listOf(spot.copy(id = 2L)))
        assertThrows(IllegalArgumentException::class.java) { sync(replacement) }
        assertEquals(sector, sectors.findByCode("A"))
        assertEquals(spot, spots.findById(1L))
        assertNull(spots.findById(2L))
    }

    @Test fun `should reject moving occupied spot coordinates or sector and preserve active session`() {
        entries.enter("OWNER", time)
        parked.park("OWNER", spot.lat, spot.lng)
        val owner = sessions.findActiveByLicensePlate("OWNER")!!
        listOf(spot.copy(lat = BigDecimal.ZERO), spot.copy(sectorCode = "B")).forEach { changed ->
            val incoming = GarageConfiguration(listOf(sector, Sector("B", BigDecimal.TEN, 1)), listOf(changed))
            assertThrows(IllegalArgumentException::class.java) { sync(incoming) }
            assertEquals(owner, sessions.findActiveByLicensePlate("OWNER"))
            assertEquals(spot.copy(occupied = true), spots.findById(1L))
            assertNull(sectors.findByCode("B"))
        }
    }

    @Test fun `should allow additions and equivalent coordinates while preserving active occupancy`() {
        entries.enter("OWNER", time)
        parked.park("OWNER", spot.lat, spot.lng)
        val owner = sessions.findActiveByLicensePlate("OWNER")!!
        val incoming = GarageConfiguration(
            listOf(sector.copy(basePrice = BigDecimal("12.50"), maxCapacity = 1), Sector("B", BigDecimal("20.00"), 2)),
            listOf(spot.copy(lat = BigDecimal("-23.5616840")),
                spot.copy(id = 2L, sectorCode = "B", lat = BigDecimal("-23.561685"))),
        )
        sync(incoming)
        sync(incoming)
        assertEquals(3L, sectors.sumMaxCapacity())
        assertEquals(BigDecimal("12.50"), sectors.findByCode("A")!!.basePrice)
        assertEquals(2L, spots.countAll())
        assertTrue(spots.findById(1L)!!.occupied)
        assertFalse(spots.findById(2L)!!.occupied)
        assertEquals(owner, sessions.findActiveByLicensePlate("OWNER"))
    }

    @Test fun `should reject capacity below active count and allow exactly that count`() {
        entries.enter("ONE", time)
        entries.enter("TWO", time)
        assertThrows(IllegalArgumentException::class.java) {
            sync(GarageConfiguration(listOf(sector.copy(maxCapacity = 1)), listOf(spot)))
        }
        assertEquals(2L, sectors.sumMaxCapacity())
        sync(initial)
        assertEquals(2L, sessions.countActiveSessions())
    }

    @Test fun `should roll back sector changes if spot persistence fails after validation`() {
        val failingSpots = object : ParkingSpotRepositoryPort by spots {
            override fun upsertAll(spots: List<ParkingSpot>) { throw IllegalStateException("Write failed") }
        }
        val incoming = GarageConfiguration(listOf(sector.copy(basePrice = BigDecimal("99.00"))), listOf(spot))
        assertThrows(IllegalStateException::class.java) { sync(incoming, failingSpots) }
        assertEquals(sector, sectors.findByCode("A"))
        assertEquals(spot, spots.findById(1L))
    }
}
