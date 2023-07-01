package ru.romanow.openapi.tags

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
internal class OpenapiGeneratorByTagsApplicationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun testAll() {
        mockMvc.get("/api/v1/openapi/all") { accept(MediaType.TEXT_PLAIN) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.valueOf("text/plain;charset=UTF-8")) }
            }
    }
}