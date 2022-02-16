package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.validation.FhirValidator
//import io.swagger.models.parameters.QueryParameter
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.QueryParameter
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage

class VerifyOAS(private val ctx: FhirContext?,
                private val supportChain: IValidationSupport,
                private val searchParameters : Bundle)
{

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)


    public fun validate(openAPI : OpenAPI) : List<OperationOutcome.OperationOutcomeIssueComponent> {
        // check all examples validate
        // check all paths are correct
        var outcomes = mutableListOf<OperationOutcome.OperationOutcomeIssueComponent>()
        for (apiPaths in openAPI.paths) {

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
                    val operationIssue = addOperationIssue(outcomes)
                    operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                    operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                    operationIssue.diagnostics = "Unable to find resource type of: "+resourceType
                    //operationIssue.
                }
            }
            if (operation.isNotEmpty() && operation.startsWith("$")) {
                val operationDefinition = getOperationDefinition(operation)
                if (operationDefinition == null) {
                    val operationIssue = addOperationIssue(outcomes)
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
                        //println(apiParameter.name)

                        val searchParameter = getSearchParameter(resourceType,apiParameter.name)
                        if (searchParameter == null) {
                            var operationIssue = addOperationIssue(outcomes)
                            operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                            operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                            operationIssue.diagnostics = "Unable to find FHIR SearchParameter of for: "+apiParameter.name
                        }
                    }
                }
            }

            // check all examples validate
            if (apiPaths.value.get != null) checkOperations(apiPaths.key + "/get",apiPaths.value.get)
            if (apiPaths.value.post != null) checkOperations(apiPaths.key + "/post", apiPaths.value.post)
        }
        if (openAPI.components != null) {
            if (openAPI.components.examples != null) {
                for (example in openAPI.components.examples) {
                    checkExample("component/"+ example.key, example.value)
                }
            }
        }
        if (outcomes.size==0) {
            val operationIssue = addOperationIssue(outcomes)
            operationIssue.code = OperationOutcome.IssueType.INFORMATIONAL
            operationIssue.severity = OperationOutcome.IssueSeverity.INFORMATION
        }
        return outcomes
    }

    private fun addOperationIssue(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>): OperationOutcome.OperationOutcomeIssueComponent {
        val operation = OperationOutcome.OperationOutcomeIssueComponent()
        outcomes.add(operation)
        return operation
    }

    private fun checkOperations(path : String, operation: Operation) {
        if (operation.requestBody != null) {
            if (operation.requestBody.content !=null) {
                for (stuff in operation.requestBody.content.entries) {
                 //   println(stuff.key)
                    checkMediaType(path + "/requestBody", stuff.value)
                }
            }
        }
        if (operation.responses != null) {
            for (response in operation.responses.entries) {
                if (response.value.content != null) {
                    for (stuff in response.value.content.entries) {
                      //  println(stuff.key)
                        checkMediaType(path + "/responses",stuff.value)
                    }
                }

            }
        }
    }

    private fun checkMediaType(path : String, mediaType : MediaType) {
        if (mediaType.example != null) {
            validateExample(path + "/example", mediaType.example)
        }
        if (mediaType.examples != null) {
            println(path + " - examples - "+mediaType.examples.size)
            for (example in mediaType.examples) {
                checkExample(path + "/" + example.key, example.value)
            }
        }
    }

    private fun checkExample(path : String,example : Example) {
        if (example.value != null) validateExample(path,example.value)
    }

    private fun validateExample(path : String, resource : Any) {
        println("path - "+ path)
        println(resource)
    }


    private fun inCodeSystem(codeSystem: CodeSystem, code : String) : Boolean {
        for (codes in codeSystem.concept) {
            if (codes.hasCode() && codes.code.equals(code)) return true;
        }
        return false
    }

    fun getOperationDefinition(operationCode : String) : OperationDefinition? {
        var operationCode= operationCode.removePrefix("$")
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is OperationDefinition) {
                if (resource.code.equals(operationCode)) {
                    return resource
                }
            }
        }
        return null
    }

    fun getSearchParameter(resourceType: String, name : String) : SearchParameter? {
        val parameters = name.split(".")

        val modifiers = parameters.get(0).split(":")

        var name = modifiers.get(0)
        if (name.startsWith("_")) {
            name = name.removePrefix("_")
        }

        var searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/$resourceType-$name")
        if (searchParameter == null)
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/individual-$name")
        else return searchParameter
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/clinical-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        } else return searchParameter
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/conformance-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        } else return searchParameter
        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/medications-$name")
            if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                searchParameter = null
            }
        } else return searchParameter

        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/Resource-$name")
        } else return searchParameter

        if (searchParameter == null) {
            searchParameter = getSearchParameterByUrl("http://hl7.org/fhir/SearchParameter/DomainResource-$name")
        } else return searchParameter


        /*
        if (searchParameter == null && name.startsWith("_")) {
            searchParameter = SearchParameter()
            searchParameter?.code = name.replace("_","")
            searchParameter?.description = "Special search parameter, see [FHIR Search](http://www.hl7.org/fhir/search.html)"
            searchParameter?.expression = ""
            searchParameter?.type = Enumerations.SearchParamType.SPECIAL
        } */
        return searchParameter
    }

    fun getSearchParameterByUrl(url : String) : SearchParameter? {
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is SearchParameter) {
                if (resource.url.equals(url)) {
                    return resource
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
