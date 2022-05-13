package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.SearchParameter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import uk.nhs.nhsdigital.fhirvalidator.service.SearchParameterSupport

@Component
class SearchParameterProvider (@Qualifier("R4") private val fhirContext: FhirContext, private val searchParameterSupport : SearchParameterSupport) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<SearchParameter> {
        return SearchParameter::class.java
    }

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(@RequiredParam(name = SearchParameter.SP_URL) url: TokenParam): List<SearchParameter> {
        val list = mutableListOf<SearchParameter>()
        val searchParameter =  searchParameterSupport.getSearchParameterByUrl(url.value)
        if (searchParameter != null) list.add(searchParameter)
        return list
    }
}
