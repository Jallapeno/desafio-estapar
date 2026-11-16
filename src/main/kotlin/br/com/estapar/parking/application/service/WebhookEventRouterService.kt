package br.com.estapar.parking.application.service

import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.application.port.inbound.WebhookEventCommand
import br.com.estapar.parking.application.port.inbound.WebhookEventType
import org.slf4j.LoggerFactory

/**
 * Routes validated webhook events to their respective domain handlers.
 */
class WebhookEventRouterService : ProcessWebhookEventUseCase {

    private val logger = LoggerFactory.getLogger(WebhookEventRouterService::class.java)

    override fun process(command: WebhookEventCommand) {
        when (command.eventType) {
            WebhookEventType.ENTRY -> logger.debug("Received ENTRY event for plate={}", command.licensePlate)
            WebhookEventType.PARKED -> logger.debug("Received PARKED event for plate={}", command.licensePlate)
            WebhookEventType.EXIT -> logger.debug("Received EXIT event for plate={}", command.licensePlate)
        }
    }
}
