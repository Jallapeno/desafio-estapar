package br.com.estapar.parking.infrastructure.persistence.repository

import br.com.estapar.parking.infrastructure.persistence.entity.SectorEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.math.BigDecimal

@JdbcRepository(dialect = Dialect.MYSQL)
interface SectorJdbcRepository : CrudRepository<SectorEntity, String> {

    @Query("SELECT sector, base_price, max_capacity FROM sectors ORDER BY sector ASC FOR UPDATE")
    fun findAllForUpdate(): List<SectorEntity>

    @Query("SELECT sector, base_price, max_capacity FROM sectors ORDER BY sector ASC LIMIT 1")
    fun findFirstOrdered(): SectorEntity?

    @Query("SELECT COALESCE(SUM(max_capacity), 0) FROM sectors")
    fun sumMaxCapacity(): Long

    @Query(
        """
        INSERT INTO sectors (sector, base_price, max_capacity)
        VALUES (:sector, :basePrice, :maxCapacity) AS incoming
        ON DUPLICATE KEY UPDATE
            base_price = incoming.base_price,
            max_capacity = incoming.max_capacity
        """,
    )
    fun upsert(sector: String, basePrice: BigDecimal, maxCapacity: Int)
}
