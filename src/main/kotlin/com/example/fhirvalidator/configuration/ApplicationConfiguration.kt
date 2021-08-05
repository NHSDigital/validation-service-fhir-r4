package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.context.support.GenericWebApplicationContext

@Configuration
class ApplicationConfiguration {

    @Autowired
    lateinit var applicationContext: GenericWebApplicationContext

    @Bean
    fun fhirContext(): FhirContext {
        val strictErrorHandler = StrictErrorHandler()
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(strictErrorHandler)
        return fhirContext
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
