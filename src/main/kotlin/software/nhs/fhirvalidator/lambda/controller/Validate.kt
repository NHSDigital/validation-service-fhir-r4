package software.nhs.fhirvalidator.lambda.controller

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import org.apache.logging.log4j.LogManager
import org.hl7.fhir.utilities.npm.NpmPackage
import org.hl7.fhir.r4.model.OperationOutcome

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import io.github.oshai.kotlinlogging.KotlinLogging
import software.nhs.fhirvalidator.common.controller.ValidateController
import software.nhs.fhirvalidator.common.util.createOperationOutcome

class Validate(
    private val fhirContext: FhirContext,
    private val npmPackages: List<NpmPackage>,
) {
    private val logger = KotlinLogging.logger {} 
    private val validateController = ValidateController(fhirContext, npmPackages)

    @Throws(IOException::class)
    fun handleRequest(input: String): OperationOutcome {
        return try {
            val inputResource = fhirContext.newJsonParser().parseResource(input)
            val resources = validateController.getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateController.validateResource(it) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (e: DataFormatException) {
            logger.error(e) { "Caught parser error" }
            createOperationOutcome(e.message ?: "Invalid JSON", null)
        }
    }
}
