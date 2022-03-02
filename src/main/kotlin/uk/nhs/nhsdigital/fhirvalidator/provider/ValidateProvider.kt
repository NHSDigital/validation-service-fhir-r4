package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Validate
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import mu.KLogging
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_40
import org.hl7.fhir.convertors.conv30_40.VersionConvertor_30_40
import org.hl7.fhir.dstu3.model.Resource
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.ResourceType
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.controller.VerifyController
import uk.nhs.nhsdigital.fhirvalidator.service.CapabilityStatementApplier
import uk.nhs.nhsdigital.fhirvalidator.service.MessageDefinitionApplier
import uk.nhs.nhsdigital.fhirvalidator.service.VerifyOAS
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import javax.servlet.http.HttpServletRequest

@Component
class ValidateProvider (private val fhirContext: FhirContext,
private val validator: FhirValidator,
private val messageDefinitionApplier: MessageDefinitionApplier,
private val capabilityStatementApplier: CapabilityStatementApplier,
private val verifyOAS: VerifyOAS

) {
    companion object : KLogging()

    @Operation(name = "\$convert", idempotent = true)
    @Throws(Exception::class)
    fun convertJson(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        return resource
    }

    @Operation(name = "\$convertR4", idempotent = true)
    @Throws(java.lang.Exception::class)
    fun convertR4(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        val convertor = VersionConvertor_30_40(BaseAdvisor_30_40())
        val resourceR3 = resource as Resource
        return convertor.convertResource(resourceR3)
    }

    @Validate
    fun validate(
        servletRequest: HttpServletRequest,
        @ResourceParam resource: IBaseResource,
        @Validate.Profile parameterResourceProfile: String?
    ): MethodOutcome {
        val profile = parameterResourceProfile ?: servletRequest.getParameter("profile")

        val operationOutcome = parseAndValidateResource(resource, profile)
        val methodOutcome = MethodOutcome()
        methodOutcome.operationOutcome = operationOutcome
        return methodOutcome
    }

    /* TODO HAPI ignores manual request and gives ContentType errors
    @Operation(name = "\$verifyOAS", manualRequest = true)
    fun verifyOAS(
        servletRequest: HttpServletRequest,
        @ResourceParam resource: IBaseResource,
        @OperationParam(name="url") url: StringType?
    ): OperationOutcome {
        return OperationOutcome()
    }

     */

    fun parseAndValidateResource(inputResource: IBaseResource, profile: String?): OperationOutcome {
        return try {
            val resources = getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateResource(it, profile) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (e: DataFormatException) {
            VerifyController.logger.error("Caught parser error", e)
            createOperationOutcome(e.message ?: "Invalid JSON", null)
        }
    }

    fun validateResource(resource: IBaseResource, profile: String?): OperationOutcome? {
        if (profile != null) return validator.validateWithResult(resource, ValidationOptions().addProfile(profile))
            .toOperationOutcome() as? OperationOutcome
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
