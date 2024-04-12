package software.nhs.fhirvalidator.webserver

import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FhirValidatorWebServer {
    fun main(args: Array<String>) {
        runApplication<FhirValidatorWebServer>(*args)
    }
}
