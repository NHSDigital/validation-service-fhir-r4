package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport.ValueSetExpansionOutcome
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.OperationParam
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.validation.FhirValidator
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
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
    fun expand(@OperationParam(name = ValueSet.SP_URL) url: TokenParam): ValueSet? {
        var valueSets = search(url)
        if (valueSets.size == 0) return null;
        var expansion : ValueSetExpansionOutcome? = supportChain.expandValueSet(this.validationSupportContext, ValueSetExpansionOptions(),valueSets[0])
      //  System.out.println(expansion.toString())
        if (expansion != null && expansion.valueSet is ValueSet) {
            var newValueSet = expansion.valueSet as ValueSet
            valueSets[0].expansion = newValueSet.expansion
        }
        return valueSets[0];
    }
}
