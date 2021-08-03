package com.example.fhirvalidator.provider

import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Validate
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import com.example.fhirvalidator.util.createOperationOutcome
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
class ValidateProvider(
    private val validator: FhirValidator,
    private val messageDefinitionApplier: MessageDefinitionApplier,
    private val capabilityStatementApplier: CapabilityStatementApplier
) {
    companion object : KLogging()

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

    fun parseAndValidateResource(
        inputResource: IBaseResource,
        profile: String?
    ): OperationOutcome {
        return try {
            val resources = getResourcesToValidate(inputResource)
            val operationOutcomeList = resources.map { validateResource(it, profile) }
            val operationOutcomeIssues = operationOutcomeList.filterNotNull().flatMap { it.issue }
            return createOperationOutcome(operationOutcomeIssues)
        } catch (e: DataFormatException) {
            logger.error("Caught parser error", e)
            createOperationOutcome("Invalid JSON", null)
        }
    }

    fun validateResource(
        resource: IBaseResource, profile: String?
    ): OperationOutcome? {
        if (profile == null) {
            val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(resource)
            if (messageDefinitionErrors != null) {
                return messageDefinitionErrors
            }
            capabilityStatementApplier.applyCapabilityStatementProfiles(resource)
            return validator.validateWithResult(resource).toOperationOutcome() as? OperationOutcome
        } else {
            val validationOptions = ValidationOptions()
            validationOptions.addProfile(profile)
            return validator.validateWithResult(resource, validationOptions).toOperationOutcome() as? OperationOutcome
        }
    }

    fun getResourcesToValidate(inputResource: IBaseResource?): List<IBaseResource> {
        return if (inputResource == null) {
            emptyList()
        } else if (inputResource is Bundle && inputResource.type == Bundle.BundleType.SEARCHSET) {
            inputResource.entry.map { it.resource }
        } else {
            listOf(inputResource)
        }
    }
}
