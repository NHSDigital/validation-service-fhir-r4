package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult
import ca.uhn.fhir.context.support.IValidationSupport.ValueSetExpansionOutcome
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import mu.KLogging
import org.apache.commons.lang3.StringUtils
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.CodingSupport
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import java.nio.charset.StandardCharsets

@Component
class ValueSetProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                        private val supportChain: ValidationSupportChain,
                        private val codingSupport: CodingSupport
) : IResourceProvider {
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

    @Search
    fun search(@RequiredParam(name = ValueSet.SP_URL) url: TokenParam): List<ValueSet> {
        val list = mutableListOf<ValueSet>()
        val resource = supportChain.fetchResource(ValueSet::class.java,java.net.URLDecoder.decode(url.value, StandardCharsets.UTF_8.name()))
        if (resource != null) list.add(resource)

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
                supportChain.validateCode(this.validationSupportContext, conceptValidaton, java.net.URLDecoder.decode(system, StandardCharsets.UTF_8.name()), code, display, java.net.URLDecoder.decode(url, StandardCharsets.UTF_8.name()))

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

    @Operation(name = "\$expandSCT", idempotent = true)
    fun subsumes (  @OperationParam(name = "filter") filter: String?,
                    @OperationParam(name = "count") count: IntegerType?,
                    @OperationParam(name = "includeDesignations") includeDesignations: BooleanType?
    ) : Parameters? {
        return codingSupport.search(filter,count,includeDesignations)
    }

    @Operation(name = "\$expandEcl", idempotent = true)
    fun eclExpand (  @OperationParam(name = "ecl", min = 1) filter: String?,
                     @OperationParam(name = "count") count: IntegerType?
    ) : Parameters? {
        return codingSupport.expandEcl(filter,count)
    }

    @Operation(name = "\$expand", idempotent = true)
    fun expand(@ResourceParam valueSet: ValueSet?,
               @OperationParam(name = ValueSet.SP_URL) url: TokenParam?,
                @OperationParam(name = "filter") filter: StringParam?): ValueSet? {
        if (url == null && valueSet == null) throw UnprocessableEntityException("Both resource and url can not be null")
        var valueSetR4: ValueSet? = null;
        if (url != null) {
            var valueSets = url.let { search(it) }
            if (valueSets != null) {
                if (valueSets.isNotEmpty())  {
                    valueSetR4= valueSets[0]
                }
            };
        } else {
            valueSetR4 = valueSet;
        }
        if (valueSetR4 != null) {
            var valueSetExpansionOptions = ValueSetExpansionOptions();
            valueSetR4.expansion = null; // remove any previous expansion
            if (filter != null) valueSetExpansionOptions.filter = filter.value
            var expansion: ValueSetExpansionOutcome? =
                supportChain.expandValueSet(this.validationSupportContext, valueSetExpansionOptions, valueSetR4)
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
