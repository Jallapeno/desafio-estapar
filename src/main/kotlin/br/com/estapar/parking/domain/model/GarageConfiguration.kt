package br.com.estapar.parking.domain.model

/**
 * Snapshot of sectors and physical parking spots provided by the garage configuration source.
 */
data class GarageConfiguration(
    val sectors: List<Sector>,
    val spots: List<ParkingSpot>,
)
