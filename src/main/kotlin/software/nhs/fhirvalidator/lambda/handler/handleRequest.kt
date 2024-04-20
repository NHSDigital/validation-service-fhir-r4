package software.nhs.fhirvalidator.lambda.handler

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import software.nhs.fhirvalidator.lambda.configuration.ApplicationConfiguration
import software.nhs.fhirvalidator.lambda.configuration.PackageConfiguration
import software.nhs.fhirvalidator.common.controller.ValidateController
import software.nhs.fhirvalidator.common.util.createOperationOutcome
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.IOException
import java.util.stream.Stream
import java.nio.charset.StandardCharsets
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.OperationOutcome
import io.github.oshai.kotlinlogging.KotlinLogging

@SpringBootApplication(scanBasePackages = ["software.nhs.fhirvalidator.lambda"])
class handleRequest : RequestStreamHandler {
    private final var validateController: ValidateController
    private lateinit var fhirContext: FhirContext
    private val logger = KotlinLogging.logger {} 
    init {
        val applicationConfiguration = getApplicationContext().getBean(ApplicationConfiguration::class.java)
        val packageConfiguration = getApplicationContext().getBean(PackageConfiguration::class.java)
        val fhirContext = applicationConfiguration.fhirContext()
        val npmPackages = packageConfiguration.npmPackages()
        validateController = ValidateController(fhirContext, npmPackages)
        logger.info{"Validator is ready"}
    }
    override fun handleRequest(inputStream: InputStream?, outputStream: OutputStream?, context: Context?) {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            var length: Int
            while (inputStream!!.read(buffer).also { length = it } != -1) {
                result.write(buffer, 0, length)
            }
            val rawInput = result.toString(StandardCharsets.UTF_8)
            logger.info{rawInput}
  
            val validatorResult = parseAndValidateResource(rawInput)
            val validatorResultAsString = fhirContext.newJsonParser().encodeResourceToString(validatorResult)

            PrintWriter(outputStream).use { writer ->
                writer.print(validatorResultAsString)
            }

        } catch (ex: IOException) {
            logger.error(ex) {ex.message}
            throw RuntimeException("error in handleRequest", ex)
        }
    }

    fun parseAndValidateResource(input: String): OperationOutcome {
        return try {
            val inputResource = fhirContext.newJsonParser().parseResource(input)
            val resources = validateController.getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateController.validateResource(it) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (ex: DataFormatException) {
            logger.error(ex) {ex.message}
            createOperationOutcome(ex.message ?: "Invalid JSON", null)
        }
    }

    private fun getApplicationContext(): ConfigurableApplicationContext {
        return SpringApplication.run(handleRequest::class.java)
    }
}
