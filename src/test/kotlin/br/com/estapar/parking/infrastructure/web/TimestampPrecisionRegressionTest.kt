package br.com.estapar.parking.infrastructure.web

import br.com.estapar.parking.domain.model.Sector
import br.com.estapar.parking.domain.port.outbound.ParkingSessionRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class TimestampPrecisionRegressionTest {
    @Inject lateinit var server: EmbeddedServer
    @Inject lateinit var sessions: ParkingSessionRepositoryPort
    @Inject lateinit var sectors: SectorRepositoryPort
    @Inject lateinit var sessionJdbc: ParkingSessionJdbcRepository
    @Inject lateinit var spotJdbc: ParkingSpotJdbcRepository
    @Inject lateinit var sectorJdbc: SectorJdbcRepository
    private val http = HttpClient.newHttpClient()

    private fun cleanup() { sessionJdbc.deleteAll(); spotJdbc.deleteAll(); sectorJdbc.deleteAll() }
    @BeforeEach fun setup() { cleanup(); sectors.upsertAll(listOf(Sector("A", BigDecimal.TEN, 2))) }
    @AfterEach fun teardown() { cleanup(); http.close() }

    private fun event(type: String, time: String, plate: String = "PRECISION"): Int {
        val field = if (type == "ENTRY") "entry_time" else "exit_time"
        val json = """{"event_type":"$type","license_plate":"$plate","$field":"$time"}"""
        val request = HttpRequest.newBuilder(URI("http://localhost:${server.port}/webhook"))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    @Test fun `should reject submillisecond ENTRY instead of storing truncated start`() {
        listOf("2025-01-01T12:00:00.000000001Z", "2025-01-01T12:00:00.123456Z").forEach {
            assertEquals(400, event("ENTRY", it))
            assertEquals(0L, sessionJdbc.count())
        }
    }

    @Test fun `should reject submillisecond EXIT without changing active session`() {
        assertEquals(200, event("ENTRY", "2025-01-01T12:00:00.123Z"))
        val before = sessions.findActiveByLicensePlate("PRECISION")!!
        assertEquals(400, event("EXIT", "2025-01-01T12:30:00.123000001Z"))
        assertEquals(before, sessions.findActiveByLicensePlate("PRECISION"))
        assertNull(before.exitTime)
        assertNull(before.chargedAmount)
    }

    @Test fun `should keep exact thirty minutes free and charge first millisecond beyond tolerance`() {
        listOf("2025-01-01T12:30:00.123000000Z" to "0.00", "2025-01-01T12:30:00.124Z" to "9.00").forEachIndexed { i, (end, amount) ->
            val plate = "VISIT$i"
            assertEquals(200, event("ENTRY", "2025-01-01T12:00:00.123000000Z", plate))
            val id = sessions.findActiveByLicensePlate(plate)!!.id!!
            assertEquals(200, event("EXIT", end, plate))
            val saved = sessionJdbc.findById(id).orElseThrow()
            assertEquals(Instant.parse("2025-01-01T12:00:00.123Z"), saved.entryTime)
            assertEquals(Instant.parse(end), saved.exitTime)
            assertEquals(BigDecimal(amount), saved.chargedAmount)
        }
    }
}
