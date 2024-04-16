package ru.romanow.openapi.tags.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import ru.romanow.openapi.tags.service.readOpenApis

@Configuration
@EnableConfigurationProperties(ApplicationProperties::class)
class OpenApiConfiguration {

    @Bean(OPENAPI_MAP_BEAN_NAME)
    fun apis(applicationProperties: ApplicationProperties) = readOpenApis(applicationProperties)

    companion object {
        const val OPENAPI_MAP_BEAN_NAME = "apis"
    }
}
