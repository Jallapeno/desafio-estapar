package br.com.estapar.parking.infrastructure.web.controller

import br.com.estapar.parking.application.port.inbound.ProcessWebhookEventUseCase
import br.com.estapar.parking.infrastructure.web.dto.WebhookEventRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody

@Validated
@Controller("/webhook")
@ExecuteOn(TaskExecutors.BLOCKING)
open class WebhookController(
    private val processWebhookEventUseCase: ProcessWebhookEventUseCase,
) {

    @Operation(
        summary = "Processar evento de estacionamento",
        description = "ENTRY reserva capacidade; PARKED associa a vaga por coordenadas exatas; EXIT encerra a sessão e calcula a cobrança. ENTRY exige entry_time, PARKED exige lat/lng, EXIT exige exit_time. Timestamps devem ter precisão máxima de milissegundos. HTTP 200 também reconhece duplicatas e conflitos de negócio, sem alterar o estado nesses casos.",
        tags = ["Eventos"],
        requestBody = RequestBody(required = true, content = [Content(
            schema = Schema(implementation = WebhookEventRequest::class),
            examples = [
                ExampleObject(name = "ENTRY", value = """{"license_plate":"ABC1234","entry_time":"2025-01-01T10:00:00.000Z","event_type":"ENTRY"}"""),
                ExampleObject(name = "PARKED", value = """{"license_plate":"ABC1234","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}"""),
                ExampleObject(name = "EXIT", value = """{"license_plate":"ABC1234","exit_time":"2025-01-01T11:15:00.000Z","event_type":"EXIT"}"""),
            ],
        )]),
        responses = [
            ApiResponse(responseCode = "200", description = "Evento reconhecido; resposta sem corpo."),
            ApiResponse(responseCode = "400", description = "Payload inválido, precisão temporal incompatível ou saída anterior à entrada. O formato do erro de binding/validação pode variar."),
            ApiResponse(responseCode = "500", description = "Falha inesperada de infraestrutura; transação revertida."),
        ],
    )
    @Post
    open fun receiveEvent(@Valid @Body request: WebhookEventRequest): HttpResponse<Void> {
        processWebhookEventUseCase.process(request.toCommand())
        return HttpResponse.ok()
    }
}
