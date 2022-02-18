package uk.nhs.nhsdigital.fhirvalidator.service

//import io.swagger.models.parameters.QueryParameter
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.QueryParameter
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class VerifyOAS(private val ctx: FhirContext?,
                @Qualifier("SupportChain") private val supportChain: IValidationSupport,
                private val searchParameters : Bundle,
                private val fhirValidator: FhirValidator,
                private val messageDefinitionApplier: MessageDefinitionApplier,
                private val capabilityStatementApplier: CapabilityStatementApplier)
{

   // var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)
    val objectMapper = ObjectMapper()

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
                    operationIssue.diagnostics = "Unable to find FHIR Resource type of: "+resourceType
                    operationIssue.location.add(StringType("OAS: "+apiPaths.key ))
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
                    operationIssue.location.add(StringType("OAS: "+apiPaths.key ))
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
                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name))
                        } else {
                            if (apiParameter.schema != null) {
                                // check schema for paramter is correct
                                if (!searchParameter.type.toCode().equals(apiParameter.schema.type)) {
                                    var operationIssue = addOperationIssue(outcomes)
                                    operationIssue.severity = OperationOutcome.IssueSeverity.WARNING
                                    operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                                    operationIssue.diagnostics = "Query parameter type for : "+apiParameter.name + " should be "+searchParameter.type.toCode()+" (FHIR) is "+apiParameter.schema.type
                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))

                                }
                                when(searchParameter.type) {
                                    Enumerations.SearchParamType.STRING, Enumerations.SearchParamType.TOKEN, Enumerations.SearchParamType.REFERENCE -> {
                                        if (!apiParameter.schema.type.equals("string")) {
                                            var operationIssue = addOperationIssue(outcomes)
                                            operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                                            operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                                            operationIssue.diagnostics = "Parameter schema type for : "+apiParameter.name + " should be a string/(FHIR Search: "+searchParameter.type.toCode()+")"
                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                    }
                                    Enumerations.SearchParamType.NUMBER -> {
                                        if (!apiParameter.schema.type.equals("string") and !apiParameter.schema.type.equals("integer")) {
                                            var operationIssue = addOperationIssue(outcomes)
                                            operationIssue.severity = OperationOutcome.IssueSeverity.ERROR
                                            operationIssue.code = OperationOutcome.IssueType.CODEINVALID
                                            operationIssue.diagnostics = "Parameter schema type for : "+apiParameter.name + " should be a string or integer (FHIR Search: "+searchParameter.type.toCode()+")"
                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // check all examples validate
            if (apiPaths.value.get != null) checkOperations(outcomes,apiPaths.key + "/get",apiPaths.value.get)
            if (apiPaths.value.post != null) checkOperations(outcomes,apiPaths.key + "/post", apiPaths.value.post)
            if (apiPaths.value.put != null) checkOperations(outcomes,apiPaths.key + "/put", apiPaths.value.put)
        }
        if (openAPI.components != null) {
            if (openAPI.components.examples != null) {
                for (example in openAPI.components.examples) {
                    checkExample(outcomes, "component/"+ example.key, example.value)
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

    private fun checkOperations(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>,path : String, operation: Operation) {
        if (operation.requestBody != null) {
            if (operation.requestBody.content !=null) {
                for (stuff in operation.requestBody.content.entries) {
                 //   println(stuff.key)
                    checkMediaType(outcomes,path + "/requestBody/"+stuff.key, stuff.value)
                }
            }
        }
        if (operation.responses != null) {
            for (response in operation.responses.entries) {
                if (response.value.content != null) {
                    for (stuff in response.value.content.entries) {
                      //  println(stuff.key)
                        checkMediaType(outcomes,path + "/responses/"+stuff.key,stuff.value)
                    }
                }

            }
        }
    }

    private fun checkMediaType(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>,path : String, mediaType : MediaType) {
        if (mediaType.example != null) {
            validateExample(outcomes,path + "/example", mediaType.example)
        }
        if (mediaType.examples != null) {
           // println(path + " - examples - "+mediaType.examples.size)
            for (example in mediaType.examples) {
                checkExample(outcomes,path + "/" + example.key, example.value)
            }
        }
    }

    private fun checkExample(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>,path : String,example : Example) {
        if (example.value != null) {
            validateExample(outcomes,path,example.value)
        }
    }

    private fun validateExample(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>,path : String, resource : Any) {
        var inputResource : IBaseResource? = null
        if (resource is JsonNode) {

            try {
                inputResource = ctx?.newJsonParser()!!?.parseResource(objectMapper.writeValueAsString(resource))
            } catch (ex: DataFormatException) {
                val issue = addOperationIssue(outcomes)
                issue.diagnostics = ex.message
                issue.severity = OperationOutcome.IssueSeverity.ERROR
                issue.code = OperationOutcome.IssueType.CODEINVALID
                issue.location.add(StringType(path))
                return
            }
        }
        if (resource is String) {
            try {
                inputResource = ctx?.newJsonParser()!!?.parseResource(resource)
            } catch (ex: DataFormatException) {
                try {
                    if (!ex.message?.contains("was: '<'")!!) throw ex
                    inputResource = ctx?.newXmlParser()!!?.parseResource(resource)
                } catch (ex: DataFormatException) {
                    val issue = addOperationIssue(outcomes)
                    issue.diagnostics = ex.message
                    issue.severity = OperationOutcome.IssueSeverity.ERROR
                    issue.code = OperationOutcome.IssueType.CODEINVALID
                    issue.location.add(StringType(path))
                    return
                }
            }
        }

        if (inputResource == null) {
            val issue = addOperationIssue(outcomes)
            issue.diagnostics = "Unrecognised format for example: "+resource.javaClass.name
            issue.severity = OperationOutcome.IssueSeverity.ERROR
            issue.code = OperationOutcome.IssueType.CODEINVALID
            issue.location.add(StringType(path))
            return
            return
        }

        val resources = getResourcesToValidate(inputResource)
        val operationOutcomeList = resources.map { validateResource(it, null) }
        val issueList = operationOutcomeList.filterNotNull().flatMap { it.issue }
        for (issue in issueList) {
            if (issue.code != OperationOutcome.IssueType.INFORMATIONAL) {
                outcomes.add(issue)
                issue.location.add(StringType("OAS: "+path))
            }
        }

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

    // TODO refactor to remove duplication

    fun validateResource(resource: IBaseResource, profile: String?): OperationOutcome? {
        if (profile != null) return fhirValidator.validateWithResult(resource, ValidationOptions().addProfile(profile)).toOperationOutcome() as? OperationOutcome
        capabilityStatementApplier.applyCapabilityStatementProfiles(resource)
        val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(resource)
        if (messageDefinitionErrors != null) {
            return messageDefinitionErrors
        }
        return fhirValidator.validateWithResult(resource).toOperationOutcome() as? OperationOutcome
    }

    fun getResourcesToValidate(inputResource: IBaseResource?): List<IBaseResource> {
        if (inputResource == null) {
            return emptyList()
        }

        if (inputResource is Bundle && inputResource.type == Bundle.BundleType.SEARCHSET) {
            val bundleResources = inputResource.entry.map { it.resource }
            if (bundleResources.all { it.resourceType == ResourceType.Bundle }) {
                return bundleResources
            }
        }

        return listOf(inputResource)
    }
}
