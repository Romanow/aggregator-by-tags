package ru.romanow.openapi.tags.config

import io.swagger.v3.oas.models.OpenAPI
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import ru.romanow.openapi.tags.config.properties.ApplicationProperties

@Configuration
@EnableConfigurationProperties(ApplicationProperties::class)
class OpenApiConfiguration {

    @Bean
    fun apis(applicationProperties: ApplicationProperties): Map<String, OpenAPI> {
        val apis = mutableMapOf<String, OpenAPI>()
        for (api in applicationProperties.apis) {
            val openApi = Yaml(Constructor(OpenAPI::class.java)).load<OpenAPI>(api.file.inputStream)
            apis[api.name] = openApi
        }
        return apis
    }
}