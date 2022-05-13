package uk.nhs.nhsdigital.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class ApplicationConfiguration {
    @Bean("R4")
    fun fhirR4Context(): FhirContext {
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean("STU3")
    fun fhirSTU3Context(): FhirContext {
        val fhirContext = FhirContext.forDstu3()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
