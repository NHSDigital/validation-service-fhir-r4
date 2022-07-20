package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport.ValueSetExpansionOutcome
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.OperationParam
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import ca.uhn.fhir.validation.FhirValidator
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ConceptMap
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.r4.model.ValueSet
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Component
class ValueSetProvider (@Qualifier("R4") private val fhirContext: FhirContext,
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


    @Search
    fun search(@RequiredParam(name = ValueSet.SP_URL) url: TokenParam): List<ValueSet> {
        val list = mutableListOf<ValueSet>()
        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    ValueSet::class.java
                )) {
                    if (resource.url.equals(url.value)) {
                        if (resource.id == null) resource.setId(url.value)
                        list.add(resource)
                    }
                }
            }
        }
        return list
    }

    @Operation(name = "\$expand", idempotent = true)
    fun expand(@ResourceParam resource: ValueSet?, @OperationParam(name = ValueSet.SP_URL) url: TokenParam? ): ValueSet? {
        if (url == null && resource == null) throw UnprocessableEntityException("Both resource and url can not be null")
        var valueSet : ValueSet? = null;
        if (url != null) {
            var valueSets = url?.let { search(it) }
            valueSet= valueSets[0];
        } else {
            valueSet = resource;
        }
        if (valueSet != null) {
            var expansion: ValueSetExpansionOutcome? =
                supportChain.expandValueSet(this.validationSupportContext, ValueSetExpansionOptions(), valueSet)
            if (expansion != null) {
                if (expansion.valueSet is ValueSet) {
                    var newValueSet = expansion.valueSet as ValueSet
                    valueSet.expansion = newValueSet.expansion
                }
                if (expansion?.error != null) { throw UnprocessableEntityException(expansion?.error ) }

            }
            return valueSet;
        } else {
            throw UnprocessableEntityException("ValueSet not found");
        }

    }
}
