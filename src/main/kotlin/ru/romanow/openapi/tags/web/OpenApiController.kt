package ru.romanow.openapi.tags.web

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.ReflectionUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import ru.romanow.openapi.tags.service.OpenApiAggregatorService
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping("/api/v1/openapi")
class OpenApiController(
    private val openApiAggregatorService: OpenApiAggregatorService,
    private val applicationProperties: ApplicationProperties
) {

    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun all() = applicationProperties.apis.map { it.name }

    @GetMapping(value = ["/{name}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun get(@PathVariable name: String) = openApiAggregatorService.findOpenApiByName(name)

    @GetMapping(value = ["/all"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun all(
        @RequestParam(required = false) include: Set<String>?,
        @RequestParam(required = false) exclude: Set<String>?
    ): String {
        val openApi = openApiAggregatorService.aggregateOpenApi(include, exclude)
        return Yaml.pretty().writeValueAsString(openApi)
    }
}