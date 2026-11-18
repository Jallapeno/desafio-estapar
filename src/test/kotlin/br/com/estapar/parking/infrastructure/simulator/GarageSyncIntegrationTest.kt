package br.com.estapar.parking.infrastructure.simulator

import br.com.estapar.parking.application.port.inbound.SyncGarageUseCase
import br.com.estapar.parking.domain.port.outbound.ParkingSpotRepositoryPort
import br.com.estapar.parking.domain.port.outbound.SectorRepositoryPort
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSessionJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.ParkingSpotJdbcRepository
import br.com.estapar.parking.infrastructure.persistence.repository.SectorJdbcRepository
import br.com.estapar.parking.infrastructure.startup.GarageSyncStartupListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GarageSyncIntegrationTest : TestPropertyProvider {

    private val simulatorServer: EmbeddedServer = ApplicationContext.run(
        EmbeddedServer::class.java,
        mapOf(
            "spec.name" to "MockSimulatorServer",
            "micronaut.server.port" to -1,
        ),
    )

    private val mockController: MockSimulatorController =
        simulatorServer.applicationContext.getBean(MockSimulatorController::class.java)

    @Inject
    lateinit var syncGarageUseCase: SyncGarageUseCase

    @Inject
    lateinit var sectorRepositoryPort: SectorRepositoryPort

    @Inject
    lateinit var parkingSpotRepositoryPort: ParkingSpotRepositoryPort

    @Inject
    lateinit var sectorJdbcRepository: SectorJdbcRepository

    @Inject
    lateinit var parkingSpotJdbcRepository: ParkingSpotJdbcRepository

    @Inject
    lateinit var parkingSessionJdbcRepository: ParkingSessionJdbcRepository

    @Inject
    lateinit var applicationContext: ApplicationContext

    override fun getProperties(): Map<String, String> =
        mapOf("simulator.base-url" to "http://localhost:${simulatorServer.port}")

    @BeforeEach
    fun setup() {
        parkingSessionJdbcRepository.deleteAll()
        parkingSpotJdbcRepository.deleteAll()
        sectorJdbcRepository.deleteAll()
        mockController.reset()
    }

    @AfterAll
    fun tearDown() {
        simulatorServer.close()
    }

    @Test
    fun `should synchronize garage sectors and spots from simulator endpoint`() {
        syncGarageUseCase.synchronize()

        val sector = sectorRepositoryPort.findByCode("A")
        val spot = parkingSpotRepositoryPort.findById(1L)

        assertNotNull(sector)
        assertEquals(BigDecimal("10.00"), sector!!.basePrice)
        assertEquals(100, sector.maxCapacity)
        assertNotNull(spot)
        assertEquals("A", spot!!.sectorCode)
        assertFalse(spot.occupied)
    }

    @Test
    fun `CT-18 should synchronize idempotently and preserve spot occupancy`() {
        syncGarageUseCase.synchronize()
        parkingSpotRepositoryPort.updateOccupied(spotId = 1L, occupied = true)

        mockController.basePrice = "12.50"
        syncGarageUseCase.synchronize()

        val updatedSector = sectorRepositoryPort.findByCode("A")
        val spot = parkingSpotRepositoryPort.findById(1L)

        assertEquals(BigDecimal("12.50"), updatedSector!!.basePrice)
        assertEquals(1L, parkingSpotRepositoryPort.countAll())
        assertTrue(spot!!.occupied)
    }

    @Test
    fun `CT-19 should retry transient simulator failures and succeed on third attempt`() {
        mockController.failuresRemaining.set(2)

        syncGarageUseCase.synchronize()

        assertEquals(3, mockController.requestCount.get())
        assertNotNull(sectorRepositoryPort.findByCode("A"))
        assertNotNull(parkingSpotRepositoryPort.findById(1L))
    }

    @Test
    fun `should abort startup when simulator remains unavailable`() {
        mockController.failuresRemaining.set(10)
        val listener = GarageSyncStartupListener(syncGarageUseCase)

        val failure = assertThrows(IllegalStateException::class.java) {
            listener.onApplicationEvent(StartupEvent(applicationContext))
        }
        assertEquals("Initial garage synchronization failed", failure.message)
        assertNotNull(failure.cause)
        assertTrue(mockController.requestCount.get() > 1)
        assertEquals(0L, sectorRepositoryPort.sumMaxCapacity())
    }

    @Singleton
    @Controller("/garage")
    @Requires(property = "spec.name", value = "MockSimulatorServer")
    class MockSimulatorController {
        val failuresRemaining = AtomicInteger(0)
        val requestCount = AtomicInteger(0)
        var basePrice: String = "10.0"

        fun reset() {
            failuresRemaining.set(0)
            requestCount.set(0)
            basePrice = "10.0"
        }

        @Get
        fun getGarage(): HttpResponse<String> {
            requestCount.incrementAndGet()
            if (failuresRemaining.getAndDecrement() > 0) {
                return HttpResponse.serverError("Simulator warming up")
            }
            val payload = """
                {
                  "garage": [
                    {
                      "sector": "A",
                      "basePrice": $basePrice,
                      "max_capacity": 100
                    }
                  ],
                  "spots": [
                    {
                      "id": 1,
                      "sector": "A",
                      "lat": -23.561684,
                      "lng": -46.655981
                    }
                  ]
                }
            """.trimIndent()
            return HttpResponse.ok(payload)
        }
    }
}
