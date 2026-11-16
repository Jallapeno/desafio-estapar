package br.com.estapar.parking.infrastructure.persistence

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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

@MicronautTest(transactional = false)
class ParkingSessionPersistenceIntegrationTest {

    @Inject
    lateinit var sessionRepositoryPort: ParkingSessionRepositoryPort

    @Inject
    lateinit var sectorRepositoryPort: SectorRepositoryPort

    @Inject
    lateinit var spotRepositoryPort: ParkingSpotRepositoryPort

    @Inject
    lateinit var sessionJdbcRepository: ParkingSessionJdbcRepository

    @Inject
    lateinit var spotJdbcRepository: ParkingSpotJdbcRepository

    @Inject
    lateinit var sectorJdbcRepository: SectorJdbcRepository

    @BeforeEach
    @AfterEach
    fun cleanup() {
        sessionJdbcRepository.deleteAll()
        spotJdbcRepository.deleteAll()
        sectorJdbcRepository.deleteAll()
    }

    @Test
    fun `should persist session with millisecond precision and allow multiple visits for same plate`() {
        sectorRepositoryPort.upsertAll(listOf(Sector("A", BigDecimal("10.00"), 10)))
        spotRepositoryPort.upsertAll(
            listOf(ParkingSpot(1L, "A", BigDecimal("-23.561684"), BigDecimal("-46.655981"))),
        )

        val firstEntry = Instant.parse("2025-01-01T10:00:00.345Z")
        val firstExit = Instant.parse("2025-01-01T11:00:00.789Z")

        val created = sessionRepositoryPort.save(
            ParkingSession(
                licensePlate = "ZUL0001",
                status = SessionStatus.ENTERED,
                entryTime = firstEntry,
                occupancyRateAtEntry = BigDecimal("0.0000"),
                dynamicMultiplier = BigDecimal("0.90"),
            ),
        )
        assertNotNull(created.id)
        assertEquals(firstEntry, created.entryTime)
        assertEquals(1L, sessionRepositoryPort.countActiveSessions())

        val parked = sessionRepositoryPort.save(created.park(spotId = 1L, sectorCode = "A"))
        assertEquals(SessionStatus.PARKED, parked.status)
        assertNotNull(sessionRepositoryPort.findActiveByLicensePlate("ZUL0001"))

        val exited = sessionRepositoryPort.save(parked.exit(exitTime = firstExit, chargedAmount = BigDecimal("9.00")))
        assertEquals(SessionStatus.EXITED, exited.status)
        assertEquals(firstExit, exited.exitTime)
        assertEquals(0L, sessionRepositoryPort.countActiveSessions())
        assertNull(sessionRepositoryPort.findActiveByLicensePlate("ZUL0001"))

        // Second visit on same day for the same license plate must be persisted cleanly
        val secondEntry = Instant.parse("2025-01-01T15:00:00.123Z")
        val secondVisit = sessionRepositoryPort.save(
            ParkingSession(
                licensePlate = "ZUL0001",
                status = SessionStatus.ENTERED,
                entryTime = secondEntry,
                occupancyRateAtEntry = BigDecimal("0.1000"),
                dynamicMultiplier = BigDecimal("0.90"),
            ),
        )
        assertNotNull(secondVisit.id)
        assertEquals(1L, sessionRepositoryPort.countActiveSessions())
        assertEquals(2L, sessionJdbcRepository.count())
    }
}
