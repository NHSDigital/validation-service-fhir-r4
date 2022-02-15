package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.validation.FhirValidator
//import io.swagger.models.parameters.QueryParameter
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.QueryParameter
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage

class VerifyOAS(private val ctx: FhirContext?,
                private val fhirValidator: FhirValidator,
                private val npmPackages: List<NpmPackage>?,
                private val supportChain: IValidationSupport,
                private val searchParameters : Bundle)
{

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)


    public fun validate(openAPI : OpenAPI) : List<OperationOutcome.OperationOutcomeIssueComponent> {
        // check all examples validate
        // check all paths are correct
        var outcomes = mutableListOf<OperationOutcome.OperationOutcomeIssueComponent>()
        for (apiPaths in openAPI.paths) {
            val operationIssue = OperationOutcome.OperationOutcomeIssueComponent()
            operationIssue.location.add(StringType(apiPaths.key))
            operationIssue.code = OperationOutcome.IssueType.INFORMATIONAL
            operationIssue.severity = OperationOutcome.IssueSeverity.INFORMATION
            outcomes.add(operationIssue)
            var path = apiPaths.key.removePrefix("/FHIR/R4")

            // check all paths are correct
            path = path.removePrefix("/")
            val paths = path.split("/")
            val resourceType = paths[0]
            var operation = ""
            if (paths.size> 1) operation = paths[1]
            if (paths[0].startsWith("$")) operation = paths[0]


            if (!resourceType.startsWith("$") && !resourceType.equals("metadata")) {
                val codeSystem = supportChain.fetchCodeSystem("http://hl7.org/fhir/resource-types")
                if (codeSystem is CodeSystem && !inCodeSystem(codeSystem,resourceType)) {
                    operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                    operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                    operationIssue.diagnostics = "Unable to find resource type of: "+resourceType
                    //operationIssue.
                }
            }
            if (operation.isNotEmpty() && operation.startsWith("$")) {
                val operationDefinition = getOperationDefinition(operation)
                if (operationDefinition == null) {
                    operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                    operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                    operationIssue.diagnostics = "Unable to find FHIR operation for: "+operation
                }
            }

            // check all parameters
            if (apiPaths.value.get != null && apiPaths.value.get.parameters != null) {

                for (apiParameter in apiPaths.value.get.parameters) {

                    if (apiParameter is QueryParameter) {
                        val apiParameter = apiParameter as QueryParameter
                        val operationIssue = OperationOutcome.OperationOutcomeIssueComponent()

                        operationIssue.location.add(StringType(apiPaths.key + "/get/" + apiParameter.name))
                        operationIssue.code = OperationOutcome.IssueType.INFORMATIONAL
                        operationIssue.severity = OperationOutcome.IssueSeverity.INFORMATION
                        outcomes.add(operationIssue)
                        //println(apiParameter.name)

                        val searchParameter = getSearchParameter(resourceType,apiParameter.name)
                        if (searchParameter == null) {
                            operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                            operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                            operationIssue.diagnostics = "Unable to find FHIR SearchParameter of for: "+apiParameter.name
                        }
                    }
                }
            }

            // check all examples validate

        }
        return outcomes
    }

    private fun inCodeSystem(codeSystem: CodeSystem, code : String) : Boolean {
        for (codes in codeSystem.concept) {
            if (codes.hasCode() && codes.code.equals(code)) return true;
        }
        return false
    }

    fun getOperationDefinition(operationCode : String) : OperationDefinition? {
        var operationCode= operationCode.removePrefix("$")
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    OperationDefinition::class.java
                )) {
                    if (resource.code.equals(operationCode)) {
                        return resource
                    }
                }
            }
        }
        return null
    }

    fun getSearchParameter(resourceType: String, name : String) : SearchParameter? {
        val parameters = name.split(".")

        val modifiers = parameters.get(0).split(":")

        var name = modifiers.get(0)

        var searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/$resourceType-$name")
        if (searchParameter == null) searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/individual-$name")
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/clinical-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        }
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/conformance-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        }
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/medications-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        }

        if (searchParameter == null && name.startsWith("_")) {
            searchParameter = SearchParameter()
            searchParameter?.code = name.replace("_","")
            searchParameter?.description = "Special search parameter, see [FHIR Search](http://www.hl7.org/fhir/search.html)"
            searchParameter?.expression = ""
            searchParameter?.type = Enumerations.SearchParamType.SPECIAL
        }
        return searchParameter
    }

    fun getSearchParameterByUrl(url : String) : SearchParameter? {
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    SearchParameter::class.java
                )) {
                    if (resource.url.equals(url)) {
                        return resource
                    }
                }
            }
        }
        for (entry in searchParameters.entry) {
            if (entry.resource is SearchParameter) {
                if ((entry.resource as SearchParameter).url.equals(url)) {
                    return entry.resource as SearchParameter
                }
            }
        }
        return null
    }
}
