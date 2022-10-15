package uk.nhs.nhsdigital.fhirvalidator.providerR5

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult
import ca.uhn.fhir.context.support.IValidationSupport.ValueSetExpansionOutcome
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_50
import org.hl7.fhir.convertors.conv40_50.VersionConvertor_40_50
import org.hl7.fhir.r5.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Component
class ValueSetProviderR5 (@Qualifier("R5") private val fhirContext: FhirContext,
                          private val supportChain: ValidationSupportChain,
                          private val npmPackages: List<NpmPackage>) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<ValueSet> {
        return ValueSet::class.java
    }
    private val validationSupportContext = ValidationSupportContext(supportChain)

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)

    companion object : KLogging()

    val convertor = VersionConvertor_40_50(BaseAdvisor_40_50())

    @Search
    fun search(@RequiredParam(name = ValueSet.SP_URL) url: TokenParam): List<ValueSet> {
        val list = mutableListOf<ValueSet>()
        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    org.hl7.fhir.r4.model.ValueSet::class.java
                )) {
                    if (resource.url.equals(url.value)) {
                        if (resource.id == null) resource.setId(url.value)
                        list.add(convertor.convertResource(resource) as ValueSet)
                    }
                }
            }
        }
        return list
    }

    @Operation(name = "\$validate-code", idempotent = true)
    fun validateCode (
        @OperationParam(name = "url") url: String?,
        @OperationParam(name = "context") context: String?,
        @ResourceParam valueSet: ValueSet?,
        @OperationParam(name = "valueSetVersion") valueSetVersion: String?,
        @OperationParam(name = "code") code: String?,
        @OperationParam(name = "system") system: String?,
        @OperationParam(name = "systemVersion") systemVersion: String?,
        @OperationParam(name = "display") display: String?,
        @OperationParam(name = "coding") coding: TokenParam?,
        @OperationParam(name = "codeableConcept") codeableConcept: CodeableConcept?,
        @OperationParam(name = "date") date: DateParam?,
        @OperationParam(name = "abstract") abstract: BooleanType?,
        @OperationParam(name = "displayLanguage") displayLanguage: CodeType?
    ) : OperationOutcome {
        val input = OperationOutcome()
        input.issueFirstRep.severity = OperationOutcome.IssueSeverity.INFORMATION
        if (code != null) {
            val conceptValidaton = ConceptValidationOptions()
            var validationResult: CodeValidationResult? =
                supportChain.validateCode(this.validationSupportContext, conceptValidaton, system, code, display, url)

            if (validationResult != null) {
                //logger.info(validationResult?.code)
                if (validationResult.severity != null) {
                    when (validationResult.severity) {
                        IValidationSupport.IssueSeverity.ERROR -> input.issueFirstRep.severity = OperationOutcome.IssueSeverity.ERROR;
                        IValidationSupport.IssueSeverity.WARNING -> input.issueFirstRep.severity = OperationOutcome.IssueSeverity.WARNING;
                        else -> {}
                    }
                }
                input.issueFirstRep.diagnostics = validationResult.message
                //logger.info(validationResult?.message)
            }
        }
        return input;
    }

    @Operation(name = "\$expand", idempotent = true)
    fun expand(@ResourceParam valueSet: ValueSet?,
               @OperationParam(name = ValueSet.SP_URL) url: TokenParam? ): ValueSet? {
        if (url == null && valueSet == null) throw UnprocessableEntityException("Both resource and url can not be null")
        var valueSetR4: ValueSet? = null
        if (url != null) {
            var valueSets = url?.let { search(it) }
            if (valueSets != null) {
                if (valueSets.isNotEmpty())  {
                    if (valueSetR4 != null) {
                        valueSetR4= valueSets[0]
                    }
                }
            }
        } else {
            valueSetR4 = valueSet;
        }
        if (valueSetR4 != null) {
            var expansion: ValueSetExpansionOutcome? =
                supportChain.expandValueSet(this.validationSupportContext, ValueSetExpansionOptions(), valueSetR4)
            if (expansion != null) {
                if (expansion.valueSet is ValueSet) {
                    var newValueSet = expansion.valueSet as ValueSet
                    valueSetR4.expansion = newValueSet.expansion
                }
                if (expansion?.error != null) { throw UnprocessableEntityException(expansion?.error ) }

            }
            return valueSetR4;
        } else {
            throw UnprocessableEntityException("ValueSet not found");
        }

    }



}
