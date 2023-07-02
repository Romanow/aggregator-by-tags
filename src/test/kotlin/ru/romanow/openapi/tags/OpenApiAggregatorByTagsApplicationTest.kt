package ru.romanow.openapi.tags

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import ru.romanow.openapi.tags.config.properties.ApplicationProperties

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
internal class OpenApiAggregatorByTagsApplicationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var applicationProperties: ApplicationProperties

    @Test
    fun testAll() {
        val yaml = mockMvc.get("/api/v1/openapi/all") { accept(MediaType.TEXT_PLAIN) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.valueOf("text/plain;charset=UTF-8")) }
            }
            .andReturn()
            .response
            .contentAsString

        val api = Yaml.mapper().readValue(yaml, OpenAPI::class.java)
        assertThat(api.tags).isNotEmpty
    }

    @Test
    fun testInclude() {

    }

    @Test
    fun testExclude() {

    }

    @ParameterizedTest
    @ValueSource(strings = ["servers"])
    fun testGetByName(name: String) {
        val yaml = applicationProperties.apis
            .first { it.name == name }.file
            .inputStream.readAllBytes()
            .decodeToString()

        mockMvc.get("/api/v1/openapi/$name") { accept(MediaType.TEXT_PLAIN) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                string(yaml)}
            }
    }

}