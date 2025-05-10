package ru.romanow.openapi.aggregator

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server

interface OpenApiAggregatorService {
    fun aggregateOpenApi(
        declarations: List<Pair<String, String>>,
        include: Set<String>?,
        exclude: Set<String>?,
        info: Info = defaultInfo(),
        servers: List<Server> = defaultServers()
    ): OpenAPI
}

private fun defaultServers() = listOf(Server().url("http://localhost:8080"))
private fun defaultInfo() = Info().title("Application").version("1.0.0")
