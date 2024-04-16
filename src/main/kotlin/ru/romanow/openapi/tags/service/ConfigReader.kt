package ru.romanow.openapi.tags.service

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.springframework.core.io.UrlResource
import ru.romanow.openapi.tags.config.properties.ApplicationProperties

fun readOpenApis(properties: ApplicationProperties): Map<String, OpenAPI> {
    val apis = mutableMapOf<String, OpenAPI>()
    val reader = Yaml.mapper().reader()
    for ((name, config) in properties.apis) {
        // Если есть внешний ресурс, то он имеет приоритет над внутренним
        val file = if (resourceAvailable(config.external)) config.external else config.local!!
        apis[name] = reader.readValue(file?.inputStream, OpenAPI::class.java)
    }
    return apis
}

fun resourceAvailable(resource: UrlResource?) = resource != null && resource.exists() && resource.isReadable
