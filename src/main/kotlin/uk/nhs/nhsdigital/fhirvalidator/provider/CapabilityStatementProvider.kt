package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.CapabilityStatement

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import java.nio.charset.StandardCharsets

@Component
class CapabilityStatementProvider(@Qualifier("R4") private val fhirContext: FhirContext,
                                  private val supportChain: ValidationSupportChain)  : IResourceProvider {
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
        var decodeUri = java.net.URLDecoder.decode(url.value, StandardCharsets.UTF_8.name());
        val resource = supportChain.fetchResource(CapabilityStatement::class.java,decodeUri)
        if (resource != null) list.add(resource)
        return list
    }
}
