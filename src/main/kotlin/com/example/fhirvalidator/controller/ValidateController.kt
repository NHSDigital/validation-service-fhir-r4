package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import com.example.fhirvalidator.util.createOperationOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.ResourceType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidateController(
    private val fhirContext: FhirContext,
    private val validator: FhirValidator,
    private val messageDefinitionApplier: MessageDefinitionApplier,
    private val capabilityStatementApplier: CapabilityStatementApplier
) {
    private val logger = KotlinLogging.logger {} 

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

    // use Synchronized here to ensure single thread as newJsonParser is not thread safe
    @Synchronized
    fun parseAndValidateResource(input: String): OperationOutcome {
        return try {
            val inputResource = fhirContext.newJsonParser().parseResource(input)
            val resources = getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateResource(it) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (e: DataFormatException) {
            logger.error(e) { "Caught parser error" }
            createOperationOutcome(e.message ?: "Invalid JSON", null)
        }
    }
    
    fun validateResource(resource: IBaseResource): OperationOutcome? {
        capabilityStatementApplier.applyCapabilityStatementProfiles(resource)
        val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(resource)
        if (messageDefinitionErrors != null) {
            return messageDefinitionErrors
        }
        validator.setConcurrentBundleValidation(false)
        val result = validator.validateWithResult(resource).toOperationOutcome() as? OperationOutcome
        var hasError = false
        for (issue in result?.issue!!) {
                if (issue.severity.equals(OperationOutcome.IssueSeverity.ERROR)) {
                    println("Error found checking file. Error: ${issue.diagnostics}")
                    hasError = true
                }
            }
        if (hasError) {
            val string_repr = fhirContext.newJsonParser().encodeResourceToString(resource)
            println("There was an error")
            println(string_repr)
        }
        return result
    }

    fun getResourcesToValidate(inputResource: IBaseResource?): List<IBaseResource> {
        if (inputResource == null) {
            return emptyList()
        }

        if ((inputResource is Bundle) && (inputResource.type == Bundle.BundleType.SEARCHSET)) {
            val bundleResources = inputResource.entry.map { it.resource }
            if (bundleResources.all { it.resourceType == ResourceType.Bundle }) {
                return bundleResources
            }
        }

        return listOf(inputResource)
    }
}
