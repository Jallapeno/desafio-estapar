package br.com.estapar.parking.application.port.outbound

/**
 * Outbound port abstracting transactional execution boundaries for application use cases.
 */
interface TransactionPort {
    fun <T> executeInTransaction(block: () -> T): T
}
