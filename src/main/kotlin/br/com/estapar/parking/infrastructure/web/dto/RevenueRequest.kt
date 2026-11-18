package br.com.estapar.parking.infrastructure.web.dto

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class RevenueRequest(val sector: String? = null, val date: String? = null)
