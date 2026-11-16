package br.com.estapar.parking.infrastructure.persistence.adapter

import br.com.estapar.parking.application.port.outbound.TransactionPort
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import java.sql.Connection

@Singleton
class MicronautTransactionAdapter(
    private val transactionOperations: TransactionOperations<Connection>,
) : TransactionPort {

    override fun <T> executeInTransaction(block: () -> T): T =
        transactionOperations.executeWrite { block() }
}
