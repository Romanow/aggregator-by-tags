package ru.romanow.openapi.aggregator

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.util.ReflectionUtils
import ru.romanow.openapi.aggregator.DefaultOpenApiAggregatorService.FilterType.*
import java.util.*
import java.util.Objects.equals
import java.util.Set.copyOf

class DefaultOpenApiAggregatorService : OpenApiAggregatorService {
    private val logger = LoggerFactory.getLogger(OpenApiAggregatorService::class.java)

    override fun aggregateOpenApi(
        declarations: List<Pair<String, String>>,
        include: Set<String>?,
        exclude: Set<String>?,
        info: Info, servers: List<Server>
    ): OpenAPI {
        val result = OpenAPI()

        val apis = readOpenApiMap(declarations)
        result.info = info
        result.servers = servers
        result.security = listOf<SecurityRequirement>()

        result.paths = Paths()
        result.components = Components()

        val filterType = when {
            !include.isNullOrEmpty() -> INCLUDE
            !exclude.isNullOrEmpty() -> EXCLUDE
            else -> NONE
        }
        val tags = when (filterType) {
            INCLUDE -> include
            EXCLUDE -> exclude
            else -> setOf()
        }
        apis.forEach { (prefix, source) ->
            copyOpenApi(source, result, prefix, tags!!, filterType)
        }
        return result
    }

    private fun copyOpenApi(
        source: OpenAPI,
        dest: OpenAPI,
        prefix: String,
        tags: Set<String>,
        filterType: FilterType
    ) {
        val components = source.components

        // Paths
        val usedItems = copyPaths(source, dest, prefix, tags, filterType)

        // Tags
        copyTags(source, dest, usedItems.tags)

        // Headers
        copyHeaders(dest, components)

        // Parameters
        copyParameters(dest, components)

        // Request Body
        copyRequestBodies(dest, components)

        // Response
        copyResponses(dest, components)

        // Schema
        copySchemas(dest, components, usedItems.schemas)

        // Security Schemes
        copySecuritySchemas(dest, components)
    }

    private fun copyTags(source: OpenAPI, dest: OpenAPI, tags: Set<String>) {
        source.tags?.toSet()
            ?.filter { it.name in tags && (dest.tags == null || it !in dest.tags) }
            ?.forEach { dest.addTagsItem(it) }
    }

    private fun copyPaths(
        source: OpenAPI, dest: OpenAPI, prefix: String, filters: Set<String>, filterType: FilterType
    ): UsedItems {
        val schemas = mutableSetOf<String>()
        val tags = mutableSetOf<String>()

        source.paths?.forEach { (name, path) ->
            for ((method, operation) in path.readOperationsMap()) {
                if (isOperationAllowed(operation.tags, filters, filterType)) {
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

                    tags.addAll(operation.tags)
                } else {
                    logger.warn("Remove operation {} {} with tags {}", method, name, operation.tags)
                    removeOperation(method.name.lowercase(Locale.getDefault()), path)
                }
            }

            if (path.readOperations().any { it != null }) {
                dest.path(prefix + name, path)
            }
        }

        return UsedItems(schemas, tags)
    }

    private fun isOperationAllowed(
        operationTags: List<String>?,
        filters: Set<String>, filterType: FilterType
    ): Boolean {
        if (filterType == INCLUDE) {
            // Если операция с тегом должна быть включена в результат, то он должен
            // присутствовать в списке тегов (список тегов операции должен быть не пустым)
            return !operationTags.isNullOrEmpty() && filters.any { operationTags.contains(it) }
        } else if (filterType == EXCLUDE) {
            // Если теги не должны быть включены в результат, то ни один из них не должен
            // присутствовать на методе (а значит пустой список тегов операции удовлетворяет этому условию)
            return operationTags.isNullOrEmpty() || filters.none { operationTags.contains(it) }
        }
        // если нет никакой фильтрации, то метод безусловно включается в результат
        return true
    }

    private fun removeOperation(methodName: String, path: PathItem) {
        val methodField = ReflectionUtils.findField(PathItem::class.java, methodName.lowercase())
            ?: throw IllegalArgumentException("Field ${methodName.lowercase()} not found")

        methodField.isAccessible = true
        ReflectionUtils.setField(methodField, path, null)
    }

    private fun copyHeaders(openApi: OpenAPI, components: Components) {
        components.headers?.forEach { (name, header) -> openApi.components.addHeaders(name, header) }
    }

    private fun copyParameters(openApi: OpenAPI, components: Components) {
        components.parameters?.forEach { (name, parameter) -> openApi.components.addParameters(name, parameter) }
    }

    private fun copyRequestBodies(openApi: OpenAPI, components: Components) {
        components.requestBodies?.forEach { (name, requestBody) ->
            openApi.components.addRequestBodies(name, requestBody)
        }
    }

    private fun copyResponses(openApi: OpenAPI, components: Components) {
        components.responses?.forEach { (name, response) -> openApi.components.addResponses(name, response) }
    }

    private fun copySchemas(openApi: OpenAPI, components: Components, usedSchemas: MutableSet<String>) {
        // Для вычисления всех используемых объектов мы обходим список usedSchemas,
        // и для каждой схемы смотрим на кого она ссылается. Цикл while нужен для того,
        // если схема ссылается на другую схему, а та схема ссылается еще на одну
        // (т.е. вложенных объектов 3+ уровня)
        val schemas = components.schemas
        if (schemas != null) {
            val processedSchemas = mutableSetOf<String>()
            while (!equals(processedSchemas, usedSchemas)) {
                for (schemaName in copyOf(usedSchemas)) {
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

    private fun readOpenApiMap(apis: List<Pair<String, String>>): Map<String, OpenAPI> {
        val reader = Yaml.mapper().reader()
        return apis.associate {
            it.first to reader.readValue(ClassPathResource(it.second).inputStream, OpenAPI::class.java)
        }
    }

    internal enum class FilterType {
        INCLUDE,
        EXCLUDE,
        NONE
    }

    internal data class UsedItems(
        val schemas: MutableSet<String>,
        val tags: MutableSet<String>
    )
}
