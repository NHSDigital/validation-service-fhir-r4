package software.nhs.fhirvalidator.webserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FhirValidatorWebserver

fun main(args: Array<String>) {
    runApplication<FhirValidatorWebserver>(*args)
}
