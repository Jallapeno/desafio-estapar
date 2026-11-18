package br.com.estapar.parking.infrastructure.web.exception

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.time.Instant

@Serdeable
data class ApiErrorResponse(
    val message: String,
    val timestamp: Instant = Instant.now(),
)

@Produces
@Singleton
class IllegalArgumentExceptionHandler :
    ExceptionHandler<IllegalArgumentException, HttpResponse<ApiErrorResponse>> {

    override fun handle(request: HttpRequest<*>, exception: IllegalArgumentException): HttpResponse<ApiErrorResponse> =
        HttpResponse.badRequest(
            ApiErrorResponse(message = exception.message ?: "Invalid request payload"),
        )
}
