package com.example.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import org.hl7.fhir.r4.model.Bundle
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidateController(
        private val fhirContext: FhirContext,
        private val validator: FhirValidator,
        private val messageDefinitionApplier: MessageDefinitionApplier
) {
    //TODO - get the message definition from the message header instead of a request parameter?
    @PostMapping("/\$validate")
    fun validate(@RequestBody input: String, @RequestParam messageDefinition: String?): String? {
        val jsonParser = fhirContext.newJsonParser()
        val inputResource = jsonParser.parseResource(input)
        messageDefinition?.let { messageDefinitionApplier.applyMessageDefinition(inputResource, it) }
        val result = validator.validateWithResult(inputResource).toOperationOutcome()
        return jsonParser.encodeResourceToString(result)
    }
}
