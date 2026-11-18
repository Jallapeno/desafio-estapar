package br.com.estapar.parking.infrastructure.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import jakarta.inject.Singleton

@OpenAPIDefinition(info = Info(title = "Estapar Parking API", version = "1.0", description = "Eventos de estacionamento e consulta de receita em BRL e UTC."))
@Singleton
class OpenApiConfiguration
