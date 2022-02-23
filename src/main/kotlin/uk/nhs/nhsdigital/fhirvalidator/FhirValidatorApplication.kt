package uk.nhs.nhsdigital.fhirvalidator

import uk.nhs.nhsdigital.fhirvalidator.configuration.TerminologyValidationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(TerminologyValidationProperties::class)
class FhirValidatorApplication

fun main(args: Array<String>) {
    runApplication<FhirValidatorApplication>(*args)
}
