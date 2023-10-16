package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class ApplicationConfiguration {
    @Bean
    fun fhirContext(): FhirContext {
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
