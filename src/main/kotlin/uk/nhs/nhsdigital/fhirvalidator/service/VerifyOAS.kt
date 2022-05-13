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
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.QueryParameter
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class VerifyOAS(@Qualifier("R4") private val ctx: FhirContext?,
                @Qualifier("SupportChain") private val supportChain: IValidationSupport,
                private val searchParameterSupport : SearchParameterSupport,
                private val fhirValidator: FhirValidator,
                private val messageDefinitionApplier: MessageDefinitionApplier,
                private val capabilityStatementApplier: CapabilityStatementApplier)
{

   // var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)
    val objectMapper = ObjectMapper()

    fun validate(openAPI : OpenAPI) : List<OperationOutcome.OperationOutcomeIssueComponent> {
        // check all examples validate
        // check all paths are correct
        val outcomes = mutableListOf<OperationOutcome.OperationOutcomeIssueComponent>()

        if (openAPI.info.extensions == null || openAPI.info.extensions.get("x-HL7-FHIR-NpmPackages") == null) {
            addOperationIssue(outcomes,OperationOutcome.IssueType.BUSINESSRULE, OperationOutcome.IssueSeverity.WARNING, "No FHIR package extension found")
                .location.add(StringType("OAS: info.extensions.x-HL7-FHIR-NpmPackages"))
        }
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
                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR, "Unable to find FHIR Resource type of: "+resourceType)

                    operationIssue.location.add(StringType("OAS: "+apiPaths.key ))
                    //operationIssue.
                }
            }
            if (operation.isNotEmpty() && operation.startsWith("$")) {
                val operationDefinition = getOperationDefinition(operation)
                if (operationDefinition == null) {
                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"Unable to find FHIR operation for: "+operation)
                    operationIssue.location.add(StringType("OAS: "+apiPaths.key ))
                }
            }

            // check all parameters
            if (apiPaths.value.get != null && apiPaths.value.get.parameters != null) {

                for (apiParameter in apiPaths.value.get.parameters) {

                    if (apiParameter is QueryParameter) {

                        val searchParameter = getSearchParameter(outcomes, path, resourceType,apiParameter.name)

                        if (apiParameter.name.startsWith("_include")) {
                            if (apiParameter.schema.enum == null || apiParameter.schema.enum.size ==0) {
                                addOperationIssue(outcomes,OperationOutcome.IssueType.INCOMPLETE, OperationOutcome.IssueSeverity.ERROR, "_include parameters MUST have an enumeration listing possible values")
                            } else
                            {
                                for (allowed in apiParameter.schema.enum) {
                                    if (allowed is String) {
                                        if (!allowed.equals("*")) {
                                            val includes=allowed.split(":")
                                            if (includes.size < 2) {
                                                val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.INCOMPLETE, OperationOutcome.IssueSeverity.ERROR, "_include allowed values MUST be of resourceType:searchParameter format")
                                                operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/enum"))
                                            } else {
                                                val searchParameter = searchParameterSupport.getSearchParameter(includes[0],includes[1])
                                                if (searchParameter == null) {
                                                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR, "_include allowed values not found "+allowed)
                                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/enum"))
                                                }
                                                if (!includes[0].equals(resourceType) && !apiParameter.name.equals("_include:iterate") ) {
                                                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.WARNING, "_include:iterate MUST be used with "+allowed)
                                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/enum"))
                                                }
                                                if (includes[0].equals(resourceType) && apiParameter.name.contains("_iterate") ) {
                                                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.WARNING, "_include MUST be used with "+allowed + " only and not :iterate")
                                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/enum"))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (searchParameter == null) {
                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR, "Unable to find FHIR SearchParameter of for: "+apiParameter.name)

                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name))
                        } else {
                            if (apiParameter.schema != null) {
                                // check schema for paramter is correct
                                if (apiParameter.schema.format != null && !searchParameter.type.toCode().equals(apiParameter.schema.format)) {
                                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.WARNING,"Query parameter format for: "+apiParameter.name + " should be "+searchParameter.type.toCode()+" (FHIR) is "+apiParameter.schema.format)

                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))

                                }
                                if (apiParameter.schema.format == null) {
                                    val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.WARNING,"Query parameter format for: "+apiParameter.name + " is missing. Should be " + searchParameter.type.toCode())

                                    operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                }
                                // Check fhir type
                                when(searchParameter.type) {
                                    Enumerations.SearchParamType.STRING, Enumerations.SearchParamType.REFERENCE -> {

                                        if (!apiParameter.schema.type.equals("string")) {
                                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"Parameter schema type for : "+apiParameter.name + " should be a string/(FHIR Search: "+searchParameter.type.toCode()+")")

                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                    }
                                    Enumerations.SearchParamType.TOKEN, Enumerations.SearchParamType.DATE -> {
                                        if (!apiParameter.schema.type.equals("string") && (
                                                    apiParameter.schema.type.equals("array") && !((apiParameter.schema as ArraySchema).items.type.equals("string"))
                                                    )) {
                                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"Parameter schema type for : "+apiParameter.name + " should be a string/array(string) (FHIR Search: "+searchParameter.type.toCode()+")")

                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                        if (apiParameter.schema is ArraySchema && apiParameter.schema.format.equals("token") && apiParameter.explode) {
                                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"For array of format = token, explode should be set to false")
                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                        if (apiParameter.schema is ArraySchema && apiParameter.schema.format.equals("date") && !apiParameter.explode) {
                                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"For array of format = date, explode should be set to true")
                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                    }
                                    Enumerations.SearchParamType.NUMBER -> {
                                        if (!apiParameter.schema.type.equals("string") and !apiParameter.schema.type.equals("integer")) {
                                            val operationIssue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"Parameter schema type for : "+apiParameter.name + " should be a string or integer (FHIR Search: "+searchParameter.type.toCode()+")")

                                            operationIssue.location.add(StringType("OAS: "+apiPaths.key + "/get/" + apiParameter.name+"/schema/type"))
                                        }
                                    }
                                    else -> {}
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
            addOperationIssue(outcomes, OperationOutcome.IssueType.INFORMATIONAL, OperationOutcome.IssueSeverity.INFORMATION, null)
        }
        return outcomes
    }

    private fun addOperationIssue(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>, code : OperationOutcome.IssueType, severity :OperationOutcome.IssueSeverity, message : String?  ): OperationOutcome.OperationOutcomeIssueComponent {
        val operation = OperationOutcome.OperationOutcomeIssueComponent()
        operation.code = code
        operation.severity = severity
        if (message!=null) operation.diagnostics = message
        outcomes.add(operation)
        return operation
    }

    private fun checkOperations(outcomes: MutableList<OperationOutcome.OperationOutcomeIssueComponent>,path : String, operation: Operation) {
        if (operation.requestBody != null) {
            if (operation.requestBody.content !=null) {
                for (mediaType in operation.requestBody.content.entries) {
                    if (!(mediaType.key in arrayOf("application/fhir+json","application/fhir+xml"))) {
                        val issue = addOperationIssue(outcomes, OperationOutcome.IssueType.VALUE, OperationOutcome.IssueSeverity.ERROR, "Invalid media type of "+mediaType.key)
                        issue.location.add(StringType(path + "/responses/"+mediaType.key))
                    }
                    checkMediaType(outcomes,path + "/requestBody/" + mediaType.key, mediaType.value)
                }
            }
        }
        if (operation.responses != null) {
            for (response in operation.responses.entries) {
                if (response.value.content != null) {

                    for (mediaType in response.value.content.entries) {
                        if (!(mediaType.key in arrayOf("application/fhir+json","application/fhir+xml"))) {
                                val issue = addOperationIssue(outcomes, OperationOutcome.IssueType.VALUE, OperationOutcome.IssueSeverity.ERROR, "Invalid media type of "+mediaType.key)
                                    issue.location.add(StringType(path + "/responses/"+mediaType.key))
                        }
                        checkMediaType(outcomes,path + "/responses/"+mediaType.key, mediaType.value)
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
                inputResource = ctx?.newJsonParser()!!.parseResource(objectMapper.writeValueAsString(resource))
            } catch (ex: DataFormatException) {
                val issue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR, ex.message)
                issue.location.add(StringType(path))
                return
            }
        }
        if (resource is String) {
            try {
                inputResource = ctx?.newJsonParser()?.parseResource(resource)
            } catch (ex: DataFormatException) {
                try {
                    if (!ex.message?.contains("was: '<'")!!) throw ex
                    inputResource = ctx?.newXmlParser()!!.parseResource(resource)
                } catch (ex: DataFormatException) {
                    val issue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,ex.message)
                    issue.location.add(StringType(path))
                    return
                }
            }
        }

        if (inputResource == null) {
            val issue = addOperationIssue(outcomes,OperationOutcome.IssueType.CODEINVALID, OperationOutcome.IssueSeverity.ERROR,"Unrecognised format for example: "+resource.javaClass.name)

            issue.location.add(StringType(path))
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
            if (codes.hasCode() && codes.code.equals(code)) return true
        }
        return false
    }

    fun getOperationDefinition(operationCode : String) : OperationDefinition? {
        val operation= operationCode.removePrefix("$")
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is OperationDefinition) {
                if (resource.code.equals(operation)) {
                    return resource
                }
            }
        }
        return null
    }

    fun getSearchParameter(outcomes : MutableList<OperationOutcome.OperationOutcomeIssueComponent> , path : String, originalResourceType: String, originalName : String) : SearchParameter? {
        val parameters = originalName.split(".")

        val searchParameter = searchParameterSupport.getSearchParameter(originalResourceType,originalName)

        if (parameters.size>1) {
            if (searchParameter?.type != Enumerations.SearchParamType.REFERENCE) {
               // maybe throw error?
            } else {

                var resourceType: String?

                // A bit coarse
                resourceType = "Resource"
                if (searchParameter.hasTarget() ) {
                    for (resource in searchParameter.target) {
                        if (!resource.code.equals("Group")) resourceType=resource.code
                    }
                }

                var newSearchParamName = parameters.get(1)
                // Add back in remaining chained parameters
                for (i in 3..parameters.size) {
                    newSearchParamName += "."+parameters.get(i)
                }

                return resourceType?.let { getSearchParameter(outcomes, path, it, newSearchParamName) }
            }
        }

        return searchParameter
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
