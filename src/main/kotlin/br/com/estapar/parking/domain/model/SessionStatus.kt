package br.com.estapar.parking.domain.model

/**
 * Lifecycle state of a vehicle parking session.
 */
enum class SessionStatus {
    ENTERED,
    PARKED,
    EXITED,
}
