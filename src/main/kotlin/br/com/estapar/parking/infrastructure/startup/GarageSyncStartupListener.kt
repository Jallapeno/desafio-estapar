package br.com.estapar.parking.infrastructure.startup

import br.com.estapar.parking.application.port.inbound.SyncGarageUseCase
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@Requires(property = "simulator.sync-on-startup", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
class GarageSyncStartupListener(
    private val syncGarageUseCase: SyncGarageUseCase,
) : ApplicationEventListener<StartupEvent> {

    private val logger = LoggerFactory.getLogger(GarageSyncStartupListener::class.java)

    override fun onApplicationEvent(event: StartupEvent) {
        try {
            syncGarageUseCase.synchronize()
        } catch (ex: Exception) {
            logger.error(
                "Cannot start application without a synchronized garage configuration",
                ex,
            )
            throw IllegalStateException("Initial garage synchronization failed", ex)
        }
    }
}
