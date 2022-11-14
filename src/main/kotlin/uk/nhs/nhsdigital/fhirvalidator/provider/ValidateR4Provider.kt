package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.utils.FHIRPathEngine
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.CapabilityStatementApplier
import uk.nhs.nhsdigital.fhirvalidator.service.MessageDefinitionApplier
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

@Component
class ValidateR4Provider (
    @Qualifier("R4") private val fhirContext: FhirContext,
    @Qualifier("SupportChain") private val supportChain: IValidationSupport,
    private val validator: FhirValidator,
    private val messageDefinitionApplier: MessageDefinitionApplier,
    private val capabilityStatementApplier: CapabilityStatementApplier

) {
    companion object : KLogging()

    @Operation(name = "\$fhirpathEvaluate", idempotent = true)
    @Throws(Exception::class)
    fun fhirpathEvaluate(
        servletRequest: HttpServletRequest,
        @ResourceParam resource: IBaseResource?,
        @OperationParam(name="expression") expressionUrl : String?
    ): Parameters {
        // This is code to explore fhirpath it is not meant to work.
        var expression = expressionUrl ?: servletRequest.getParameter("expression")
        val returnResult = Parameters()
        if (expression==null) return returnResult
        var hapiWorkerContext = HapiWorkerContext(fhirContext,supportChain)
        var fhirPathEngine = FHIRPathEngine(hapiWorkerContext)
       // var expression = "identifier.where(system='https://fhir.nhs.uk/Id/nhs-number').exists().not() or (identifier.where(system='https://fhir.nhs.uk/Id/nhs-number').exists()  and identifier.where(system='https://fhir.nhs.uk/Id/nhs-number').value.matches('^([456789]{1}[0-9]{9})\$'))"
        var decode = URLDecoder.decode(expression, StandardCharsets.UTF_8.name())
        System.out.println(expression)
        System.out.println(decode)
        var result = fhirPathEngine.evaluate(resource as Resource,decode)


        if (result is List<*>)
        {
            for(base in result) {
                if (base is Type) {
                    returnResult.addParameter(Parameters.ParametersParameterComponent()
                        .setName("result")
                        .addPart(Parameters.ParametersParameterComponent().setName("expression").setValue(StringType().setValue(expression)))
                        .addPart(Parameters.ParametersParameterComponent().setName("result").setValue(base as Type?))
                    )
                }

            }
        }

        return returnResult
    }

    @Operation(name = "\$convert", idempotent = true)
    @Throws(Exception::class)
    fun convertJson(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        return resource
    }
/*
 Move to a STU3 RestfulServer, is asuming input is R4 at present
    @Operation(name = "\$convertR4", idempotent = true)
    @Throws(java.lang.Exception::class)
    fun convertR4(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        val convertor = VersionConvertor_30_40(BaseAdvisor_30_40())
        val resourceR3 = resource as Resource
        return convertor.convertResource(resourceR3)
    }
*/
    @Validate
    fun validate(
        servletRequest: HttpServletRequest,
        @ResourceParam resource: IBaseResource,
        @Validate.Profile parameterResourceProfile: String?
    ): MethodOutcome {
        var profile = parameterResourceProfile ?: servletRequest.getParameter("profile")
        if (profile!= null) profile = URLDecoder.decode(profile, StandardCharsets.UTF_8.name());
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
            logger.error("Caught parser error", e)
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

        if (inputResource is Bundle
            && inputResource.type == Bundle.BundleType.SEARCHSET) {
            val bundleEntries = inputResource.entry
                .map { it }
            val bundleResources = bundleEntries.map { it.resource }
            if (bundleResources.all { it.resourceType == ResourceType.Bundle }) {
                return bundleResources
            }
        }

        return listOf(inputResource)
    }
}
