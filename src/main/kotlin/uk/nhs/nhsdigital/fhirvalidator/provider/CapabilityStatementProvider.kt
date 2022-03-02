package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Component
class CapabilityStatementProvider(private val fhirContext: FhirContext, private val npmPackages: List<NpmPackage>)  : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<CapabilityStatement> {
        return CapabilityStatement::class.java
    }
    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(@RequiredParam(name = CapabilityStatement.SP_URL) url: TokenParam): List<CapabilityStatement> {
        val list = mutableListOf<CapabilityStatement>()
        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    CapabilityStatement::class.java
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
