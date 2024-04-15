package ru.romanow.openapi.tags.service

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
import org.springframework.stereotype.Service
import org.springframework.util.ReflectionUtils
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import ru.romanow.openapi.tags.web.OpenApiController
import java.math.BigDecimal

@Service
class OpenApiAggregatorServiceImpl(
    private val applicationProperties: ApplicationProperties
) : OpenApiAggregatorService {

    private val logger = LoggerFactory.getLogger(OpenApiController::class.java)

    override fun findOpenApiByName(name: String) =
        applicationProperties.apis
            .findLast { name == it.name }!!.file
            .inputStream.readAllBytes()
            .decodeToString()

    override fun aggregateOpenApi(include: Set<String>?, exclude: Set<String>?): OpenAPI {
        val openApi = OpenAPI()

        val reader = Yaml.mapper().reader()
        val apis = mutableMapOf<String, OpenAPI>()
        for (api in applicationProperties.apis) {
            apis[api.name] = reader.readValue(api.file.inputStream, OpenAPI::class.java)
        }

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

        openApi.paths = Paths()
        openApi.components = Components()
        for ((name, api) in apis.entries) {
            val (_, prefix, _) = applicationProperties.apis.first { it.name == name }
            copyOpenApi(openApi, prefix, api, tags)
        }
        return openApi
    }

    private fun copyOpenApi(dest: OpenAPI, prefix: String?, source: OpenAPI, tags: Set<Tag>) {
        val components = source.components

        // Tags
        copyTags(source, dest, tags)

        // Paths
        val usedSchemas = copyPaths(source, dest, prefix, tags)

        // Headers
        copyHeaders(dest, components)

        // Parameters
        copyParameters(dest, components)

        // Request Body
        copyRequestBodies(dest, components)

        // Response
        copyResponses(dest, components)

        // Schema
        copySchemas(dest, components, usedSchemas)

        // Security Schemes
        copySecuritySchemas(dest, components)
    }

    private fun copyTags(source: OpenAPI, dest: OpenAPI, tags: Set<Tag>) {
        source.tags.toSet()
            .filter { it in tags && (dest.tags == null || it !in dest.tags) }
            .forEach { dest.addTagsItem(it) }
    }

    private fun copyPaths(source: OpenAPI, dest: OpenAPI, prefix: String?, tags: Set<Tag>): MutableSet<String> {
        val schemas = mutableSetOf<String>()

        source.paths?.forEach { (name, path) ->
            for ((method, operation) in path.readOperationsMap()) {
                if (tags.map { it.name }.containsAll(operation.tags)) {
                    operation.requestBody
                        ?.content
                        ?.values
                        ?.forEach { schemas += calculateUsedSchemas(it.schema) }

                    operation.responses
                        ?.values
                        ?.filter { it.content != null }
                        ?.flatMap { it.content.values }
                        ?.forEach { schemas += calculateUsedSchemas(it.schema) }

                    operation.parameters
                        ?.forEach { schemas += calculateUsedSchemas(it.schema) }

                    modifyOperation(operation)
                } else {
                    logger.warn("Remove operation {} {} with tags {}", method.name, name, operation.tags)
                    removeOperation(method.name.lowercase(), path)
                }
            }

            if (path.readOperations().any { it != null }) {
                dest.path(prefix + name, path)
            }
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

    private fun copySchemas(openApi: OpenAPI, components: Components, usedSchemas: MutableSet<String>) {
        // Для вычисления всех используемых объектов мы обходим список usedSchemas,
        // и для каждой схемы смотрим на кого она ссылается. Цикл while нужен для того,
        // если схема ссылается на другую схему, а та схема ссылается еще на одну
        // (т.е. вложенных объектов 3+ уровня)
        val schemas = components.schemas
        if (schemas != null) {
            val processedSchemas = mutableSetOf<String>()
            while (processedSchemas != usedSchemas) {
                for (schemaName in java.util.Set.copyOf(usedSchemas)) {
                    if (schemaName !in processedSchemas) {
                        val schema = schemas[schemaName]!!
                        usedSchemas.addAll(calculateUsedSchemas(schema))
                        processedSchemas.add(schemaName)
                    }
                }
            }

            schemas
                .filter { it.key in usedSchemas }
                .forEach { (name, schema) ->
                    schema.additionalProperties = false
                    openApi.components.addSchemas(name, schema)
                }
        }
    }

    private fun calculateUsedSchemas(schema: Schema<*>): Set<String> {
        // Ссылки на схемы могут быть в самом компоненте, в items (массиве) или в parameters
        val schemas = HashSet<String>()
        if (schema.`$ref` != null) {
            val schemaRef = schema.`$ref`
            schemas.add(schemaRef.substring(schemaRef.lastIndexOf("/") + 1))
        } else if (schema is ArraySchema) {
            schemas.addAll(calculateUsedSchemas(schema.items))
        }
        if (schema.properties != null) {
            for ((_, value) in schema.properties) {
                schemas.addAll(calculateUsedSchemas(value))
            }
        }
        return schemas
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

    private fun servers() = listOf(Server().url("http://localhost:8080").description("Local server"))

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
