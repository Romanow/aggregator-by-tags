package ru.romanow.openapi.tags

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OpenApiAggregatorByTagsApplication

fun main(args: Array<String>) {
    runApplication<OpenApiAggregatorByTagsApplication>(*args)
}