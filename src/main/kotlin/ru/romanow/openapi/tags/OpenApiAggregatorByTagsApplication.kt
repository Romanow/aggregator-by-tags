package ru.romanow.openapi.tags

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class OpenApiAggregatorByTagsApplication

fun main(args: Array<String>) {
    runApplication<OpenApiAggregatorByTagsApplication>(*args)
}
