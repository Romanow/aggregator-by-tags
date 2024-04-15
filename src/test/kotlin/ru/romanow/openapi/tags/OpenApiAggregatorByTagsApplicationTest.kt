package ru.romanow.openapi.tags

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import ru.romanow.openapi.tags.config.properties.ApplicationProperties
import java.util.stream.Stream

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
internal class OpenApiAggregatorByTagsApplicationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var applicationProperties: ApplicationProperties

    @ParameterizedTest
    @ArgumentsSource(ValueProvider::class)
    fun testAll(path: String, file: String) {
        val yaml = ClassPathResource("openapi/$file")
            .inputStream.readAllBytes()
            .decodeToString()
        val targetOpenApi = Yaml.mapper().readValue(yaml, OpenAPI::class.java)

        val openApi = mockMvc.get("/api/v1/openapi/$path") { accept(MediaType.TEXT_PLAIN) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.valueOf("text/plain;charset=UTF-8")) }
            }
            .andReturn()
            .response
            .contentAsString
            .let { Yaml.mapper().readValue(it, OpenAPI::class.java) }

        assertThat(openApi).isEqualTo(targetOpenApi)
    }

    @ParameterizedTest
    @ValueSource(strings = ["store", "orders", "warehouse", "warranty"])
    fun testGetByName(name: String) {
        val yaml = applicationProperties.apis
            .first { it.name == name }.file
            .inputStream.readAllBytes()
            .decodeToString()

        mockMvc.get("/api/v1/openapi/$name") { accept(MediaType.TEXT_PLAIN) }
            .andExpect {
                status { isOk() }
                content {
                    contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    string(yaml)
                }
            }
    }

    internal class ValueProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<Arguments> =
            Stream.of(
                of("all", "all.yml"),
                of("all?include=Read,Store API,Order API", "include.yml"),
                of("all?exclude=Modification,Warranty API", "exclude.yml"),
                of("all?exclude=Read,Modification", "empty.yml")
            )
    }
}
