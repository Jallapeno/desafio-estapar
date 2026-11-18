package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import br.com.estapar.parking.infrastructure.web.dto.RevenueResponse
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Fluxos HTTP reais com MySQL/Testcontainers, sem substituir os casos de uso. */
@MicronautTest(transactional = false)
class ParkingLifecycleE2ETest {
    @Inject lateinit var server: EmbeddedServer
    @Inject lateinit var mapper: ObjectMapper
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var spots: ParkingSpotRepositoryPort
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    private val http = HttpClient.newHttpClient()
    private val now = Instant.parse("2025-01-03T12:00:00Z")
    private val morning = Instant.parse("2025-01-01T10:00:00Z")

    @MockBean(Clock::class)
    fun fixedClock(): Clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeEach
    fun setup() {
        cleanup()
        sectors.upsertAll(listOf(Sector("A", BigDecimal("10.00"), 4), Sector("B", BigDecimal("20.00"), 4)))
        spots.upsertAll((1L..4L).map {
            ParkingSpot(it, "A", BigDecimal("-23.561684").add(BigDecimal.valueOf(it, 6)), BigDecimal("-46.655981"))
        } + ParkingSpot(5L, "B", BigDecimal("-23.570000"), BigDecimal("-46.660000")))
    }

    private fun cleanup() {
        sessionJdbc.deleteAll()
        spotJdbc.deleteAll()
        sectorJdbc.deleteAll()
    }

    @AfterEach
    fun teardown() {
        cleanup()
        http.close()
    }

    private fun request(method: String, path: String, json: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${server.port}$path"))
            .timeout(Duration.ofSeconds(20))
        if (json != null) builder.header("Content-Type", "application/json")
        return http.send(builder.method(method, json?.let(HttpRequest.BodyPublishers::ofString)
            ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun event(json: String) {
        val response = request("POST", "/webhook", json)
        assertEquals(200, response.statusCode(), response.body())
    }

    private fun entry(plate: String, time: Instant = morning) = event(
        """{"event_type":"ENTRY","license_plate":"$plate","entry_time":"$time"}""",
    )

    private fun parked(plate: String, spotId: Long) {
        val spot = spots.findById(spotId)!!
        event("""{"event_type":"PARKED","license_plate":"$plate","lat":${spot.lat},"lng":${spot.lng}}""")
    }

    private fun exit(plate: String, time: Instant) = event(
        """{"event_type":"EXIT","license_plate":"$plate","exit_time":"$time"}""",
    )

    private fun revenue(sector: String, date: String = "2025-01-01", body: Boolean = false): BigDecimal {
        val response = if (body) request("GET", "/revenue", """{"sector":"$sector","date":"$date"}""")
            else request("GET", "/revenue?sector=$sector&date=$date")
        assertEquals(200, response.statusCode(), response.body())
        val value = mapper.readValue(response.body(), RevenueResponse::class.java)
        assertEquals("BRL", value.currency)
        assertEquals(now, value.timestamp)
        return value.amount
    }

    @Test
    fun `should execute full lifecycle and preserve discounted charge in both revenue formats`() {
        entry("ABC1234")
        parked("ABC1234", 1L)
        val id = sessions.findActiveByLicensePlate("ABC1234")!!.id!!
        exit("ABC1234", morning.plusSeconds(4500))
        assertEquals(BigDecimal("18.00"), revenue("A"))
        assertEquals(revenue("A"), revenue("A", body = true))
        val saved = sessionJdbc.findById(id).orElseThrow()
        assertEquals(SessionStatus.EXITED, saved.status)
        assertEquals(BigDecimal("18.00"), saved.chargedAmount)
        assertFalse(spots.findById(1L)!!.occupied)
        assertEquals(0L, sessions.countActiveSessions())
    }

    @Test
    fun `should distinguish free 30 minutes from first paid second over HTTP`() {
        entry("FREE")
        parked("FREE", 1L)
        exit("FREE", morning.plusSeconds(1800))
        assertEquals(BigDecimal("0.00"), revenue("A"))
        entry("PAID", morning.plusSeconds(3600))
        parked("PAID", 1L)
        exit("PAID", morning.plusSeconds(5401))
        assertEquals(BigDecimal("9.00"), revenue("A"))
    }

    @Test
    fun `CT-25 should accumulate two complete visits by same plate and isolate sector revenue`() {
        entry("RETURN")
        parked("RETURN", 1L)
        exit("RETURN", morning.plusSeconds(3600))
        entry("RETURN", morning.plusSeconds(14400))
        parked("RETURN", 1L)
        exit("RETURN", morning.plusSeconds(18900))
        entry("OTHER", morning.plusSeconds(21600))
        parked("OTHER", 5L)
        exit("OTHER", morning.plusSeconds(25200))
        assertEquals(BigDecimal("27.00"), revenue("A"))
        assertEquals(BigDecimal("18.00"), revenue("B"))
        assertEquals(3L, sessionJdbc.count())
        assertEquals(0L, sessions.countActiveSessions())
    }

    @Test
    fun `CT-20 CT-21 CT-22 should admit one of ten HTTP entrants then reopen after EXIT`() {
        sectors.upsertAll(listOf(Sector("A", BigDecimal.TEN, 2), Sector("B", BigDecimal.TEN, 0)))
        entry("FIRST")
        parked("FIRST", 1L)
        val executor = Executors.newFixedThreadPool(10)
        val ready = CountDownLatch(10)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until 10).map { index -> executor.submit {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                entry("RACE$index")
            } }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        val admitted = (0 until 10).map { "RACE$it" }.filter { sessions.findActiveByLicensePlate(it) != null }
        assertEquals(1, admitted.size)
        assertEquals(2L, sessions.countActiveSessions())
        entry("FULL")
        assertNull(sessions.findActiveByLicensePlate("FULL"))
        exit("FIRST", morning.plusSeconds(600))
        entry("FULL", morning.plusSeconds(601))
        parked("FULL", 1L)
        assertTrue(spots.findById(1L)!!.occupied)
        assertEquals(2L, sessions.countActiveSessions())
    }

    @Test
    fun `CT-23 CT-24 should keep webhook repeats idempotent and preserve charged revenue`() {
        entry("REPEAT")
        entry("REPEAT")
        parked("REPEAT", 1L)
        parked("REPEAT", 1L)
        exit("REPEAT", morning.plusSeconds(3600))
        exit("REPEAT", morning.plusSeconds(3600))
        assertEquals(1L, sessionJdbc.count())
        assertEquals(BigDecimal("9.00"), revenue("A"))
        assertFalse(spots.findById(1L)!!.occupied)
    }

    @Test
    fun `CT-29 should attribute crossing midnight stay to exit UTC date`() {
        val start = Instant.parse("2025-01-01T23:30:00Z")
        entry("NIGHT", start)
        parked("NIGHT", 1L)
        exit("NIGHT", Instant.parse("2025-01-02T01:30:00Z"))
        assertEquals(BigDecimal("0.00"), revenue("A", "2025-01-01"))
        assertEquals(BigDecimal("18.00"), revenue("A", "2025-01-02"))
    }
}
