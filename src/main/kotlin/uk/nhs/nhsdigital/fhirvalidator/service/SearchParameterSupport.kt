package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.support.IValidationSupport
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.SearchParameter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class SearchParameterSupport(@Qualifier("SupportChain") private val supportChain: IValidationSupport,
                             private val searchParameters : Bundle
) {

    fun getSearchParameter(originalResourceType: String, originalName : String) : SearchParameter? {
        val parameters = originalName.split(".")

        val modifiers = parameters.get(0).split(":")

        var name = modifiers.get(0)
        if (name.startsWith("_")) {
            name = name.removePrefix("_")
        }

        var searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/$originalResourceType-$name")

        if (searchParameter == null)
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/individual-$name")

        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/clinical-$name")
            if (searchParameter != null && !searchParameter.expression.contains(originalResourceType)) {
                searchParameter = null
            }
        }
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/conformance-$name")
            if (searchParameter != null && !searchParameter.expression.contains(originalResourceType)) {
                searchParameter = null
            }
        }
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/medications-$name")
            if (searchParameter != null && !searchParameter.expression.contains(originalResourceType)) {
                searchParameter = null
            }
        }

        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/Resource-$name")
        }

        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/DomainResource-$name")
        }
        if (searchParameter == null) {
            if (originalName.startsWith("_"))
                searchParameter = getSearchParameterByCode(originalName)
            else
                searchParameter = getSearchParameterByCode(name)
        }

        if (searchParameter == null) return null

        if (modifiers.size>1) {
            val modifier = modifiers.get(1)
            // Don't alter original
            searchParameter = searchParameter.copy()
            if (modifier == "identifier") {
                searchParameter.code += ":" + modifier
                searchParameter.type = Enumerations.SearchParamType.TOKEN
                searchParameter.expression += ".identifier | "+ searchParameter.expression +".where(resolve() is Resource).identifier"
            }
        }
        return searchParameter
    }

    fun getSearchParameterByUrl(url : String) : SearchParameter? {

        var searchParameter: SearchParameter? = null
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is SearchParameter) {
                if (resource.url.equals(url)) {
                    searchParameter = resource
                }
            }
        }

        /*
        for (resource in implementationGuideParser!!.getResourcesOfType(
            npmPackages,
            SearchParameter::class.java
        )) {
            if (resource.url.equals(url)) {
                return resource
            }
        }

        for (resource in implementationGuideParser!!.getResourcesOfType(
            npmPackages,
            SearchParameter::class.java
        )) {
            if (resource.url.equals(url)) {
                return resource
            }
        }

         */

        for (entry in searchParameters.entry) {
            if (entry.resource is SearchParameter) {
                if ((entry.resource as SearchParameter).url.equals(url)) {
                    searchParameter = entry.resource as SearchParameter
                }
            }
        }

        return searchParameter
    }

    fun getSearchParameterByCode(originalCode : String) : SearchParameter? {

        val codes=originalCode.split(":")
        val code= codes[0]

        var searchParameter: SearchParameter? = null
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is SearchParameter) {
                if (resource.code.equals(code)) {
                    searchParameter = resource
                }
            }
        }
        when(code) {
            "_sort" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("Order to sort results in (can repeat for inner sort orders)").setExpression("")
            }
            "_count" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.NUMBER).setDescription("Number of results per page").setExpression("")
            }
            "_include" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("Other resources to include in the search results that search matches point to").setExpression("")
            }
            "_revinclude" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("Other resources to include in the search results when they refer to search matches").setExpression("")

            }
            "_summary" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("Just return the summary elements (for resources where this is defined)").setExpression("")

            }
            "_contained" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("Whether to return resources contained in other resources in the search matches").setExpression("")

            }
            "_containedType" -> {
                searchParameter = SearchParameter().setCode(code).setType(Enumerations.SearchParamType.STRING).setDescription("If returning contained resources, whether to return the contained or container resources").setExpression("")

            }
        }

        return searchParameter
    }

}
