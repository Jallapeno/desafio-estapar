package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ParkedResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.application.service.ParkedEventService
import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@MicronautTest(transactional = false)
class ParkedEventIntegrationTest {
    @Inject lateinit var entries: ProcessEntryUseCase
    @Inject lateinit var parked: ProcessParkedUseCase
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var spots: ParkingSpotRepositoryPort
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var transactions: TransactionPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    @Inject @field:Client("/") lateinit var client: HttpClient

    private val entryTime = Instant.parse("2025-01-01T12:00:00Z")
    private val a = ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))
    private val b = ParkingSpot(2L, "B", BigDecimal("-23.561700"), BigDecimal("-46.656000"))

    @BeforeEach
    fun setup() {
        cleanup()
        sectors.upsertAll(listOf(Sector("A", BigDecimal("10.00"), 5), Sector("B", BigDecimal("20.00"), 5)))
        spots.upsertAll(listOf(a, b))
    }

    @AfterEach
    fun cleanup() {
        sessionJdbc.deleteAll()
        spotJdbc.deleteAll()
        sectorJdbc.deleteAll()
    }

    private fun enter(plate: String = "ZUL0001") {
        assertEquals(EntryResult.ACCEPTED, entries.enter(plate, entryTime))
    }

    private fun park(plate: String = "ZUL0001", spot: ParkingSpot = a) = parked.park(plate, spot.lat, spot.lng)

    private fun webhook(plate: String, lat: BigDecimal, lng: BigDecimal) = client.toBlocking().exchange(
        HttpRequest.POST("/webhook", """
            {"license_plate":"$plate", "lat":$lat, "lng":$lng, "event_type":"PARKED"}
        """.trimIndent()).contentType(MediaType.APPLICATION_JSON_TYPE), Void::class.java,
    )

    @Test
    fun `should park by exact coordinates and preserve entry pricing and global occupancy`() {
        enter()
        val before = sessions.findActiveByLicensePlate("ZUL0001")!!
        assertEquals(HttpStatus.OK, webhook(" zul0001 ", b.lat, b.lng).status)
        val after = sessions.findActiveByLicensePlate("ZUL0001")!!
        assertEquals(before.park(b.id, b.sectorCode), after)
        assertTrue(spots.findById(b.id)!!.occupied)
        assertFalse(spots.findById(a.id)!!.occupied)
        assertEquals(1L, sessions.countActiveSessions())
    }

    @Test
    fun `should reject nearby and distant unknown coordinates without allocating another sector`() {
        enter()
        val before = sessions.findActiveByLicensePlate("ZUL0001")!!
        listOf(BigDecimal("-23.561685") to BigDecimal("-46.655982"), BigDecimal.ZERO to BigDecimal.ZERO).forEach { (lat, lng) ->
            assertEquals(ParkedResult.UNKNOWN_SPOT, parked.park("ZUL0001", lat, lng))
            assertEquals(HttpStatus.OK, webhook("ZUL0001", lat, lng).status)
        }
        assertEquals(before, sessions.findActiveByLicensePlate("ZUL0001"))
        assertFalse(spots.findById(a.id)!!.occupied)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should refuse occupied spot even when another sector has an available spot`() {
        enter("OWNER")
        enter("OTHER")
        assertEquals(ParkedResult.PARKED, park("OWNER"))
        val other = sessions.findActiveByLicensePlate("OTHER")!!
        assertEquals(ParkedResult.SPOT_OCCUPIED, park("OTHER"))
        assertEquals(HttpStatus.OK, webhook("OTHER", a.lat, a.lng).status)
        assertEquals(other, sessions.findActiveByLicensePlate("OTHER"))
        assertEquals(a.id, sessions.findActiveByLicensePlate("OWNER")!!.spotId)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should accept exact duplicate with trailing zeroes but reject moving a parked session`() {
        enter()
        assertEquals(ParkedResult.PARKED, park())
        val before = sessions.findActiveByLicensePlate("ZUL0001")!!
        assertEquals(ParkedResult.ALREADY_PARKED, parked.park("ZUL0001", BigDecimal("-23.5616840"), a.lng))
        assertEquals(ParkedResult.DIFFERENT_SPOT, park(spot = b))
        assertEquals(before, sessions.findActiveByLicensePlate("ZUL0001"))
        assertTrue(spots.findById(a.id)!!.occupied)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should not allocate a spot for absent or closed session`() {
        assertEquals(ParkedResult.NO_ACTIVE_SESSION, park())
        enter()
        val closed = sessions.findActiveByLicensePlate("ZUL0001")!!.exit(entryTime.plusSeconds(600), BigDecimal.ZERO)
        sessions.save(closed)
        assertEquals(ParkedResult.NO_ACTIVE_SESSION, park())
        assertEquals(HttpStatus.OK, webhook("ZUL0001", a.lat, a.lng).status)
        assertEquals(closed.copy(chargedAmount = BigDecimal("0.00")), sessionJdbc.findById(closed.id!!).orElseThrow().toDomain())
        assertFalse(spots.findById(a.id)!!.occupied)
    }

    @Test
    fun `should reject invalid ranges and coordinates requiring rounding with HTTP 400`() {
        enter()
        listOf(BigDecimal("90.000001") to a.lng, a.lat to BigDecimal("-180.000001"),
            BigDecimal("-23.5616841") to a.lng, a.lat to BigDecimal("-46.6559811")).forEach { (lat, lng) ->
            val error = assertThrows(HttpClientResponseException::class.java) { webhook("ZUL0001", lat, lng) }
            assertEquals(HttpStatus.BAD_REQUEST, error.status)
        }
        assertEquals(SessionStatus.ENTERED, sessions.findActiveByLicensePlate("ZUL0001")!!.status)
        assertFalse(spots.findById(a.id)!!.occupied)
    }

    private fun concurrently(requests: List<Pair<String, ParkingSpot>>): List<ParkedResult> {
        val executor = Executors.newFixedThreadPool(requests.size)
        val start = CountDownLatch(1)
        try {
            val futures = requests.map { (plate, spot) ->
                executor.submit<ParkedResult> {
                    check(start.await(10, TimeUnit.SECONDS))
                    park(plate, spot)
                }
            }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `should allow only one of ten vehicles to occupy the same spot`() {
        repeat(10) { enter("RACE$it") }
        val results = concurrently((0 until 10).map { "RACE$it" to a })
        assertEquals(1, results.count { it == ParkedResult.PARKED })
        assertEquals(9, results.count { it == ParkedResult.SPOT_OCCUPIED })
        assertEquals(1, sessionJdbc.findAll().count { it.status == SessionStatus.PARKED })
        assertEquals(9, sessionJdbc.findAll().count { it.status == SessionStatus.ENTERED })
        assertTrue(spots.findById(a.id)!!.occupied)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should handle ten simultaneous duplicate PARKED events idempotently`() {
        enter()
        val results = concurrently(List(10) { "ZUL0001" to a })
        assertEquals(1, results.count { it == ParkedResult.PARKED })
        assertEquals(9, results.count { it == ParkedResult.ALREADY_PARKED })
        assertEquals(1L, sessionJdbc.count())
        assertTrue(spots.findById(a.id)!!.occupied)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should prevent simultaneous claims of different spots by same vehicle`() {
        enter()
        val results = concurrently(listOf("ZUL0001" to a, "ZUL0001" to b))
        assertEquals(1, results.count { it == ParkedResult.PARKED })
        assertEquals(1, results.count { it == ParkedResult.DIFFERENT_SPOT })
        assertEquals(1, listOf(a, b).count { spots.findById(it.id)!!.occupied })
        val selected = sessions.findActiveByLicensePlate("ZUL0001")!!.spotId!!
        assertTrue(spots.findById(selected)!!.occupied)
    }

    @Test
    fun `should roll back spot occupancy if updating session fails`() {
        enter()
        val before = sessions.findActiveByLicensePlate("ZUL0001")!!
        val failingSessions = object : ParkingSessionRepositoryPort by sessions {
            override fun save(session: ParkingSession): ParkingSession = throw IllegalStateException("Write failed")
        }
        val failingService = ParkedEventService(sectors, spots, failingSessions, transactions)
        assertThrows(IllegalStateException::class.java) { failingService.park("ZUL0001", a.lat, a.lng) }
        assertFalse(spots.findById(a.id)!!.occupied)
        assertEquals(before, sessions.findActiveByLicensePlate("ZUL0001"))
    }
}
