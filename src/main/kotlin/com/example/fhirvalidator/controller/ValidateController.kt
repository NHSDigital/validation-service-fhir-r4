package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.MessageDefinitionApplier
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.OperationOutcome
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
        val result = validateResource(inputResource)
        return jsonParser.encodeResourceToString(result)
    }

    fun validateResource(resource: IBaseResource): IBaseOperationOutcome {
        val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(resource)
        if (messageDefinitionErrors != null) {
            return messageDefinitionErrors
        }
        return validator.validateWithResult(resource).toOperationOutcome()
    }
}
