package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.OptionalParam
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.SearchParameter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import uk.nhs.nhsdigital.fhirvalidator.service.SearchParameterSupport
import java.nio.charset.StandardCharsets

@Component
class SearchParameterProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                               private val searchParameterSupport : SearchParameterSupport) : IResourceProvider {
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
    fun search(@OptionalParam(name = SearchParameter.SP_URL) url: TokenParam?,
               @OptionalParam(name = SearchParameter.SP_CODE) code: TokenParam?,
               @OptionalParam(name= SearchParameter.SP_BASE) base: TokenParam?
               ): List<SearchParameter> {
        if (url == null && code== null) throw UnprocessableEntityException("Both url and code may not be null")
        if (url != null && code != null) throw UnprocessableEntityException("Only one of url or code maybe supplied")
        if (base == null && code != null) throw UnprocessableEntityException("base must be supplied when code parameter is used")
        val list = mutableListOf<SearchParameter>()
        var searchParameter: SearchParameter? = null
        if (url!=null) {
            var decodeUri = java.net.URLDecoder.decode(url?.value , StandardCharsets.UTF_8.name());
            searchParameter = searchParameterSupport.getSearchParameterByUrl(decodeUri)
        } else {
            if (base != null && code != null) {
                searchParameter = searchParameterSupport.getSearchParameter(base.value,code.value)
            }
        }
        if (searchParameter != null) list.add(searchParameter)
        return list
    }
}
