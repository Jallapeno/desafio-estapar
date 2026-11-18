package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ExitResult
import br.com.estapar.parking.application.port.inbound.ParkedResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessExitUseCase
import br.com.estapar.parking.application.port.inbound.ProcessParkedUseCase
import br.com.estapar.parking.application.port.outbound.TransactionPort
import br.com.estapar.parking.application.service.ExitEventService
import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.domain.service.PricingCalculator
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
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class ExitEventIntegrationTest {
    @Inject lateinit var entries: ProcessEntryUseCase
    @Inject lateinit var parked: ProcessParkedUseCase
    @Inject lateinit var exits: ProcessExitUseCase
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var spots: ParkingSpotRepositoryPort
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var transactions: TransactionPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    @Inject @field:Client("/") lateinit var client: HttpClient

    private val start = Instant.parse("2025-01-01T12:00:00.123Z")
    private val a = ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))
    private val b = ParkingSpot(2L, "B", BigDecimal("-23.561700"), BigDecimal("-46.656000"))

    @BeforeEach
    fun setup() {
        cleanup()
        sectors.upsertAll(listOf(Sector("B", BigDecimal("20.00"), 5), Sector("A", BigDecimal("10.00"), 5)))
        spots.upsertAll(listOf(a, b))
    }

    @AfterEach
    fun cleanup() {
        sessionJdbc.deleteAll()
        spotJdbc.deleteAll()
        sectorJdbc.deleteAll()
    }

    private fun enter(plate: String = "ZUL0001", time: Instant = start) {
        assertEquals(EntryResult.ACCEPTED, entries.enter(plate, time))
    }

    private fun park(plate: String = "ZUL0001", spot: ParkingSpot = a) {
        assertEquals(ParkedResult.PARKED, parked.park(plate, spot.lat, spot.lng))
    }

    private fun saved(id: Long) = sessionJdbc.findById(id).orElseThrow().toDomain()
    private fun active(plate: String = "ZUL0001") = sessions.findActiveByLicensePlate(plate)!!

    private fun webhook(plate: String = "ZUL0001", time: Instant = start.plusSeconds(3600)) = client.toBlocking().exchange(
        HttpRequest.POST("/webhook", """
            {"license_plate":"$plate", "exit_time":"$time", "event_type":"EXIT"}
        """.trimIndent()).contentType(MediaType.APPLICATION_JSON_TYPE), Void::class.java,
    )

    @Test
    fun `should bill parked sector with saved multiplier and close session atomically`() {
        repeat(3) { enter("SEED$it") }
        enter()
        park(spot = b)
        val before = active()
        assertEquals(BigDecimal("1.00"), before.dynamicMultiplier)
        repeat(3) { enter("LATER$it") }
        // Current occupancy is 70 percent; billing must retain the multiplier captured at 30 percent.
        val end = start.plusSeconds(3600)
        assertEquals(HttpStatus.OK, webhook(" zul0001 ", end).status)
        assertEquals(before.exit(end, BigDecimal("20.00")), saved(before.id!!))
        assertNull(sessions.findActiveByLicensePlate("ZUL0001"))
        assertFalse(spots.findById(b.id)!!.occupied)
        assertEquals(6L, sessions.countActiveSessions())
    }

    @Test
    fun `should persist billing correctly at millisecond tolerance and hour boundaries`() {
        listOf("PT30M" to "0.00", "PT30M0.001S" to "9.00", "PT60M0.001S" to "18.00").forEachIndexed { index, (duration, amount) ->
            val plate = "BOUNDARY$index"
            enter(plate)
            park(plate)
            val id = active(plate).id!!
            val end = start.plus(Duration.parse(duration))
            assertEquals(HttpStatus.OK, webhook(plate, end).status)
            assertEquals(BigDecimal(amount), saved(id).chargedAmount)
            assertEquals(end, saved(id).exitTime)
            assertFalse(spots.findById(a.id)!!.occupied)
        }
    }

    @Test
    fun `CT-26 should close unparked session free within tolerance without releasing another vehicle spot`() {
        enter("OWNER")
        park("OWNER", b)
        val owner = active("OWNER")
        enter()
        val id = active().id!!
        assertEquals(HttpStatus.OK, webhook(time = start.plusSeconds(600)).status)
        val closed = saved(id)
        assertEquals(SessionStatus.EXITED, closed.status)
        assertEquals("A", closed.sectorCode)
        assertNull(closed.spotId)
        assertEquals(BigDecimal("0.00"), closed.chargedAmount)
        assertEquals(owner, active("OWNER"))
        assertTrue(spots.findById(b.id)!!.occupied)
        assertEquals(1L, sessions.countActiveSessions())
    }

    @Test
    fun `should bill unparked session using documented first sector fallback after tolerance`() {
        enter()
        val id = active().id!!
        assertEquals(ExitResult.EXITED, exits.exit("ZUL0001", start.plusSeconds(3660)))
        val closed = saved(id)
        assertEquals("A", closed.sectorCode)
        assertEquals(BigDecimal("18.00"), closed.chargedAmount)
        assertNull(closed.spotId)
        assertEquals(0L, sessions.countActiveSessions())
        assertFalse(spots.findById(a.id)!!.occupied)
        assertFalse(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `should acknowledge absent or duplicate EXIT without charging or releasing reused spot`() {
        assertEquals(HttpStatus.OK, webhook("UNKNOWN").status)
        assertEquals(0L, sessionJdbc.count())
        enter()
        park()
        val id = active().id!!
        webhook()
        val closed = saved(id)
        enter("NEWOWNER", start.plusSeconds(7200))
        park("NEWOWNER")
        assertEquals(HttpStatus.OK, webhook().status)
        assertEquals(closed, saved(id))
        assertTrue(spots.findById(a.id)!!.occupied)
        assertEquals(a.id, active("NEWOWNER").spotId)
        assertEquals(1L, sessions.countActiveSessions())
    }

    @Test
    fun `CT-27 should reject invalid exit timestamp without changing session or spot`() {
        enter()
        park()
        val before = active()
        val error = assertThrows(HttpClientResponseException::class.java) { webhook(time = start.minusMillis(1)) }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        assertEquals(before, active())
        assertTrue(spots.findById(a.id)!!.occupied)
    }

    @Test
    fun `should preserve new visit on replay of older EXIT for same plate`() {
        enter()
        park()
        val oldId = active().id!!
        webhook()
        val closed = saved(oldId)
        enter(time = start.plusSeconds(7200))
        park(spot = b)
        val current = active()
        val error = assertThrows(HttpClientResponseException::class.java) { webhook() }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        assertEquals(current, active())
        assertEquals(closed, saved(oldId))
        assertFalse(spots.findById(a.id)!!.occupied)
        assertTrue(spots.findById(b.id)!!.occupied)
    }

    @Test
    fun `CT-21 should allow entry and spot reuse immediately after exit from full garage`() {
        sectors.upsertAll(listOf(Sector("A", BigDecimal.TEN, 1), Sector("B", BigDecimal.TEN, 1)))
        enter()
        park()
        enter("SECOND")
        park("SECOND", b)
        assertEquals(EntryResult.FULL, entries.enter("THIRD", start))
        assertEquals(ExitResult.EXITED, exits.exit("ZUL0001", start.plusSeconds(600)))
        enter("THIRD", start.plusSeconds(601))
        park("THIRD")
        assertEquals(2L, sessions.countActiveSessions())
        assertTrue(spots.findById(a.id)!!.occupied)
    }

    private fun <T> concurrently(tasks: List<() -> T>): List<T> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        val gate = CountDownLatch(1)
        try {
            val futures = tasks.map { task -> executor.submit<T> {
                check(gate.await(10, TimeUnit.SECONDS))
                task()
            } }
            gate.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `should close session only once for ten concurrent duplicate exits`() {
        enter()
        park()
        val id = active().id!!
        val results = concurrently(List(10) { { exits.exit("ZUL0001", start.plusSeconds(3600)) } })
        assertEquals(1, results.count { it == ExitResult.EXITED })
        assertEquals(9, results.count { it == ExitResult.NO_ACTIVE_SESSION })
        assertEquals(BigDecimal("9.00"), saved(id).chargedAmount)
        assertEquals(1L, sessionJdbc.count())
        assertEquals(0L, sessions.countActiveSessions())
        assertFalse(spots.findById(a.id)!!.occupied)
    }

    @Test
    fun `should serialize PARKED against EXIT without leaving an occupied spot or active session`() {
        enter()
        val id = active().id!!
        concurrently(listOf<() -> Any>(
            { exits.exit("ZUL0001", start.plusSeconds(3600)) },
            { parked.park("ZUL0001", a.lat, a.lng) },
        ))
        assertEquals(SessionStatus.EXITED, saved(id).status)
        assertEquals(BigDecimal("9.00"), saved(id).chargedAmount)
        assertFalse(spots.findById(a.id)!!.occupied)
        assertEquals(0L, sessions.countActiveSessions())
    }

    @Test
    fun `should serialize ENTRY against EXIT without exceeding capacity`() {
        sectors.upsertAll(listOf(Sector("A", BigDecimal.TEN, 1), Sector("B", BigDecimal.TEN, 0)))
        enter()
        park()
        val results = concurrently(listOf<() -> Any>(
            { exits.exit("ZUL0001", start.plusSeconds(600)) },
            { entries.enter("NEWOWNER", start.plusSeconds(601)) },
        ))
        assertEquals(ExitResult.EXITED, results[0])
        assertTrue(results[1] == EntryResult.ACCEPTED || results[1] == EntryResult.FULL)
        assertNull(sessions.findActiveByLicensePlate("ZUL0001"))
        assertFalse(spots.findById(a.id)!!.occupied)
        assertTrue(sessions.countActiveSessions() <= 1L)
        if (results[1] == EntryResult.FULL) enter("NEWOWNER", start.plusSeconds(601))
        assertNotNull(sessions.findActiveByLicensePlate("NEWOWNER"))
    }

    @Test
    fun `should roll back released spot and session when persistence fails`() {
        enter()
        park()
        val before = active()
        val failingSessions = object : ParkingSessionRepositoryPort by sessions {
            override fun save(session: ParkingSession): ParkingSession = throw IllegalStateException("Write failed")
        }
        val service = ExitEventService(sectors, spots, failingSessions, transactions, PricingCalculator())
        assertThrows(IllegalStateException::class.java) { service.exit("ZUL0001", start.plusSeconds(3600)) }
        assertTrue(spots.findById(a.id)!!.occupied)
        assertEquals(before, active())
    }
}
