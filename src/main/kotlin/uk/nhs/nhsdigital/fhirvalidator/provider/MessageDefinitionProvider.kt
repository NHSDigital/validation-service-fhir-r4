package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.MessageDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Component
class MessageDefinitionProvider (private val fhirContext: FhirContext, private val npmPackages: List<NpmPackage>) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<MessageDefinition> {
        return MessageDefinition::class.java
    }

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(@RequiredParam(name = MessageDefinition.SP_URL) url: TokenParam): List<MessageDefinition> {
        val list = mutableListOf<MessageDefinition>()
        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    MessageDefinition::class.java
                )) {
                    if (resource.url.equals(url.value)) {
                        list.add(resource)
                    }
                }
            }
        }
        return list
    }
}
