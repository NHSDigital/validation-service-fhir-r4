package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import com.example.fhirvalidator.util.createOperationOutcome
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.hl7.fhir.instance.model.api.IBaseResource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidateController(
        private val fhirContext: FhirContext,
        private val validator: FhirValidator,
        private val messageDefinitionApplier: MessageDefinitionApplier,
        private val capabilityStatementApplier: CapabilityStatementApplier
) {
    companion object : KLogging()

    @PostMapping("/\$validate", produces = ["application/json", "application/fhir+json"])
    fun validate(@RequestBody input: String): String {
        val result = parseAndValidateResource(input)
        return fhirContext.newJsonParser().encodeResourceToString(result)
    }

    private fun parseAndValidateResource(input: String): IBaseOperationOutcome {
        return try {
            val inputResource = fhirContext.newJsonParser().parseResource(input)
            val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(inputResource)
            capabilityStatementApplier.applyCapabilityStatementProfiles(inputResource)
            messageDefinitionErrors ?: validator.validateWithResult(inputResource).toOperationOutcome()
        } catch (e: DataFormatException) {
            logger.error("Caught parser error", e)
            createOperationOutcome("Invalid JSON", null)
        }
    }
}
