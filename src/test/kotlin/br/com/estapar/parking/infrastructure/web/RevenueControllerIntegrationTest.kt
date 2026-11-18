package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.domain.model.ParkingSession
import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.model.SessionStatus
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
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
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class RevenueControllerIntegrationTest {
    @Inject lateinit var server: EmbeddedServer
    @Inject lateinit var mapper: ObjectMapper
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    private val http = HttpClient.newHttpClient()
    private val now = Instant.parse("2025-01-02T01:00:00Z")

    @MockBean(Clock::class)
    fun fixedClock(): Clock = Clock.fixed(now, ZoneId.of("America/Sao_Paulo"))

    @BeforeEach
    fun setup() {
        cleanup()
        sectors.upsertAll(listOf(Sector("A", BigDecimal.TEN, 10), Sector("B", BigDecimal.TEN, 10)))
    }

    @AfterEach
    fun tearDown() {
        cleanup()
        http.close()
    }

    fun cleanup() {
        sessionJdbc.deleteAll()
        spotJdbc.deleteAll()
        sectorJdbc.deleteAll()
    }

    private fun seed(time: String, amount: String, sector: String = "A", status: SessionStatus = SessionStatus.EXITED) {
        val exit = Instant.parse(time)
        sessions.save(ParkingSession(
            licensePlate = "REPEATVISIT",
            status = status,
            entryTime = exit.minusSeconds(3600),
            exitTime = exit,
            sectorCode = sector,
            occupancyRateAtEntry = BigDecimal("0.3000"),
            dynamicMultiplier = BigDecimal.ONE,
            chargedAmount = BigDecimal(amount),
        ))
    }

    // JDK client deliberately sends real GET bodies; Micronaut's client disallows them by default.
    private fun request(path: String = "/revenue", json: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${server.port}$path"))
        if (json != null) builder.header("Content-Type", "application/json")
        return http.send(builder.method("GET", json?.let(HttpRequest.BodyPublishers::ofString)
            ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun result(path: String = "/revenue", json: String? = null): RevenueResponse {
        val response = request(path, json)
        assertEquals(200, response.statusCode(), response.body())
        val parsed = mapper.readValue(response.body(), RevenueResponse::class.java)
        assertEquals("BRL", parsed.currency)
        assertEquals(now, parsed.timestamp)
        return parsed
    }

    @Test
    fun `CT-30 should accept JSON body and query parameters with identical revenue`() {
        seed("2025-01-01T10:00:00Z", "10.00")
        seed("2025-01-01T15:00:00Z", "22.50")
        seed("2025-01-01T15:00:00Z", "15.00", "B")
        val body = result(json = """{"date":"2025-01-01","sector":"A"}""")
        val query = result("/revenue?date=2025-01-01&sector=A")
        assertEquals(BigDecimal("32.50"), body.amount)
        assertEquals(body, query)
    }

    @Test
    fun `CT-29 should use inclusive UTC midnight and exclusive next midnight`() {
        seed("2024-12-31T23:59:59.999Z", "99.00")
        seed("2025-01-01T00:00:00Z", "2.00")
        seed("2025-01-01T01:30:00Z", "12.50")
        seed("2025-01-01T23:59:59.999Z", "3.00")
        seed("2025-01-02T00:00:00Z", "77.00")
        assertEquals(BigDecimal("17.50"), result("/revenue?sector=A&date=2025-01-01").amount)
    }

    @Test
    fun `should default to UTC current date even when clock zone is previous day`() {
        seed("2025-01-01T23:59:59Z", "99.00")
        seed("2025-01-02T00:30:00Z", "15.00")
        assertEquals(BigDecimal("15.00"), result("/revenue?sector=A").amount)
        assertEquals(BigDecimal("15.00"), result(json = """{"sector":"A"}""").amount)
    }

    @Test
    fun `should return two decimal zero for sector or date without revenue`() {
        assertEquals(BigDecimal("0.00"), result("/revenue?sector=A&date=2025-01-01").amount)
        assertEquals(BigDecimal("0.00"), result("/revenue?sector=UNKNOWN&date=2025-01-01").amount)
    }

    @Test
    fun `should sum only closed sessions and retain decimal precision`() {
        seed("2025-01-01T12:00:00Z", "0.10")
        seed("2025-01-01T13:00:00Z", "0.20")
        // Non-finalized records must never contribute, even if a charge field is populated.
        seed("2025-01-01T14:00:00Z", "99.00", status = SessionStatus.ENTERED)
        seed("2025-01-01T14:00:00Z", "99.00", status = SessionStatus.PARKED)
        assertEquals(BigDecimal("0.30"), result("/revenue?sector=A&date=2025-01-01").amount)
    }

    @Test
    fun `should include revenue at response timestamp and exclude future exits`() {
        seed("2025-01-02T01:00:00Z", "10.00")
        seed("2025-01-02T01:00:00.001Z", "90.00")
        seed("2025-01-03T00:00:00Z", "90.00")
        assertEquals(BigDecimal("10.00"), result("/revenue?sector=A").amount)
        assertEquals(BigDecimal("0.00"), result("/revenue?sector=A&date=2025-01-03").amount)
    }

    @Test
    fun `should accept agreeing or complementary filters in query and body`() {
        seed("2025-01-01T12:00:00Z", "10.00")
        assertEquals(BigDecimal("10.00"), result("/revenue?sector=A", """{"date":"2025-01-01"}""").amount)
        assertEquals(BigDecimal("10.00"), result("/revenue?sector=A&date=2025-01-01",
            """{"date":"2025-01-01","sector":" A "}""").amount)
    }

    @Test
    fun `should reject missing blank oversized invalid or conflicting filters`() {
        val invalid = listOf(
            "/revenue" to null,
            "/revenue?sector=" to null,
            "/revenue?sector=${"A".repeat(21)}" to null,
            "/revenue?sector=A&date=invalid" to null,
            "/revenue?sector=A&date=2025-02-30" to null,
            "/revenue?sector=A&date=0999-01-01" to null,
            "/revenue?sector=A" to "{",
            "/revenue?sector=A" to """{"sector":"B"}""",
            "/revenue?sector=A&date=2025-01-01" to """{"date":"2025-01-02"}""",
            "/revenue" to """{"sector":"  "}""",
        )
        invalid.forEach { (path, body) ->
            val response = request(path, body)
            assertEquals(400, response.statusCode(), "$path $body: ${response.body()}")
        }
        assertTrue(sessionJdbc.count() == 0L)
    }
}
