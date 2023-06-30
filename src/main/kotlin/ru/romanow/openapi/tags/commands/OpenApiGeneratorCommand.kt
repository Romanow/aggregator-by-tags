package ru.romanow.openapi.tags.commands

import io.swagger.v3.oas.models.OpenAPI
import org.springframework.shell.CompletionContext
import org.springframework.shell.CompletionProposal
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import org.springframework.shell.standard.ValueProvider
import org.springframework.stereotype.Component

@ShellComponent
class OpenApiGenerator {

    @ShellMethod("get")
    fun get(@ShellOption(valueProvider = ApiValueProvider::class) file: String): String {
        return object {}.javaClass.getResource(file)!!.readText()
    }

    @ShellMethod("generate")
    fun generate(
        @ShellOption(defaultValue = INCLUDE_ALL) include: String,
        @ShellOption(defaultValue = EXCLUDE_NONE) exclude: String,
    ) {

    }

    companion object {
        private const val INCLUDE_ALL = "all"
        private const val EXCLUDE_NONE = "none"

    }
}

@Component
class ApiValueProvider(
    private val apis: Map<String, OpenAPI>,
) : ValueProvider {
    override fun complete(completionContext: CompletionContext): List<CompletionProposal> {
        return apis.keys.map { CompletionProposal(it) }
    }
}