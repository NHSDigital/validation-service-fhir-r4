package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class ApplicationConfiguration {
    @Bean
    fun fhirContext(): FhirContext {
        return FhirContext.forR4()
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}