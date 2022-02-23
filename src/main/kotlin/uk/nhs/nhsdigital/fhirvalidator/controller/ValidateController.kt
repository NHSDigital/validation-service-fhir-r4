package uk.nhs.nhsdigital.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import uk.nhs.nhsdigital.fhirvalidator.service.CapabilityStatementApplier
import uk.nhs.nhsdigital.fhirvalidator.service.MessageDefinitionApplier
import uk.nhs.nhsdigital.fhirvalidator.service.VerifyOAS
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
class ValidateController(
    private val fhirContext: FhirContext,
    private val validator: FhirValidator,
    private val messageDefinitionApplier: MessageDefinitionApplier,
    private val capabilityStatementApplier: CapabilityStatementApplier,
    private val verifyOAS:VerifyOAS

) {
    companion object : KLogging()


    @PostMapping("/\$validate", produces = ["application/json", "application/fhir+json","application/xml", "application/fhir+xml"])
    fun validate(
        @RequestBody input: String,
        @RequestHeader("x-request-id", required = false) requestId: String?,
        @RequestParam(required = false) profile: String?
    ): String {
        requestId?.let { logger.info("started processing message $it") }
        val result = parseAndValidateResource(input, profile)
        requestId?.let { logger.info("finished processing message $it") }
        return fhirContext.newJsonParser().encodeResourceToString(result)
    }

    @PostMapping("/\$verifyOAS", produces = ["application/json", "application/x-yaml"])
    fun validate(
        @RequestBody input: Optional<String>,
        @RequestParam(required = false) url: String?
    ): String {
        var openAPI : OpenAPI? = null
        if (url != null) {
            val parseOptions = ParseOptions()
            parseOptions.isResolve = true // implicit
          //  parseOptions.isResolveFully = true
            openAPI = OpenAPIV3Parser().readLocation(url,null,parseOptions).openAPI
        }
        else {
            if (input.isPresent) {
                openAPI = OpenAPIV3Parser().readContents(input.get()).openAPI
            } else {
                return  fhirContext.newJsonParser().encodeResourceToString(OperationOutcome()
                    .addIssue(OperationOutcome.OperationOutcomeIssueComponent()
                    .setSeverity(OperationOutcome.IssueSeverity.FATAL)
                        .setDiagnostics("If url is not provided, the OAS must be present in the payload")))
            }
        }

        if (openAPI !=null) {
            val results = verifyOAS.validate(openAPI)
            return  fhirContext.newJsonParser().encodeResourceToString(createOperationOutcome(results))
        }

        return  fhirContext.newJsonParser().encodeResourceToString(OperationOutcome().addIssue(OperationOutcome.OperationOutcomeIssueComponent()
            .setSeverity(OperationOutcome.IssueSeverity.FATAL).setDiagnostics("Unable to process OAS")))
    }

    fun parseAndValidateResource(input: String, profile: String?): OperationOutcome {
        return try {
            var inputResource : IBaseResource
            // TODO crude
            try {
                inputResource = fhirContext.newJsonParser().parseResource(input)
            } catch (ex : DataFormatException) {
                if (!ex.message?.contains("was: '<'")!!) throw ex
                inputResource = fhirContext.newXmlParser().parseResource(input)
            }
            val resources = getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateResource(it, profile) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (e: DataFormatException) {
            logger.error("Caught parser error", e)
            createOperationOutcome(e.message ?: "Invalid JSON", null)
        }
    }

    fun validateResource(resource: IBaseResource, profile: String?): OperationOutcome? {
        if (profile != null) return validator.validateWithResult(resource, ValidationOptions().addProfile(profile)).toOperationOutcome() as? OperationOutcome
        capabilityStatementApplier.applyCapabilityStatementProfiles(resource)
        val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(resource)
        if (messageDefinitionErrors != null) {
            return messageDefinitionErrors
        }
        return validator.validateWithResult(resource).toOperationOutcome() as? OperationOutcome
    }

    fun getResourcesToValidate(inputResource: IBaseResource?): List<IBaseResource> {
        if (inputResource == null) {
            return emptyList()
        }

        if (inputResource is Bundle && inputResource.type == Bundle.BundleType.SEARCHSET) {
            val bundleResources = inputResource.entry.map { it.resource }
            if (bundleResources.all { it.resourceType == ResourceType.Bundle }) {
                return bundleResources
            }
        }

        return listOf(inputResource)
    }
}
