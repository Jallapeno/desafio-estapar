package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessExitUseCase
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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class EntryEventIntegrationTest {
    @Inject lateinit var entries: ProcessEntryUseCase
    @Inject lateinit var exits: ProcessExitUseCase
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var spots: ParkingSpotRepositoryPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    @Inject @field:Client("/") lateinit var client: HttpClient

    private val entryTime = Instant.parse("2025-01-01T12:00:00.123Z")

    @BeforeEach
    @AfterEach
    fun cleanup() {
        sessionJdbc.deleteAll()
        spotJdbc.deleteAll()
        sectorJdbc.deleteAll()
    }

    private fun capacity(value: Int) {
        sectors.upsertAll(listOf(Sector("A", BigDecimal("10.00"), value)))
    }

    private fun webhook(plate: String) = client.toBlocking().exchange(
        HttpRequest.POST("/webhook", """
            {"license_plate":"$plate", "entry_time":"$entryTime", "event_type":"ENTRY"}
        """.trimIndent()).contentType(MediaType.APPLICATION_JSON_TYPE), Void::class.java,
    )

    @Test
    fun `should reserve global capacity with entry snapshot and acknowledge full garage without insertion`() {
        capacity(1)
        sectors.upsertAll(listOf(Sector("B", BigDecimal("20.00"), 1)))
        assertEquals(HttpStatus.OK, webhook(" zul0001 ").status)
        val first = sessions.findActiveByLicensePlate("ZUL0001")!!
        assertEquals(SessionStatus.ENTERED, first.status)
        assertEquals(entryTime, first.entryTime)
        assertEquals(BigDecimal("0.0000"), first.occupancyRateAtEntry)
        assertEquals(BigDecimal("0.90"), first.dynamicMultiplier)
        assertNull(first.spotId)
        assertNull(first.sectorCode)

        assertEquals(HttpStatus.OK, webhook("ZUL0002").status)
        val second = sessions.findActiveByLicensePlate("ZUL0002")!!
        assertEquals(BigDecimal("0.5000"), second.occupancyRateAtEntry)
        assertEquals(BigDecimal("1.00"), second.dynamicMultiplier)
        assertEquals(HttpStatus.OK, webhook("FULL9999").status)
        assertNull(sessions.findActiveByLicensePlate("FULL9999"))
        assertEquals(HttpStatus.OK, webhook("ZUL0001").status)
        assertEquals(2L, sessions.countActiveSessions())
        assertEquals(first, sessions.findActiveByLicensePlate("ZUL0001"))
    }

    @Test
    fun `should refuse entry without configured or positive capacity`() {
        assertEquals(EntryResult.FULL, entries.enter("ZUL0001", entryTime))
        capacity(0)
        assertEquals(EntryResult.FULL, entries.enter("ZUL0001", entryTime))
        assertEquals(0L, sessionJdbc.count())
    }

    @Test
    fun `should allow new visit after closed session while preserving history`() {
        capacity(1)
        entries.enter("ZUL0001", entryTime)
        val first = sessions.findActiveByLicensePlate("ZUL0001")!!
        // Closed history is a fixture; EXIT processing belongs to US-08.
        sessions.save(first.exit(entryTime.plusSeconds(600), BigDecimal.ZERO))
        assertEquals(EntryResult.ACCEPTED, entries.enter("ZUL0001", entryTime.plusSeconds(7200)))
        assertEquals(2L, sessionJdbc.count())
        assertEquals(1L, sessions.countActiveSessions())
        assertNotEquals(first.id, sessions.findActiveByLicensePlate("ZUL0001")!!.id)
    }

    @Test
    fun `should acknowledge duplicate entry for parked vehicle even when garage is full`() {
        capacity(1)
        entries.enter("ZUL0001", entryTime)
        spots.upsertAll(listOf(ParkingSpot(1L, "A", BigDecimal.ZERO, BigDecimal.ZERO)))
        val parked = sessions.save(sessions.findActiveByLicensePlate("ZUL0001")!!.park(1L, "A"))

        assertEquals(EntryResult.ALREADY_ACTIVE, entries.enter("ZUL0001", entryTime.plusSeconds(60)))
        assertEquals(parked, sessions.findActiveByLicensePlate("ZUL0001"))
        assertEquals(1L, sessionJdbc.count())
    }

    private fun concurrently(plates: List<String>): List<EntryResult> {
        val executor = Executors.newFixedThreadPool(plates.size)
        val start = CountDownLatch(1)
        try {
            val futures = plates.map { plate ->
                executor.submit<EntryResult> {
                    check(start.await(10, TimeUnit.SECONDS))
                    entries.enter(plate, entryTime)
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
    fun `CT-22 should accept exactly one of ten entries competing for last vacancy`() {
        capacity(10)
        repeat(9) { assertEquals(EntryResult.ACCEPTED, entries.enter("SEED$it", entryTime)) }
        val results = concurrently((0 until 10).map { "RACE$it" })
        assertEquals(1, results.count { it == EntryResult.ACCEPTED })
        assertEquals(9, results.count { it == EntryResult.FULL })
        assertEquals(10L, sessions.countActiveSessions())
        val winner = sessionJdbc.findAll().single { it.licensePlate.startsWith("RACE") }
        assertEquals(BigDecimal("0.9000"), winner.occupancyRateAtEntry)
        assertEquals(BigDecimal("1.25"), winner.dynamicMultiplier)
    }

    @Test
    fun `should create only one active session for ten simultaneous duplicate entries`() {
        capacity(10)
        val results = concurrently(List(10) { "ZUL0001" })
        assertEquals(1, results.count { it == EntryResult.ACCEPTED })
        assertEquals(9, results.count { it == EntryResult.ALREADY_ACTIVE })
        assertEquals(1L, sessionJdbc.count())
    }
    @Test
    fun `should acknowledge historic ENTRY replay over HTTP without phantom capacity and allow a new visit`() {
        capacity(1)
        webhook("ZUL0001")
        val firstId = sessions.findActiveByLicensePlate("ZUL0001")!!.id!!
        exits.exit("ZUL0001", entryTime.plusSeconds(3600))
        val closed = sessionJdbc.findById(firstId).orElseThrow()
        assertEquals(HttpStatus.OK, webhook("ZUL0001").status)
        assertEquals(EntryResult.ALREADY_PROCESSED, entries.enter("ZUL0001", entryTime))
        assertEquals(1L, sessionJdbc.count())
        assertEquals(0L, sessions.countActiveSessions())
        assertEquals(closed, sessionJdbc.findById(firstId).orElseThrow())
        assertEquals(EntryResult.ACCEPTED, entries.enter("ZUL0001", entryTime.plusSeconds(7200)))
        assertEquals(2L, sessionJdbc.count())
    }

    @Test
    fun `should keep ten simultaneous historic ENTRY replays idempotent`() {
        capacity(1)
        entries.enter("ZUL0001", entryTime)
        exits.exit("ZUL0001", entryTime.plusSeconds(3600))
        assertEquals(List(10) { EntryResult.ALREADY_PROCESSED }, concurrently(List(10) { "ZUL0001" }))
        assertEquals(1L, sessionJdbc.count())
        assertEquals(0L, sessions.countActiveSessions())
    }

    @Test
    fun `should deduplicate historic entry even when another vehicle fills capacity`() {
        capacity(1)
        entries.enter("OLD", entryTime)
        exits.exit("OLD", entryTime.plusSeconds(3600))
        entries.enter("CURRENT", entryTime.plusSeconds(7200))
        val current = sessions.findActiveByLicensePlate("CURRENT")!!
        assertEquals(EntryResult.ALREADY_PROCESSED, entries.enter("OLD", entryTime))
        assertEquals(current, sessions.findActiveByLicensePlate("CURRENT"))
        assertEquals(1L, sessions.countActiveSessions())
    }

}
