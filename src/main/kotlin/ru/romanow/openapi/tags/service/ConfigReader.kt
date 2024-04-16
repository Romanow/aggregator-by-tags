package ru.romanow.openapi.tags.service

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import ru.romanow.openapi.tags.config.properties.ApplicationProperties

fun readOpenApis(properties: ApplicationProperties): Map<String, OpenAPI> {
    val apis = mutableMapOf<String, OpenAPI>()
    val reader = Yaml.mapper().reader()
    for ((name, config) in properties.apis) {
        val file = if (config.external != null && config.external.exists() && config.external.isReadable) {
            config.external
        } else {
            config.local
        }
        apis[name] = reader.readValue(file?.inputStream, OpenAPI::class.java)
    }
    return apis
}
