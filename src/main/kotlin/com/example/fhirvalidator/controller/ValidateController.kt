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
    @PostMapping("/\$validate")
    fun validate(@RequestBody input: String): String? {
        val jsonParser = fhirContext.newJsonParser()
        val inputResource = jsonParser.parseResource(input)
        messageDefinitionApplier.applyMessageDefinition(inputResource)
        val result = validator.validateWithResult(inputResource).toOperationOutcome()
        return jsonParser.encodeResourceToString(result)
    }
}
