package br.com.estapar.parking.application.port.inbound

import java.time.Instant

enum class EntryResult { ACCEPTED, ALREADY_ACTIVE, ALREADY_PROCESSED, FULL }

fun interface ProcessEntryUseCase {
    fun enter(licensePlate: String, entryTime: Instant): EntryResult
}
