package uk.nhs.nhsdigital.fhirvalidator

import mu.KLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan
import uk.nhs.nhsdigital.fhirvalidator.configuration.*


@SpringBootApplication
@ServletComponentScan
@EnableConfigurationProperties(TerminologyValidationProperties::class,FHIRServerProperties::class)
open class FhirValidatorApplication : ApplicationRunner {
    companion object : KLogging()

    override fun run(args: ApplicationArguments?) {
        logger.debug("EXECUTING THE APPLICATION")
        if (args != null) {
            for (opt in args.optionNames) {
                FhirValidatorApplication.logger.debug("args: {}", opt)
            }
        }
    }


}

fun main(args: Array<String>) {
    FhirValidatorApplication.logger.debug("STARTING THE APPLICATION")
    for (i in 0 until args.size) {
        FhirValidatorApplication.logger.debug("args[{}]: {}", i, args[i])
    }
    runApplication<FhirValidatorApplication>(*args)
}
