package uk.nhs.nhsdigital.fhirvalidator

import uk.nhs.nhsdigital.fhirvalidator.configuration.TerminologyValidationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan
import uk.nhs.nhsdigital.fhirvalidator.configuration.FHIRServerProperties

@SpringBootApplication
@ServletComponentScan
@EnableConfigurationProperties(TerminologyValidationProperties::class,FHIRServerProperties::class)
open class FhirValidatorApplication

fun main(args: Array<String>) {
    runApplication<FhirValidatorApplication>(*args)
}
