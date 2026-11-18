package br.com.estapar.parking.application.port.inbound

import java.math.BigDecimal

enum class ParkedResult {
    PARKED, ALREADY_PARKED, NO_ACTIVE_SESSION, UNKNOWN_SPOT, SPOT_OCCUPIED, DIFFERENT_SPOT,
}

fun interface ProcessParkedUseCase {
    fun park(licensePlate: String, lat: BigDecimal, lng: BigDecimal): ParkedResult
}
