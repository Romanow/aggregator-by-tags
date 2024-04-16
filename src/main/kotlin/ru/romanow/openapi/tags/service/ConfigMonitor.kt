package ru.romanow.openapi.tags.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
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
            .filter { (_, config) -> resourceAvailable(config.external) }
            .forEach { (name, _) -> filesUnderMonitor[name] = LocalDateTime.now() }
    }

    @Scheduled(fixedDelayString = "\${application.config.reload.interval}", initialDelayString = "PT10S")
    fun executeCycle() {
        val updatedFiles = mutableListOf<String>()
        val apis = applicationProperties.apis.filter { (_, config) -> resourceAvailable(config.external) }
        for ((name, config) in apis) {
            val resource = config.external
            if (filesUnderMonitor.containsKey(name)) {
                val attributes = readAttributes(resource!!.file.toPath(), BasicFileAttributes::class.java)
                val lastModifiedTime =
                    LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), systemDefault())

                if (filesUnderMonitor[name]!!.isBefore(lastModifiedTime)) {
                    filesUnderMonitor[name] = lastModifiedTime
                    updatedFiles.add(resource.filename!!)
                }
            }
        }

        if (updatedFiles.isNotEmpty()) {
            logger.info("Files {} updated, reload config", updatedFiles)
            reloadOpenApi()
        }
    }

    private fun reloadOpenApi() {
        val registry = applicationContext.autowireCapableBeanFactory as DefaultSingletonBeanRegistry
        registry.destroySingleton(OPENAPI_MAP_BEAN_NAME)
        registry.registerSingleton(OPENAPI_MAP_BEAN_NAME, readOpenApis(applicationProperties))
    }
}
