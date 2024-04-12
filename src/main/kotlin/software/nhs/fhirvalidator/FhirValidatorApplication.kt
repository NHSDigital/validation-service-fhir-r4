package software.nhs.fhirvalidator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FhirValidatorApplication

fun main(args: Array<String>) {
    runApplication<FhirValidatorApplication>(*args)
}
