package ru.romanow.openapi.tags.config

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.romanow.openapi.tags.config.properties.ApplicationProperties

@Configuration
@EnableConfigurationProperties(ApplicationProperties::class)
class OpenApiConfiguration {

    @Bean
    fun apis(applicationProperties: ApplicationProperties): Map<String, OpenAPI> {
        val apis = mutableMapOf<String, OpenAPI>()
        val reader = Yaml.mapper().reader()
        for (api in applicationProperties.apis) {
            val openApi = reader.readValue(api.file.inputStream, OpenAPI::class.java)
            apis[api.name] = openApi
        }
        return apis
    }
}
