package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.validation.FhirValidator
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Component
class StructureDefinitionProvider (
    @Qualifier("R4") private val fhirContext: FhirContext,
    private val supportChain : ValidationSupportChain) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<StructureDefinition> {
        return StructureDefinition::class.java
    }

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(@RequiredParam(name = StructureDefinition.SP_URL) url: TokenParam): List<StructureDefinition> {
        val list = mutableListOf<StructureDefinition>()
        for (resource in supportChain.fetchAllStructureDefinitions()) {
            val structureDefinition = resource as StructureDefinition
            if (structureDefinition.url.equals(url.value)) {
                resource.setId(url.value);

                list.add(structureDefinition)
            }
        }
        return list
    }
}
