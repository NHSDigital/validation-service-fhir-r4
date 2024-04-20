package software.nhs.fhirvalidator.webserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FhirValidatorLambda

fun main(args: Array<String>) {
    runApplication<FhirValidatorLambda>(*args)
}
