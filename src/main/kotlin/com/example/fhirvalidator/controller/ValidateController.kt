package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Validate
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.ValidationModeEnum
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import com.example.fhirvalidator.util.createOperationOutcome
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.hl7.fhir.instance.model.api.IBaseResource
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder
import javax.servlet.http.HttpServletRequest

@Component
class ValidateController
    (private val fhirContext: FhirContext,
    private val validator: FhirValidator,
    private val messageDefinitionApplier: MessageDefinitionApplier,
    private val capabilityStatementApplier: CapabilityStatementApplier
) {
    companion object : KLogging()

            /*
    @PostMapping("/\$validate", produces = ["application/json", "application/fhir+json"])
    fun validate(
        @RequestBody input: String,
        @RequestHeader("x-request-id", required = false) requestId: String?
    ): String {
        requestId?.let { logger.info("started processing message $it") }
        val result = parseAndValidateResource(input)
        requestId?.let { logger.info("finished processing message $it") }
        return fhirContext.newJsonParser().encodeResourceToString(result)
    }*/

    @Validate
    fun validate(theServletRequest : HttpServletRequest, @ResourceParam resource : IBaseResource,
                 @Validate.Profile theProfile : String?,
                 @Validate.Mode theMode : ValidationModeEnum?
    ) : MethodOutcome {
        // Probably a better way of doing this. Presume @Validate.Profile is to support the Parameter POST operation
        var profile : String? = theProfile
        if (theServletRequest.queryString != null && theProfile == null) {
            val query_pairs: MutableMap<String, String> = LinkedHashMap()
            val query = theServletRequest.queryString
            val pairs = query.split("&".toRegex()).toTypedArray()
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                query_pairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
            profile = query_pairs["profile"]
        }
        val operationOutcome = parseAndValidateResource(resource, profile)
        val methodOutcome = MethodOutcome()
        methodOutcome.setOperationOutcome(operationOutcome)
        return methodOutcome;
    }

    private fun parseAndValidateResource(inputResource : IBaseResource, theProfile : String?): IBaseOperationOutcome {
        return try {
            // KGM 18/7/2021 val inputResource = fhirContext.newJsonParser().parseResource(input)
            val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(inputResource)
            capabilityStatementApplier.applyCapabilityStatementProfiles(inputResource)
            messageDefinitionErrors ?: validator.validateWithResult(inputResource).toOperationOutcome()
        } catch (e: DataFormatException) {
            logger.error("Caught parser error", e)
            createOperationOutcome("Invalid JSON", null)
        }
    }
}
