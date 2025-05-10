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
            "/store" to "source/store.yml",
            "/orders" to "source/orders.yml",
            "/warehouse" to "source/warehouse.yml",
            "/warranty" to "source/warranty.yml"
        )
        val actualOpenApi = openApiAggregatorService.aggregateOpenApi(declarations, include, exclude)

        assertThat(actualOpenApi).isEqualTo(expectedOpenApi)
    }
}

internal class ValueProvider : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext): Stream<Arguments> =
        Stream.of(
            of(setOf<String>(), setOf<String>(), "target/all.yml"),
            of(setOf("Read", "Store API", "Order API"), setOf<String>(), "target/include.yml"),
            of(setOf<String>(), setOf("Modification", "Warranty API"), "target/exclude.yml"),
            of(setOf<String>(), setOf("Read", "Modification"), "target/empty.yml")
        )
}
