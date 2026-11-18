package br.com.estapar.parking.domain.service

import br.com.estapar.parking.domain.model.GarageConfiguration
import br.com.estapar.parking.domain.model.ParkingSpot
import br.com.estapar.parking.domain.model.Sector

/** Validate a complete incoming snapshot without removing historical identities or moving existing spots. */
object GarageTopologyPolicy {
    fun validate(
        currentSectors: List<Sector>,
        currentSpots: List<ParkingSpot>,
        incoming: GarageConfiguration,
        activeSessions: Long,
    ) {
        val sectorCodes = incoming.sectors.map { it.code }.toSet()
        require(currentSectors.all { it.code in sectorCodes }) { "Cannot remove configured sectors" }
        val incomingSpots = incoming.spots.associateBy { it.id }
        require(currentSpots.all { current ->
            val next = incomingSpots[current.id]
            next != null && next.sectorCode == current.sectorCode &&
                next.lat.compareTo(current.lat) == 0 && next.lng.compareTo(current.lng) == 0
        }) { "Cannot remove or relocate configured spots" }
        require(incoming.sectors.sumOf { it.maxCapacity.toLong() } >= activeSessions) {
            "Garage capacity cannot be reduced below active session count"
        }
    }
}
