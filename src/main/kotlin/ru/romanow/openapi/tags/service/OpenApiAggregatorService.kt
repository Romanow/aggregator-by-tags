package ru.romanow.openapi.tags.service

import io.swagger.v3.oas.models.OpenAPI

interface OpenApiAggregatorService {
    fun findOpenApiByName(name: String): String

    fun aggregateOpenApi(include: Set<String>?, exclude: Set<String>?): OpenAPI
}
