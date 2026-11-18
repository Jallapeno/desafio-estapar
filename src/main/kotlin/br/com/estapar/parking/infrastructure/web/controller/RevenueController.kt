package br.com.estapar.parking.infrastructure.web.controller

import br.com.estapar.parking.application.port.inbound.QueryRevenueUseCase
import br.com.estapar.parking.infrastructure.web.dto.RevenueRequest
import br.com.estapar.parking.infrastructure.web.dto.RevenueResponse
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.serde.ObjectMapper
import java.io.IOException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Controller("/revenue")
@ExecuteOn(TaskExecutors.BLOCKING)
class RevenueController(private val queryRevenue: QueryRevenueUseCase, private val mapper: ObjectMapper) {
    @Operation(
        summary = "Consultar receita por setor e data UTC",
        description = "sector é obrigatório no conjunto de filtros. date é opcional e assume o dia atual UTC. Aceita query params e/ou body JSON; valores conflitantes retornam 400. Soma somente sessões EXITED até o instante timestamp. Sem receita retorna amount 0.00. Clientes de navegador, incluindo Swagger UI, devem usar query params: o body GET é mantido para compatibilidade com o enunciado.",
        tags = ["Receita"],
        requestBody = RequestBody(required = false, content = [Content(schema = Schema(implementation = RevenueRequest::class),
            examples = [ExampleObject(value = """{"sector":"A","date":"2025-01-01"}""")])]),
        responses = [
            ApiResponse(responseCode = "200", description = "Receita em BRL e instante UTC da consulta.", content = [Content(schema = Schema(implementation = RevenueResponse::class))]),
            ApiResponse(responseCode = "400", description = "Setor ausente/inválido, data inválida ou filtros conflitantes."),
            ApiResponse(responseCode = "500", description = "Falha inesperada de infraestrutura."),
        ],
    )
    @Get
    fun query(
        @Parameter(hidden = true) request: HttpRequest<*>,
        @Parameter(description = "Código do setor; obrigatório se não informado no body.", example = "A")
        @Nullable @QueryValue("sector") sector: String?,
        @Parameter(description = "Dia UTC yyyy-MM-dd; padrão: dia atual UTC.", example = "2025-01-01", schema = Schema(type = "string", format = "date"))
        @Nullable @QueryValue("date") date: String?,
    ): RevenueResponse {
        val body = readBody(request as ServerHttpRequest<*>)
        val bodySector = body?.sector?.trim()
        val querySector = sector?.trim()
        require(bodySector == null || querySector == null || bodySector == querySector) { "Conflicting sector filters" }
        val bodyDate = parseDate(body?.date)
        val queryDate = parseDate(date)
        require(bodyDate == null || queryDate == null || bodyDate == queryDate) { "Conflicting date filters" }
        val code = requireNotNull(querySector ?: bodySector) { "sector is required" }
        val result = queryRevenue.query(code, queryDate ?: bodyDate)
        return RevenueResponse(result.amount, "BRL", result.timestamp)
    }

    // GET bodies are required by the challenge, but skipped by Micronaut's standard @Body binding.
    // ByteBody is consumed on the blocking executor, with no changes to other endpoints.
    private fun readBody(request: ServerHttpRequest<*>): RevenueRequest? =
        request.byteBody().toInputStream().use { input ->
            val bytes = input.readAllBytes()
            if (bytes.isEmpty()) null else try {
                mapper.readValue(bytes, RevenueRequest::class.java)
            } catch (ex: IOException) {
                throw IllegalArgumentException("Invalid revenue JSON body", ex)
            }
        }

    private fun parseDate(value: String?): LocalDate? = value?.let {
        try {
            LocalDate.parse(it)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("date must be a valid ISO date (yyyy-MM-dd)", ex)
        }
    }
}
