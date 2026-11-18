package br.com.estapar.parking.application.port.inbound

import java.time.Instant

enum class ExitResult { EXITED, NO_ACTIVE_SESSION }

fun interface ProcessExitUseCase {
    fun exit(licensePlate: String, exitTime: Instant): ExitResult
}
