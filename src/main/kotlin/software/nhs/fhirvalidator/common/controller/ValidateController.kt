package software.nhs.fhirvalidator.common.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import software.nhs.fhirvalidator.common.service.CapabilityStatementApplier
import software.nhs.fhirvalidator.common.service.MessageDefinitionApplier
import software.nhs.fhirvalidator.common.service.ImplementationGuideParser
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
    private val npmPackages: List<NpmPackage>
) {
    private val logger = KotlinLogging.logger {} 
    private val implementationGuideParser = ImplementationGuideParser(fhirContext)
    private val validationConfiguration = ValidationConfiguration(implementationGuideParser)
    private val terminologyValidationSupport = validationConfiguration.terminologyValidationSupport(fhirContext)
    private val supportChain = validationConfiguration.validationSupportChain(fhirContext, terminologyValidationSupport, npmPackages)
    private val instanceValidator = validationConfiguration.instanceValidator(supportChain)
    private val validator = validationConfiguration.validator(fhirContext, instanceValidator)
    private val messageDefinitionApplier = MessageDefinitionApplier(implementationGuideParser, npmPackages)
    private val capabilityStatementApplier = CapabilityStatementApplier(implementationGuideParser, npmPackages)

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
        return validator.validateWithResult(resource).toOperationOutcome() as? OperationOutcome
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
