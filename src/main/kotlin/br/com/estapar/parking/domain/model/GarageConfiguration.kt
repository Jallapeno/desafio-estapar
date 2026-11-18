package br.com.estapar.parking.domain.model

import java.math.BigDecimal
import java.util.Locale

/**
 * Validated snapshot of the topology supplied by the simulator.
 * Capacity is independent from the number of spots in the response.
 */
data class GarageConfiguration(
    val sectors: List<Sector>,
    val spots: List<ParkingSpot>,
) {
    init {
        require(sectors.isNotEmpty()) { "Garage configuration must contain sectors" }
        require(spots.isNotEmpty()) { "Garage configuration must contain spots" }
        require(sectors.map { it.code.uppercase(Locale.ROOT) }.distinct().size == sectors.size) {
            "Garage configuration contains duplicate sector codes"
        }
        require(spots.map { it.id }.distinct().size == spots.size) {
            "Garage configuration contains duplicate spot ids"
        }

        val sectorCodes = sectors.map { it.code }.toSet()
        sectors.forEach {
            require(it.code.length <= 20 && it.code == it.code.trim()) { "Invalid sector code" }
            require(it.basePrice.stripTrailingZeros().scale() <= 2 && it.basePrice <= BigDecimal("99999999.99")) {
                "Sector base price cannot be represented as DECIMAL(10,2)"
            }
        }
        spots.forEach {
            require(it.sectorCode in sectorCodes) { "Spot ${it.id} references an unknown sector" }
            require(it.lat >= BigDecimal("-90") && it.lat <= BigDecimal("90")) { "Invalid spot latitude" }
            require(it.lng >= BigDecimal("-180") && it.lng <= BigDecimal("180")) { "Invalid spot longitude" }
            require(it.lat.stripTrailingZeros().scale() <= 6 && it.lng.stripTrailingZeros().scale() <= 6) {
                "Spot coordinates cannot be represented without rounding to six decimal places"
            }
        }
        require(spots.map { it.lat.stripTrailingZeros() to it.lng.stripTrailingZeros() }.distinct().size == spots.size) {
            "Garage configuration contains duplicate spot coordinates"
        }
    }
}
