package ru.romanow.openapi.tags.service

import io.swagger.v3.oas.models.OpenAPI
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.romanow.openapi.tags.config.OpenApiConfiguration.Companion.OPENAPI_MAP_BEAN_NAME
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import java.nio.file.Files.readAttributes
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneOffset.systemDefault

/**
 * При старте приложения создаем список внешних отслеживаемых файлов (external != null)
 * Если время изменения внешнего файла больше времени последнего изменения, то перезагружаем конфигурацию
 */
@Service
@ConditionalOnProperty(value = ["application.config.reload.enabled"], havingValue = "true", matchIfMissing = true)
class ConfigMonitor(
    private val applicationProperties: ApplicationProperties,
    private val applicationContext: ApplicationContext
) {
    private final val logger = LoggerFactory.getLogger(ConfigMonitor::class.java)

    private var filesUnderMonitor = mutableMapOf<String, LocalDateTime>()

    @PostConstruct
    fun init() {
        applicationProperties.apis
            .filter { (_, config) -> config.external != null }
            .forEach { (name, _) -> filesUnderMonitor[name] = LocalDateTime.now() }
    }

    @Scheduled(fixedDelayString = "\${application.config.reload.interval}", initialDelayString = "PT10S")
    fun executeCycle() {
        val updatedFiles = mutableListOf<String>()
        for (name in filesUnderMonitor.keys) {
            val config = applicationProperties.apis[name]!!
            if (resourceAvailable(config.external)) {
                logger.info("Processing file $config")

                val attributes = readAttributes(config.external!!.file.toPath(), BasicFileAttributes::class.java)
                val lastModifiedTime =
                    LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), systemDefault())

                val isFileModified = filesUnderMonitor[name]!!.isBefore(lastModifiedTime)
                logger.info("Check '$name' update time: $lastModifiedTime, result: $isFileModified")
                if (isFileModified) {
                    filesUnderMonitor[name] = lastModifiedTime
                    updatedFiles.add(config.external.filename!!)
                }
            }
        }

        if (updatedFiles.isNotEmpty()) {
            logger.info("Files {} updated, reload OpenAPI map", updatedFiles)
            val apis = applicationContext.getBean(OPENAPI_MAP_BEAN_NAME, mutableMapOf<String, OpenAPI>()::class.java)
            apis.clear()
            apis.putAll(readOpenApis(applicationProperties))
        }
    }
}
