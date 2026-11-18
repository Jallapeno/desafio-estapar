package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.EntryResult
import br.com.estapar.parking.application.port.inbound.ProcessEntryUseCase
import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import org.slf4j.LoggerFactory

/**
 * Routes validated webhook events to their respective domain handlers.
 */
class WebhookEventRouterService(
    private val processEntryUseCase: ProcessEntryUseCase,
) : ProcessWebhookEventUseCase {

    private val logger = LoggerFactory.getLogger(WebhookEventRouterService::class.java)

    override fun process(command: WebhookEventCommand) {
        when (command.eventType) {
            WebhookEventType.ENTRY -> {
                val result = processEntryUseCase.enter(command.licensePlate, requireNotNull(command.entryTime))
                if (result == EntryResult.FULL) {
                    logger.info("Rejected ENTRY because garage has no available capacity: plate={}", command.licensePlate)
                }
            }
            WebhookEventType.PARKED -> logger.debug("Received PARKED event for plate={}", command.licensePlate)
            WebhookEventType.EXIT -> logger.debug("Received EXIT event for plate={}", command.licensePlate)
        }
    }
}
