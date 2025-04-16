package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class ApplicationConfiguration {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun fhirContext(): FhirContext {
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(StrictErrorHandler())

        val validatorVersion = System.getenv("validatorVersion") ?: "unknown"
        val commitSha = System.getenv("commitSha") ?: "unknown"
        logger.info{ "validatorVersion: $validatorVersion"}
        logger.info{ "commitSha: $commitSha"}

        return fhirContext
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
