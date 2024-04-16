package ru.romanow.openapi.tags.web

import io.swagger.v3.core.util.Yaml
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import ru.romanow.openapi.tags.service.OpenApiAggregatorService
import java.util.*

@RestController
@RequestMapping("/api/v1/openapi")
class OpenApiController(
    private val openApiAggregatorService: OpenApiAggregatorService,
    private val applicationProperties: ApplicationProperties
) {

    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun all() = applicationProperties.apis.keys

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
