package ru.romanow.openapi.tags.web

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
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
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping("/api/v1/openapi")
class OpenApiController(
    private val applicationProperties: ApplicationProperties,
    private val apis: Map<String, OpenAPI>
) {
    private val logger = LoggerFactory.getLogger(OpenApiController::class.java)

    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun all() = applicationProperties.apis.map { it.name }

    @GetMapping(value = ["/{name}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun get(@PathVariable name: String) =
        applicationProperties.apis
            .findLast { name == it.name }!!.file
            .inputStream.readAllBytes()
            .decodeToString()

    @GetMapping(value = ["/all"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun all(
        @RequestParam(required = false) include: List<String>?,
        @RequestParam(required = false) exclude: List<String>?
    ): String {
        val openApi = OpenAPI()

        val tags: Set<Tag> = apis
            .values
            .flatMap { it.tags }
            .filter {
                if (!include.isNullOrEmpty()) {
                    return@filter include.contains(it.name)
                } else if (!exclude.isNullOrEmpty()) {
                    return@filter !exclude.contains(it.name)
                }
                return@filter true
            }.toSet()

        openApi.info = info()
        openApi.servers = servers()
        openApi.security = security()

        openApi.components(Components())
        for ((name, api) in apis.entries) {
            val (_, prefix, _) = applicationProperties.apis.first { it.name == name }
            copyOpenApi(openApi, prefix, api, tags)
        }

        return Yaml.pretty().writeValueAsString(openApi)
    }

    private fun copyOpenApi(openApi: OpenAPI, prefix: String, api: OpenAPI, tags: Set<Tag>) {
        val components = api.components

        // Tags
        copyTags(openApi, api, tags)

        // Paths
        val usedSchemas = copyPaths(openApi, prefix, api, tags)

        // Headers
        copyHeaders(openApi, components)

        // Parameters
        copyParameters(openApi, components)

        // Request Body
        copyRequestBodies(openApi, components)

        // Response
        copyResponses(openApi, components)

        // Schema
        copySchemas(openApi, components, usedSchemas)

        // Security Schemes
        copySecuritySchemas(openApi, components)
    }

    private fun copyTags(openApi: OpenAPI, api: OpenAPI, tags: Set<Tag>) {
        api.tags.toSet()
            .filter { it in tags }
            .forEach { openApi.addTagsItem(it) }
    }

    private fun copyPaths(openApi: OpenAPI, prefix: String, api: OpenAPI, tags: Set<Tag>): Set<String> {
        val schemas: HashSet<String> = HashSet()
        api.paths?.forEach { (name, path) ->
            for ((method, operation) in path.readOperationsMap()) {
                if (tags.map { it.name }.containsAll(operation.tags)) {
                    operation.requestBody
                        ?.content
                        ?.values
                        ?.forEach {
                            val schemaName = if (it.schema.`$ref` != null) {
                                it.schema.`$ref`.substringAfterLast("/")
                            } else {
                                it.schema.items.`$ref`.substringAfterLast("/")
                            }
                            schemas += schemaName
                        }

                    operation.responses
                        ?.values
                        ?.filter { it.content != null }
                        ?.flatMap { it.content.values }
                        ?.forEach { schemas += it.schema.name }

                    modifyOperation(operation)
                } else {
                    logger.warn("Remove operation {} {} with tags {}", method.name, name, operation.tags)
                    removeOperation(method.name.lowercase(), path)
                }
            }

            openApi.path("/$prefix$name", path)
        }
        return schemas
    }

    private fun removeOperation(methodName: String, path: PathItem) {
        val methodField = ReflectionUtils.findField(PathItem::class.java, methodName.lowercase())
            ?: throw IllegalArgumentException("Field ${methodName.lowercase()} not found")

        methodField.isAccessible = true
        ReflectionUtils.setField(methodField, path, null)
    }

    private fun copyHeaders(openApi: OpenAPI, components: Components) {
        components.headers?.forEach { (name, header) ->
            openApi.components.addHeaders(name, header)
        }
    }

    private fun copyParameters(openApi: OpenAPI, components: Components) {
        components.parameters?.forEach { (name, parameter) ->
            updateSchema(parameter.schema)
            openApi.components.addParameters(name, parameter)
        }
    }

    private fun copyRequestBodies(openApi: OpenAPI, components: Components) {
        components.requestBodies?.forEach { (name, requestBody) ->
            openApi.components.addRequestBodies(name, requestBody)
        }
    }

    private fun copyResponses(openApi: OpenAPI, components: Components) {
        components.responses?.forEach { (name, response) ->
            openApi.components.addResponses(name, response)
        }
    }

    private fun copySchemas(openApi: OpenAPI, components: Components, schemas: Set<String>) {
        components.schemas
            ?.filter { it.value.name in schemas }
            ?.forEach { (name, schema) ->
                schema.additionalProperties = false
                openApi.components.addSchemas(name, schema)
            }
    }

    private fun copySecuritySchemas(openApi: OpenAPI, components: Components) {
        components.securitySchemes?.forEach { (name, securityScheme) ->
            openApi.components.addSecuritySchemes(name, securityScheme)
        }
    }

    private fun modifyOperation(operation: Operation) {
        if (operation.responses.containsKey("200")) {
            val content = operation.responses["200"]?.content
            if (content != null && content.containsKey(MediaType.APPLICATION_JSON_VALUE)) {
                val schema = content[MediaType.APPLICATION_JSON_VALUE]?.schema
                if (schema?.type != null) {
                    updateSchema(schema)
                }
            }
        }
    }

    private fun updateSchema(property: Schema<*>?) {
        when (property?.type) {
            ARRAY -> {
                val arraySchema = property as ArraySchema
                property.setMaxItems(Int.MAX_VALUE)
                if (arraySchema.items.type != null) {
                    updateSchema(arraySchema.items)
                }
            }

            NUMBER, INTEGER -> {
                property.maximum = BigDecimal.valueOf(Int.MAX_VALUE.toLong(), 0)
                property.minimum = BigDecimal.valueOf(Int.MIN_VALUE.toLong(), 0)
            }

            STRING -> {
                if (property.format == UUID_FORMAT) {
                    property.format = null
                    property.pattern = UUID_PATTERN
                }
                property.maxLength = MAX_LENGTH
            }
        }
    }

    private fun servers() = listOf(Server().url("http://localhost:8080"))

    private fun security() = listOf<SecurityRequirement>()

    private fun info(): Info {
        return Info()
            .title("OpenAPI aggregator by Tags")
            .description("Concatenate multiple OpenAPI in one file with Include and exclude tags")
            .contact(
                Contact()
                    .email("romanowalex@mail.ru")
                    .name("Romanov Alexey")
                    .url("https://romanow.github.io/")
            )
            .version("1.0.0")
    }

    companion object {
        private const val MAX_LENGTH = 255
        private const val UUID_PATTERN = "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b"
        private const val UUID_FORMAT = "uuid"
        private const val INTEGER = "integer"
        private const val ARRAY = "array"
        private const val STRING = "string"
        private const val NUMBER = "number"
    }
}