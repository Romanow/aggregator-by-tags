package ru.romanow.openapi.tags.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.ClassPathResource

@ConfigurationProperties("application")
data class ApplicationProperties(
    val apis: List<OpenApiProperties>
)

data class OpenApiProperties(
    val name: String,
    val prefix: String? = null,
    val file: ClassPathResource,
)
