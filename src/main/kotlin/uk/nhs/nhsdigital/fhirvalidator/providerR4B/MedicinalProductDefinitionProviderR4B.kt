package uk.nhs.nhsdigital.fhirvalidator.providerR4B

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.ValueSet

import org.hl7.fhir.r5.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.provider.ValueSetProvider
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import uk.nhs.nhsdigital.fhirvalidator.shared.LookupCodeResultUK

@Component
class MedicinalProductDefinitionProviderR4B (@Qualifier("R5") private val fhirContext: FhirContext,
                                             private val supportChain: ValidationSupportChain,
                                             private val valueSetProvider: ValueSetProvider,
                                             private val npmPackages: List<NpmPackage>) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<MedicinalProductDefinition> {
        return MedicinalProductDefinition::class.java
    }
    private val validationSupportContext = ValidationSupportContext(supportChain)

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(
       // @OptionalParam(name = MedicinalProductDefinition.SP_IDENTIFIER) identifier : TokenParam?,
        @OptionalParam(name = MedicinalProductDefinition.SP_NAME) name : StringParam?
    ): List<MedicinalProductDefinition> {
        val list = mutableListOf<MedicinalProductDefinition>()
        if (name != null) {
            var valueSetR4: ValueSet? = null;

            var valueSets = valueSetProvider.search(TokenParam().setValue("https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"))
            if (valueSets.size>0) valueSetR4= valueSets[0];

            if (valueSetR4 != null) {
                var valueSetExpansionOptions = ValueSetExpansionOptions();
                valueSetR4.expansion = null; // remove any previous expansion
                valueSetExpansionOptions.filter = name.value
                var expansion: IValidationSupport.ValueSetExpansionOutcome? =
                    supportChain.expandValueSet(this.validationSupportContext, valueSetExpansionOptions, valueSetR4)
                if (expansion != null) {
                    if (expansion.valueSet is ValueSet) {
                        var newValueSet = expansion.valueSet as ValueSet
                        valueSetR4.expansion = newValueSet.expansion
                    }
                    if (expansion?.error != null) {
                        throw UnprocessableEntityException(expansion?.error)
                    }
                    if (valueSetR4.hasExpansion() && valueSetR4.expansion.hasContains())
                    {
                        for (content in valueSetR4.expansion.contains) {
                            var medicinalProductDefinition = MedicinalProductDefinition()
                            medicinalProductDefinition.id = content.code
                            medicinalProductDefinition.addName(MedicinalProductDefinition.MedicinalProductDefinitionNameComponent(content.display))

                            medicinalProductDefinition.addIdentifier()
                                .setSystem("https://dmd.nhs.uk")
                                .setValue(content.code)
                            medicinalProductDefinition.addIdentifier()
                                .setSystem("http://snomed.info/sct")
                                .setValue(content.code)
                            list.add(medicinalProductDefinition)
                        }
                    }
                }
              // Convert response into Medicinal Product Definition
            } else {
                throw UnprocessableEntityException("ValueSet not found");
            }
        }
        return list
    }


}
