package software.nhs.fhirvalidator.webserver.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import software.nhs.fhirvalidator.common.service.CapabilityStatementApplier
import software.nhs.fhirvalidator.common.service.MessageDefinitionApplier
import software.nhs.fhirvalidator.common.service.ImplementationGuideParser
import software.nhs.fhirvalidator.common.controller.ValidateController
import software.nhs.fhirvalidator.common.configuration.ValidationConfiguration
import software.nhs.fhirvalidator.common.util.createOperationOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidateController(
    private val fhirContext: FhirContext,
    private val npmPackages: List<NpmPackage>,
) {
    private val logger = KotlinLogging.logger {} 
    private val validateController = ValidateController(fhirContext, npmPackages)

    @PostMapping("/\$validate", produces = ["application/json", "application/fhir+json"])
    fun validate(
        @RequestBody input: String,
        @RequestHeader("x-request-id", required = false) requestId: String?
    ): String {
        requestId?.let { logger.info { "started processing message $it" } }
        val result = parseAndValidateResource(input)
        requestId?.let { logger.info { "finished processing message $it"} }
        return fhirContext.newJsonParser().encodeResourceToString(result)
    }

    fun parseAndValidateResource(input: String): OperationOutcome {
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
