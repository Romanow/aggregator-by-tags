package ru.romanow.openapi.aggregator

import io.swagger.v3.oas.models.OpenAPI

interface OpenApiAggregatorService {
    fun aggregateOpenApi(
        declarations: List<Pair<String, String>>,
        include: Set<String>?,
        exclude: Set<String>?
    ): OpenAPI
}
