package ru.romanow.openapi.tags.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.UrlResource

@ConfigurationProperties("application")
data class ApplicationProperties(
    var apis: Map<String, OpenApiConfig>
)

data class OpenApiConfig(
    val name: String? = null,
    val prefix: String? = null,
    val local: ClassPathResource? = null,
    val external: UrlResource? = null
)
