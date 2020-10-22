package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.MessageDefinitionApplier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidateController(
        private val fhirContext: FhirContext,
        private val validator: FhirValidator,
        private val messageDefinitionApplier: MessageDefinitionApplier
) {
    @PostMapping("/\$validate", produces = ["application/json"])
    fun validate(@RequestBody input: String): String {
        val jsonParser = fhirContext.newJsonParser()
        val inputResource = jsonParser.parseResource(input)
        val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(inputResource)
        if (messageDefinitionErrors != null) {
            return jsonParser.encodeResourceToString(messageDefinitionErrors)
        }
        val result = validator.validateWithResult(inputResource).toOperationOutcome()
        return jsonParser.encodeResourceToString(result)
    }
}
