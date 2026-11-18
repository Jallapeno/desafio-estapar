package br.com.estapar.parking.infrastructure.simulator.client

import br.com.estapar.parking.infrastructure.simulator.dto.GarageConfigResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client("\${simulator.base-url}")
interface SimulatorHttpClient {

    @Get("/garage")
    fun fetchGarageConfiguration(): GarageConfigResponse
}
