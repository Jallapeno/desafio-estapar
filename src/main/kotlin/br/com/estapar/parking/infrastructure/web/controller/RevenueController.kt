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
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Controller("/revenue")
@ExecuteOn(TaskExecutors.BLOCKING)
class RevenueController(private val queryRevenue: QueryRevenueUseCase, private val mapper: ObjectMapper) {
    @Get
    fun query(
        request: HttpRequest<*>,
        @Nullable @QueryValue("sector") sector: String?,
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
