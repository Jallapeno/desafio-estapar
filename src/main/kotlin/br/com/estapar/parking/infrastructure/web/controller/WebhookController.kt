package br.com.estapar.parking.infrastructure.web.controller

import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.infrastructure.web.dto.WebhookEventRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import jakarta.validation.Valid

@Validated
@Controller("/webhook")
open class WebhookController(
    private val processWebhookEventUseCase: ProcessWebhookEventUseCase,
) {

    @Post
    open fun receiveEvent(@Valid @Body request: WebhookEventRequest): HttpResponse<Void> {
        processWebhookEventUseCase.process(request.toCommand())
        return HttpResponse.ok()
    }
}
