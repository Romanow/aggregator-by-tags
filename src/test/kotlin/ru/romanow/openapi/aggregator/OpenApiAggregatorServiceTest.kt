package ru.romanow.openapi.aggregator

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.core.io.ClassPathResource
import java.util.stream.Stream

internal class OpenApiAggregatorServiceTest {
    private val openApiAggregatorService = DefaultOpenApiAggregatorService()

    @ParameterizedTest
    @ArgumentsSource(ValueProvider::class)
    fun testAll(include: Set<String>, exclude: Set<String>, expected: String) {
        val yaml = ClassPathResource(expected).inputStream.readAllBytes().decodeToString()
        val expectedOpenApi = Yaml.mapper().readValue(yaml, OpenAPI::class.java)

        val declarations = listOf(
            "/store" to "source/store-service.yml",
            "/warehouse" to "source/warehouse-service.yml",
            "/warranty" to "source/warranty-service.yml"
        )
        val actualOpenApi = openApiAggregatorService.aggregateOpenApi(declarations, include, exclude)

        println(actualOpenApi.openapi)
        assertThat(actualOpenApi).isEqualTo(expectedOpenApi)
    }
}

internal class ValueProvider : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext): Stream<Arguments> =
        Stream.of(
            of(setOf<String>(), setOf<String>(), "target/all.yml"),
            of(setOf("Read", "public", "Order API"), setOf<String>(), "target/include.yml"),
            of(setOf<String>(), setOf("private"), "target/exclude.yml"),
            of(setOf<String>(), setOf("public", "private"), "target/empty.yml")
        )
}
