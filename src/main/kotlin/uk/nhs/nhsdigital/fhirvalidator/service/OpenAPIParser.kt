package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.api.Constants
import ca.uhn.fhir.util.HapiExtensions
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.apache.commons.lang3.StringUtils
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.instance.model.api.IPrimitiveType
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import java.util.stream.Collectors

@Service
class OpenAPIParser(@Qualifier("R4") private val ctx: FhirContext,
                    private val npmPackages: List<NpmPackage>?,
                    @Qualifier("SupportChain") private val supportChain: IValidationSupport,
                    private val searchParameterSupport : SearchParameterSupport) {


    val PAGE_SYSTEM = "System Level Operations"
    //val FHIR_CONTEXT_CANONICAL = FhirContext.forR4()

    private var generateXML = false
    private var cs: CapabilityStatement = CapabilityStatement()
    private val exampleServer = "http://example.org/"
    private val exampleServerPrefix = "FHIR/R4/"
    private val validationSupportContext = ValidationSupportContext(supportChain)
    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)




    fun generateOpenApi(_cs: CapabilityStatement): OpenAPI? {
        cs = _cs
        val openApi = OpenAPI()
        openApi.info = Info()
        openApi.info.description = cs.description
        if (openApi.info.description == null) openApi.info.description = ""
        openApi.info.title = cs.software.name
        openApi.info.version = cs.software.version
        openApi.info.contact = Contact()
        openApi.info.contact.name = cs.contactFirstRep.name
        openApi.info.contact.email = cs.contactFirstRep.telecomFirstRep.value

        for (code in cs.format) {
            if (code.value.contains("xml")) generateXML = true
        }

        if (cs.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Package")) {
            val apiDefinition = cs.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Package")
            // Sample table:\n\n| One | Two | Three |\n|-----|-----|-------|\n| a   | b   | c     |
            if (apiDefinition.hasExtension("openApi")) {
                var docDescription = "\n\n | API Documentation |\n |-----|\n "
                apiDefinition.extension.forEach{
                    if (it.url.equals("openApi")) {
                        docDescription += " |["+(it.getExtensionByUrl("description").value as StringType).value+"]("+(it.getExtensionByUrl("documentation").value as UriType).value+")|\n"
                    }
                }
                openApi.info.description += docDescription
            }
            openApi.info.extensions = mutableMapOf<String,Any>()
            val igs =  mutableMapOf<String,Any>()
            if (apiDefinition.hasExtension("FHIRPackage")) {
                var igDescription = "\n\n | FHIR Implementation Guide | Version |\n |-----|-----|\n"
                apiDefinition.extension.forEach{

                    if (it.url.equals("FHIRPackage")) {

                        val name = it.getExtensionByUrl("name").value as StringType
                        var url = "https://simplifier.net/guide/NHSDigital/Home"
                        var version = ""
                        if (it.hasExtension("version")) {
                            version = (it.getExtensionByUrl("version").value as StringType).value
                            igs[(it.getExtensionByUrl("name").value as StringType).value] = (it.getExtensionByUrl("version").value as StringType).value
                        } else {
                            igs[(it.getExtensionByUrl("name").value as StringType).value] = ""
                        }
                        if (name.value.startsWith("uk.nhsdigital.medicines")) url = "https://simplifier.net/guide/nhsdigital-medicines/home"
                        if (name.value.startsWith("ukcore.")) url = "https://simplifier.net/guide/hl7fhirukcorer4release1/home"
                        igDescription += " |[$name]($url)|$version|\n"

                    }
                }
                openApi.info.description += igDescription
            }
            openApi.info.extensions["x-HL7-FHIR-NpmPackages"] = igs

        }
        openApi.externalDocs = ExternalDocumentation()
        openApi.externalDocs.description = "Hl7 FHIR R4"
        openApi.externalDocs.url = "https://www.hl7.org/fhir/"
        val server = Server()
        openApi.addServersItem(server)
        server.url = cs.implementation.url
        server.description = cs.software.name

        val paths = Paths()
        openApi.paths = paths
        val serverTag = Tag()
        serverTag.name = PAGE_SYSTEM
        serverTag.description = "Server-level operations"
        openApi.addTagsItem(serverTag)
        val capabilitiesOperation = getPathItem(paths, "/metadata", PathItem.HttpMethod.GET)
        capabilitiesOperation.addTagsItem(PAGE_SYSTEM)
        capabilitiesOperation.summary = "server-capabilities: Fetch the server FHIR CapabilityStatement"
        addFhirResourceResponse(this.ctx, openApi, capabilitiesOperation, "CapabilityStatement",null,null,null)
        val systemInteractions =
            cs.restFirstRep.interaction.stream().map { t: CapabilityStatement.SystemInteractionComponent -> t.code }
                .collect(Collectors.toSet())


        // Transaction Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.TRANSACTION) || systemInteractions.contains(
                CapabilityStatement.SystemRestfulInteraction.BATCH
            )
        ) {
            val transaction = getPathItem(paths, "/", PathItem.HttpMethod.POST)
            transaction.addTagsItem(PAGE_SYSTEM)
            transaction.summary = "server-transaction: Execute a FHIR Transaction (or FHIR Batch) Bundle"
            addFhirResourceResponse(ctx, openApi, transaction, null, null,null,null)
            addFhirResourceRequestBody(openApi, transaction, emptyList(), "Bundle",null)
        }

        // System History Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.HISTORYSYSTEM)) {
            val systemHistory = getPathItem(paths, "/_history", PathItem.HttpMethod.GET)
            systemHistory.addTagsItem(PAGE_SYSTEM)
            systemHistory.summary =
                "server-history: Fetch the resource change history across all resource types on the server"
            addFhirResourceResponse(ctx, openApi, systemHistory, null, null,null,null)
        }

        // System-level Operations
        for (nextOperation in cs.restFirstRep.operation) {
            addFhirOperation(ctx, openApi, paths, null, nextOperation)
        }


        // System-level REST

        for (nextResource in cs.restFirstRep.resource) {
            val resourceType = nextResource.type
            val typeRestfulInteractions =
                nextResource.interaction.stream().map { t: CapabilityStatement.ResourceInteractionComponent ->
                    t.codeElement.value
                }.collect(Collectors.toSet())

            addResoureTag(openApi,resourceType,nextResource.profile)

            for (resftfulIntraction in nextResource.interaction) {
                val requestExample = getRequestExample(resftfulIntraction)

                when (resftfulIntraction.code) {
                    // Search
                    CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.summary = resftfulIntraction.code.display
                        operation.description = "[search](http://www.hl7.org/fhir/search.html) for $resourceType instances."
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+ resftfulIntraction.documentation
                        }
                        if (nextResource.hasExtension("http://hl7.org/fhir/StructureDefinition/capabilitystatement-search-parameter-combination")) {
                            var comboDoc = "\n\n **Required Parameters** \n\n One of the following search paramter combinations is **required** \n\n" +
                                    "| Required | Optional | \n"
                            comboDoc += "|----------|---------| \n"

                            for (extension in nextResource.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/capabilitystatement-search-parameter-combination")) {
                                var requiredDoc = ""
                                var optionalDoc = ""
                                if (extension.hasExtension("required")) {
                                    for (required in extension.getExtensionsByUrl("required")) {
                                        if (requiredDoc != "") requiredDoc += "+"
                                        requiredDoc += (required.value as StringType).value
                                    }
                                }
                                if (extension.hasExtension("optional")) {
                                    for (optional in extension.getExtensionsByUrl("optional")) {
                                        optionalDoc += (optional.value as StringType).value + "<br/>"
                                    }
                                }
                                comboDoc += "| $requiredDoc| $optionalDoc | \n"
                            }
                            operation.description += comboDoc
                        }
                        addFhirResourceResponse(ctx, openApi, operation, resourceType,resftfulIntraction, null,null)
                        processSearchParameter(operation,nextResource,resourceType)
                        addResourceAPIMParameter(operation)
                    }
                    // Instance Read
                    CapabilityStatement.TypeRestfulInteraction.READ -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.summary = resftfulIntraction.code.display
                        operation.description = "[read](http://www.hl7.org/fhir/http.html#read) $resourceType instance."

                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation,resourceType,resftfulIntraction,null,null)
                    }
                    // Instance Update
                    CapabilityStatement.TypeRestfulInteraction.UPDATE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PUT)
                        operation.addTagsItem(resourceType)
                        operation.summary = resftfulIntraction.code.display
                        operation.description =
                            "[update](http://www.hl7.org/fhir/http.html#update) an existing $resourceType instance, or create using a client-assigned ID."
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+ resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addFhirResourceRequestBody(openApi, operation,  requestExample, resourceType,nextResource.profile)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction,null,null)
                    }
                    // Type Create
                    CapabilityStatement.TypeRestfulInteraction.CREATE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.POST)
                        operation.addTagsItem(resourceType)
                        operation.summary = resftfulIntraction.code.display
                        operation.description = "[create](http://www.hl7.org/fhir/http.html#create) a new $resourceType instance."
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+resftfulIntraction.documentation
                        }
                        addResourceAPIMParameter(operation)
                        addFhirResourceRequestBody(openApi, operation, requestExample, resourceType, nextResource.profile)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction,null,null)
                    }
                    // Instance Patch
                    CapabilityStatement.TypeRestfulInteraction.PATCH -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PATCH)
                        operation.addTagsItem(resourceType)
                        operation.summary = resftfulIntraction.code.display
                        operation.description = "[patch](http://www.hl7.org/fhir/http.html#patch) a resource instance of type $resourceType by ID."

                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addJSONSchema(openApi)
                        addPatchResourceRequestBody(operation, patchExampleSupplier(resourceType))
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction,null,null)
                    }

                        // Instance Delete
                        CapabilityStatement.TypeRestfulInteraction.DELETE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.DELETE)
                        operation.addTagsItem(resourceType)
                            operation.summary = resftfulIntraction.code.display
                            operation.description = "Perform a logical [delete](http://www.hl7.org/fhir/http.html#delete) on a resource instance."

                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction,null,null)
                    }

                    // Type history
                    CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE -> {
                        val operation = getPathItem(paths, "/$resourceType/_history", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.summary = "history"
                        operation.description =
                            "Fetch the resource change [history](http://www.hl7.org/fhir/http.html#history) for all resources of type $resourceType."
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += "\n\n"+resftfulIntraction.documentation
                        }
                        processSearchParameter(operation,nextResource,resourceType)
                        addResourceAPIMParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation,  resourceType,resftfulIntraction, null,null)
                    }

                        // Instance history
                     CapabilityStatement.TypeRestfulInteraction.HISTORYINSTANCE -> {
                        val operation = getPathItem(
                            paths,
                            "/$resourceType/{id}/_history", PathItem.HttpMethod.GET
                        )
                        operation.addTagsItem(resourceType)
                        operation.summary = "history"
                        operation.description =
                            "Fetch the resource change [history](http://www.hl7.org/fhir/http.html#history) for all resources of type $resourceType"
                         if (resftfulIntraction.hasDocumentation()) {
                             operation.description += "\n\n"+resftfulIntraction.documentation
                         }
                        addResourceIdParameter(operation)
                        processSearchParameter(operation,nextResource,resourceType)
                        addResourceAPIMParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation,  resourceType,resftfulIntraction,null,null)
                    }

                    else -> {}
                }
            }


            // Instance VRead
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.VREAD)) {
                val operation = getPathItem(
                    paths,
                    "/$resourceType/{id}/_history/{version_id}", PathItem.HttpMethod.GET
                )
                operation.addTagsItem(resourceType)
                operation.summary = "vread"
                operation.description = "[vread](http://www.hl7.org/fhir/http.html#vread) $resourceType instance with specific version."

                addResourceIdParameter(operation)
                addResourceVersionIdParameter(operation)
                addResourceAPIMParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation,  resourceType,null,null,null)
            }


            // Resource-level Operations
            for (nextOperation in nextResource.operation) {
                addFhirOperation(ctx, openApi, paths, resourceType, nextOperation)
            }
        }
        return openApi
    }


    private fun processSearchParameter(operation : Operation, nextResource : CapabilityStatement.CapabilityStatementRestResourceComponent,resourceType: String) {
        for (nextSearchParam in nextResource.searchParam) {
            val parametersItem = Parameter()
            operation.addParametersItem(parametersItem)
            parametersItem.name = nextSearchParam.name
            parametersItem.setIn("query")
            parametersItem.description = nextSearchParam.documentation
            parametersItem.description += getSearchParameterDocumentation(nextSearchParam,resourceType, parametersItem,true)

            // calculate style and explode
            parametersItem.style = Parameter.StyleEnum.FORM
            if (parametersItem.schema != null && parametersItem.schema.format != null) {
                when (parametersItem.schema.format) {
                    "date" -> {
                        parametersItem.explode = true
                    }
                    "token" -> {
                        parametersItem.explode = false
                    }
                    else -> {
                        parametersItem.explode = false
                    }
                }
            }



            if (nextSearchParam.name.startsWith("_include") && nextResource.hasSearchInclude()) {
                nextSearchParam.name = "_include"
                parametersItem.explode = true
                //parametersItem.style= Parameter.StyleEnum.FORM
                val iterateSchema = ArraySchema()
                iterateSchema.example("MedicationRequest:patient")
                iterateSchema.items = StringSchema()
                parametersItem.schema.example = "MedicationRequest:patient"
                parametersItem.schema.minimum = BigDecimal.ZERO
                for (include in nextResource.searchInclude) {
                    val includes = include.value.split(":")
                    if (includes[0].equals(resourceType)) {
                        parametersItem.schema.addEnumItemObject(include)
                    }
                    else {
                        iterateSchema.addEnumItemObject(include)
                    }
                    if (!includes[0].equals("*")) {
                        if (includes.size < 2) {
                            parametersItem.description += "\n **FHIR ERROR _include "+include.value + " format {resourceType}:{searchParameterName}**"
                        } else {
                            val searchParameter = searchParameterSupport.getSearchParameter(includes[0],includes[1])
                            if (searchParameter == null) {
                                parametersItem.description += "\n **FHIR ERROR _include "+include.value + " searchParameter " + includes[1] + " does not exist for " + includes[0] + "**"
                            }
                        }
                    }
                }
                if (iterateSchema.enum.size>0) {
                    val iterateParameter = Parameter()
                    iterateParameter.schema(iterateSchema).name("_include:iterate")
                        .description("The inclusion process can be iterative").setIn("query")
                    operation.addParametersItem(iterateParameter)
                }
            }
            if (nextSearchParam.name.startsWith("_revinclude") && nextResource.hasSearchRevInclude()) {
                nextSearchParam.name = "_revinclude"
                parametersItem.explode = true
                //parametersItem.style= Parameter.StyleEnum.FORM
                //val iterateSchema = ArraySchema()
               // iterateSchema.example("MedicationRequest:patient")
                //iterateSchema.items = StringSchema()
                parametersItem.schema.minimum = BigDecimal.ZERO
                parametersItem.schema.example = "MedicationRequest:patient"
                for (include in nextResource.searchRevInclude) {
                    parametersItem.schema.addEnumItemObject(include)
                    val includes = include.value.split(":")
                    if (includes.size < 2) {
                        parametersItem.description += "\n INVALID _revinclude "+include + " format {resourceType}:{searchParameterName}"
                    } else {
                        val searchParameter = searchParameterSupport.getSearchParameter(includes[0],includes[1])
                        if (searchParameter == null) {
                            parametersItem.description += "\n INVALID _revinclude "+include + " searchParameter " + includes[1] + " does not exist for " + includes[0]
                        }
                    }
                }
            }

            addSchemaProperties(nextSearchParam,parametersItem)
        }
    }

    private fun addJSONSchema(openApi: OpenAPI) {
        // Add schema

        if (!openApi.components.schemas.containsKey("JSONPATCH")) {

                ensureComponentsSchemasPopulated(openApi)
                val schema = ObjectSchema()
                schema.description = "See [JSON Patch](http://jsonpatch.com/)"
                openApi.components.addSchemas("JSONPATCH", schema)
                return
        }
    }



    private fun getBaseProfile(profile: String) :StructureDefinition? {
        var profileDef = getProfile(profile)
        if (profileDef != null && profileDef.hasBaseDefinition() && !profileDef.baseDefinition.contains("Resource")) {
           profileDef = getBaseProfile(profileDef.baseDefinition)
        }
        return profileDef
    }

    private fun getProfile(profile: String?) : StructureDefinition? {
        val structureDefinition = supportChain.fetchStructureDefinition(profile)
        if (structureDefinition is StructureDefinition) return structureDefinition
        return null
    }

    private fun getDocumentationPath(profile : String) : String {
        supportChain.fetchAllConformanceResources()?.filterIsInstance<StructureDefinition>()?.forEach {
            if (it.url == profile) {
                return "https://simplifier.net/guide/nhsdigital"
               /// TODO return getDocumentationPathNpm(profile,npmPackage)
            }
        }
        return "https://simplifier.net/guide/nhsdigital/home"
    }


    private fun getProfileName(profile : String) : String {
        val uri = URI(profile)
        val path: String = uri.path
        return path.substring(path.lastIndexOf('/') + 1)
    }



    private fun patchExampleSupplier(resourceType: String?): String {

        if (resourceType.equals("MedicationDispense")) {
            val patch1 = JSONObject()
                .put("op", "replace")
                .put("path", "/status")
                .put("value", "in-progress")

            val patch2 = JSONObject()
                .put("op", "add")
                .put("path", "/whenPrepared")
                .put("value", JSONArray().put("2022-02-08T00:00:00+00:00"))

            return JSONObject()
                .put("patches", JSONArray().put(patch1).put(patch2))
                .toString()

        }
        if (resourceType.equals("MedicationRequest")) {
            val patch1 = JSONObject()
                .put("op", "replace")
                .put("path", "/status")
                .put("value", "cancelled")

            return JSONObject()
                .put("patches", JSONArray().put(patch1))
                .toString()

        }
        val patch1 = JSONObject()
            .put("op", "add")
            .put("path", "/foo")
            .put("value", JSONArray().put("bar"))

        val patch2 = JSONObject()
            .put("op", "add")
            .put("path", "/foo2")
            .put("value", JSONArray().put("barbar"))

        return JSONObject()
            .put("patches", JSONArray().put(patch1).put(patch2))
            .toString()
    }




    private fun addFhirOperation(
        theFhirContext: FhirContext?,
        theOpenApi: OpenAPI,
        thePaths: Paths,
        theResourceType: String?,
        theOperation: CapabilityStatement.CapabilityStatementRestResourceOperationComponent
    ) {
        val operationDefinition = AtomicReference<OperationDefinition?>()
        //val definitionId = IdType(theOperation.definition)

            for (resource in supportChain.fetchAllConformanceResources()!!) {
                if (resource is OperationDefinition && resource.url == theOperation.definition) {
                    operationDefinition.set(resource)
                    break
                }
            }

        if (operationDefinition.get() == null) {
            val operationDef = OperationDefinition()
            operationDef.description = "**NOT HL7 FHIR Conformant** - No definition found for custom operation"
            operationDef.affectsState = false
            operationDef.url = "http://example.fhir.org/unknown-operation"
            operationDef.code = theOperation.name
            operationDef.system = true // default to system
            operationDefinition.set(operationDef)
            val operation = getPathItem(
                thePaths, "/$" + operationDefinition.get()!!
                    .code, PathItem.HttpMethod.GET
            )
            populateOperation(
                theFhirContext,
                theOpenApi,
                theResourceType,
                operationDefinition.get(),
                operation,
                true,
                theOperation
            )
            return
        }
        if (!operationDefinition.get()!!.affectsState) {

            // GET form for non-state-affecting operations
            if (theResourceType != null) {
                if (operationDefinition.get()!!.type) {
                    val operation = getPathItem(
                        thePaths, "/$theResourceType/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.GET
                    )
                    populateOperation(
                        theFhirContext,
                        theOpenApi,
                        theResourceType,
                        operationDefinition.get(),
                        operation,
                        true,
                        theOperation
                    )
                }
                if (operationDefinition.get()!!.instance) {
                    val operation = getPathItem(
                        thePaths, "/$theResourceType/{id}/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.GET
                    )
                    addResourceIdParameter(operation)
                    populateOperation(
                        theFhirContext,
                        theOpenApi,
                        theResourceType,
                        operationDefinition.get(),
                        operation,
                        true,
                        theOperation
                    )
                }
            } else {
                if (operationDefinition.get()!!.system) {
                    val operation = getPathItem(
                        thePaths, "/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.GET
                    )
                    populateOperation(theFhirContext, theOpenApi, null, operationDefinition.get(), operation, true,
                        theOperation
                    )
                }
            }
        } else {

            // POST form for all operations
            if (theResourceType != null) {
                if (operationDefinition.get()!!.type) {
                    val operation = getPathItem(
                        thePaths, "/$theResourceType/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.POST
                    )
                    populateOperation(
                        theFhirContext,
                        theOpenApi,
                        theResourceType,
                        operationDefinition.get(),
                        operation,
                        false,
                        theOperation
                    )
                }
                if (operationDefinition.get()!!.instance) {
                    val operation = getPathItem(
                        thePaths, "/$theResourceType/{id}/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.POST
                    )
                    addResourceIdParameter(operation)
                    populateOperation(
                        theFhirContext,
                        theOpenApi,
                        theResourceType,
                        operationDefinition.get(),
                        operation,
                        false,
                        theOperation

                    )
                }
            } else {
                if (operationDefinition.get()!!.system) {
                    val operation = getPathItem(
                        thePaths, "/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.POST
                    )
                    populateOperation(theFhirContext, theOpenApi, null, operationDefinition.get(), operation, false,                        theOperation
                    )
                }
            }
        }
    }


    private fun populateOperation(
        theFhirContext: FhirContext?,
        openApi: OpenAPI,
        theResourceType: String?,
        theOperationDefinition: OperationDefinition?,
        theOperation: Operation,
        theGet: Boolean,
        theOperationComponent: CapabilityStatement.CapabilityStatementRestResourceOperationComponent
    ) {
        if (theResourceType == null) {
            theOperation.addTagsItem(PAGE_SYSTEM)
        } else {
            theOperation.addTagsItem(theResourceType)
        }
        theOperation.summary = theOperationDefinition!!.title
        theOperation.description = theOperationDefinition.description
        if (theOperationComponent.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            //
            val exampleOperation = getOperationResponeExample(theOperationComponent)
            if (exampleOperation != null && exampleOperation.get() != null) {
                theOperation.responses = ApiResponses()
                val response200 = ApiResponse()
                response200.description = "Success"
                val exampleList = mutableListOf<Example>()
                exampleList.add(Example().value(ctx?.newJsonParser()?.encodeResourceToString(exampleOperation.get())))
                response200.content = provideContentFhirResource(
                    openApi,
                    exampleList,
                    (exampleOperation.get())?.fhirType(),
                    null
                )
                theOperation.responses.addApiResponse("200",response200)

                addStandardResponses(openApi,theOperation.responses)
            } else {
                addFhirResourceResponse(theFhirContext, openApi, theOperation, "Parameters", null,theOperationComponent,null)
            }

            //theOperation.requestBody.content = provideContentFhirResource(theOpenApi,ctx,exampleOperation, null)
        } else {
            addFhirResourceResponse(theFhirContext, openApi, theOperation, "Parameters", null, theOperationComponent,null)
        }
        val mediaType = MediaType()
        if (theGet) {
            for (nextParameter in theOperationDefinition.parameter) {
                val parametersItem = Parameter()
                theOperation.addParametersItem(parametersItem)
                parametersItem.name = nextParameter.name
                parametersItem.setIn("query")
                parametersItem.description = nextParameter.documentation
                parametersItem.style = Parameter.StyleEnum.SIMPLE
                parametersItem.required = nextParameter.min > 0
                val exampleExtensions = nextParameter.getExtensionsByUrl(HapiExtensions.EXT_OP_PARAMETER_EXAMPLE_VALUE)
                if (exampleExtensions.size == 1) {
                    parametersItem.example = exampleExtensions[0].valueAsPrimitive.valueAsString
                } else if (exampleExtensions.size > 1) {
                    for (next in exampleExtensions) {
                        val nextExample = next.valueAsPrimitive.valueAsString
                        parametersItem.addExample(nextExample, Example().value(nextExample))
                    }
                }
            }
        } else {
            val exampleRequestBody = Parameters()

            // Maybe load in the schema from core files?
            val parametersSchema = Schema<Any?>().type("object").title("Parameters-"+theOperationDefinition.code)
                .required(mutableListOf("resourceType","parameter"))

            parametersSchema.addProperties("resourceType",  Schema<String>()
                .type("string")
                .example("Parameters")
                .minProperties(1))
            parametersSchema.addProperties("parameter", ArraySchema().type("array").items(Schema<Any?>().type("object")
                .addProperties("name", Schema<String>()
                    .minProperties(1)
                    .maxProperties(1)
                    .type("string"))
                .addProperties("value(x)", Schema<Any>()
                    .minProperties(1)
                    .maxProperties(1))
                .addProperties("parts",ArraySchema().type("array").items(Schema<Any?>().type("object")
                    .addProperties("name", Schema<String>()
                        .minProperties(1)
                        .maxProperties(1)
                        .type("string"))
                    .addProperties("value(x)", Schema<Any>()
                        .minProperties(1)
                        .maxProperties(1)) )
            )))



            for (nextSearchParam in theOperationDefinition.parameter) {
                if (nextSearchParam.use != OperationDefinition.OperationParameterUse.OUT) {
                    val param = exampleRequestBody.addParameter()

                    param.name = nextSearchParam.name
                    val paramType = nextSearchParam.type
                    when (StringUtils.defaultString(paramType)) {
                        "uri", "url", "code", "string" -> {
                            val type =
                                ctx?.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
                            type.valueAsString = "example"
                            param.value = type as Type
                          /*  parametersArray.addProperties(nextSearchParam.name,  Schema<String>()
                                .type("string")
                                .example("Bundle")
                                .minProperties(nextSearchParam.min)
                                .description(nextSearchParam.documentation)) */
                        }
                        "integer" -> {
                            val type =
                                ctx?.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
                            type.valueAsString = "0"
                            param.value = type as Type
                        }
                        "boolean" -> {
                            val type =
                                ctx?.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
                            type.valueAsString = "false"
                            param.value = type as Type
                        }
                        "CodeableConcept" -> {
                            val type = CodeableConcept()
                            type.codingFirstRep.system = "http://example.com"
                            type.codingFirstRep.code = "1234"
                            param.value = type
                        }
                        "Coding" -> {
                            val type = Coding()
                            type.system = "http://example.com"
                            type.code = "1234"
                            param.value = type
                        }
                        "Reference" -> {
                            val reference = Reference("example")
                            param.value = reference
                        }
                        "Resource" -> if (theResourceType != null) {
                            val resource = ctx.getResourceDefinition(theResourceType).newInstance()
                            resource.setId("1")
                            param.resource = resource as Resource
                        }
                    }
                }
            }
            var exampleRequestBodyString =
                ctx.newJsonParser()?.setPrettyPrint(true)
                    ?.encodeResourceToString(exampleRequestBody)
            /*
            val operationExample = getOperationExample(true,theOperationComponent)

            if (operationExample != null && operationExample.get() !=null ) {
                exampleRequestBodyString = FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true)
                    .encodeResourceToString(operationExample.get())
            }

             */
            theOperation.requestBody = RequestBody()
            theOperation.requestBody.content = Content()

            if (!theOperationDefinition.url.equals("http://hl7.org/fhir/OperationDefinition/MessageHeader-process-message")
                && !theOperationDefinition.url.equals("https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message")) {
                if (theOperationComponent.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
                    //
                    val exampleOperation = getOperationExample(true,theOperationComponent)
                    if (exampleOperation.size > 0) {
                        mediaType.examples = mutableMapOf<String,Example>()
                        for ( example in exampleOperation) {
                            var exampleName = "Example"
                            if (example.summary != null) exampleName = example.summary
                            mediaType.examples[exampleName] = example
                        }
                    } else {
                        mediaType.example = exampleRequestBodyString
                    }
                    //theOperation.requestBody.content = provideContentFhirResource(theOpenApi,ctx,exampleOperation, null)
                } else {
                    mediaType.example = exampleRequestBodyString
                }
            } else {
                mediaType.examples = mutableMapOf<String,Example>()
            }
            // TODO add in correct schema
            if (theOperationDefinition.url.equals("http://hl7.org/fhir/OperationDefinition/MessageHeader-process-message")
                || theOperationDefinition.url.equals("https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message")) {
                Schema<Any?>().type("object").title("Bundle-Message")

               // addSchemaFhirResource(openApi,bundleSchema,"Bundle-Message")
                addFhirResourceSchema(openApi,"Bundle","https://fhir.nhs.uk/StructureDefinition/NHSDigital-Bundle-FHIRMessage")
                mediaType.schema = ObjectSchema().`$ref`(
                    "#/components/schemas/Bundle"
                )
            } else {
                //addSchemaFhirResource(theOpenApi,parametersSchema,"Parameters-"+theOperationDefinition.code)
                mediaType.schema = ObjectSchema().`$ref`(
                    "#/components/schemas/Parameters"
                )

            }

            theOperation.requestBody.content.addMediaType(Constants.CT_FHIR_JSON_NEW, mediaType)

        }
        if (theOperationDefinition.hasParameter()) {
            var inDoc = "\n\n ## Parameters (In) \n\n |Name | Cardinality | Type | Profile | Documentation |\n |-------|-----------|-------------|------------|------------|"
            var outDoc = "\n\n ## Parameters (Out) \n\n |Name | Cardinality | Type | Profile | Documentation |\n |-------|-----------|-------------|------------|------------|"
            for (parameter in theOperationDefinition.parameter) {
                var entry = "\n |"+parameter.name + "|" + parameter.min + ".." + parameter.max + "|"
                if (parameter.hasType()) entry += parameter.type + "|"
                else entry += "|"
                if (parameter.hasTargetProfile()) entry += parameter.targetProfile + "|"
                else entry += "|"
                var documentation = ""
                if (parameter.hasDocumentation()) documentation += escapeMarkdown(parameter.documentation,true)
                if (parameter.hasPart()) {
                    documentation += "<br/><br/> <table>"
                    for (part in parameter.part) {
                        documentation += "<tr>"
                        documentation += "<td>"+part.name+"</td>"
                        documentation += "<td>"+ part.min + ".." + part.max +"</td>"
                        documentation += "<td>"+part.type+"</td>"
                        if (part.hasDocumentation()) {
                            documentation += "<td>"+part.documentation+"</td>"
                        } else {
                            documentation += "<td></td>"
                        }
                        documentation += "</tr>"
                    }
                    documentation += "</table>"
                }
                entry += documentation + "|"
                if (parameter.use == OperationDefinition.OperationParameterUse.IN) {
                    inDoc += entry
                } else {
                    outDoc += entry
                }
            }
            theOperation.description += inDoc
            theOperation.description += outDoc
        }
        if (theOperationDefinition.hasComment()) {
            theOperation.description += "\n\n ## Comment \n\n"+theOperationDefinition.comment
        }
        if (theOperationDefinition.url.equals("http://hl7.org/fhir/OperationDefinition/MessageHeader-process-message")
            || theOperationDefinition.url.equals("https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message")) {
            var supportedDocumentation = "\n\n ## Supported Messages \n\n"

            for (messaging in cs.messaging) {
                if (messaging.hasDocumentation()) {
                    supportedDocumentation += messaging.documentation +" \n"
                }
                for (supportedMessage in messaging.supportedMessage) {
                    val idStr = getProfileName(supportedMessage.definition)
                    supportedDocumentation += "* $idStr \n"
                    mediaType.examples[idStr] = getMessageExample(openApi, supportedMessage)
                }
            }
            theOperation.description += supportedDocumentation
        }
    }


    protected fun getPathItem(thePaths: Paths, thePath: String, theMethod: PathItem.HttpMethod?): Operation {
        val pathItem: PathItem?
        if (thePaths.containsKey(thePath)) {
            pathItem = thePaths[thePath]
        } else {
            pathItem = PathItem()
            thePaths.addPathItem(thePath, pathItem)
        }
        when (theMethod) {
            PathItem.HttpMethod.POST -> {
                assert(pathItem!!.post == null) { "Have duplicate POST at path: $thePath" }
                return pathItem.post(Operation()).post
            }
            PathItem.HttpMethod.GET -> {
                assert(pathItem!!.get == null) { "Have duplicate GET at path: $thePath" }
                return pathItem[Operation()].get
            }
            PathItem.HttpMethod.PUT -> {
                assert(pathItem!!.put == null)
                return pathItem.put(Operation()).put
            }
            PathItem.HttpMethod.PATCH -> {
                assert(pathItem!!.patch == null)
                return pathItem.patch(Operation()).patch
            }
            PathItem.HttpMethod.DELETE -> {
                assert(pathItem!!.delete == null)
                return pathItem.delete(Operation()).delete
            }
            PathItem.HttpMethod.HEAD, PathItem.HttpMethod.OPTIONS, PathItem.HttpMethod.TRACE -> throw IllegalStateException()
            else -> throw IllegalStateException()
        }
    }


    private fun addPatchResourceRequestBody(
        theOperation: Operation,
        theExampleSupplier: String?
    ) {
        val requestBody = RequestBody()
        requestBody.content = Content()
        requestBody.content.addMediaType("application/json-patch+json",
            MediaType()
                .example(theExampleSupplier)
                .schema(ObjectSchema().`$ref`(
                "JSONPATCH"
                    )
                )
        )

        theOperation.requestBody = requestBody
    }

    private fun addFhirResourceRequestBody(
        theOpenApi: OpenAPI,
        theOperation: Operation,
        theExampleSupplier: List<Example>,
        theResourceType: String?,
        profile: String?
    ) {
        val requestBody = RequestBody()
        requestBody.content = provideContentFhirResource(theOpenApi, theExampleSupplier,theResourceType, profile)
        theOperation.requestBody = requestBody
    }

    private fun addResourceVersionIdParameter(theOperation: Operation) {
        val parameter = Parameter()
        parameter.name = "version_id"
        parameter.setIn("path")
        parameter.description = "The resource version ID"
        parameter.example = "1"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)
    }

    private fun addFhirResourceResponse(
        theFhirContext: FhirContext?,
        theOpenApi: OpenAPI,
        theOperation: Operation,
        theResourceType: String?,
        resftfulIntraction : CapabilityStatement.ResourceInteractionComponent?,
        operationComponent: CapabilityStatement.CapabilityStatementRestResourceOperationComponent?,
        profile: String?
    ) {
        theOperation.responses = ApiResponses()
        val response200 = ApiResponse()
        response200.description = "Success"
        if (resftfulIntraction != null) {
            val exampleResponse = getInteractionResponseExample(resftfulIntraction)

            response200.content = provideContentFhirResource(
                theOpenApi,
                exampleResponse,
                theResourceType,
                profile
            )

        }
        if (operationComponent != null) {
            val exampleResponse = getOperationResponseExample(operationComponent)

            response200.content = provideContentFhirResource(
                theOpenApi,
                exampleResponse,
                theResourceType,
                profile
            )

        }
        if (response200.content == null) {

            response200.content = provideContentFhirResource(
                theOpenApi,
                genericExampleSupplier(theFhirContext, theResourceType),
                theResourceType,
                profile
            )
        }

        theOperation.responses.addApiResponse("200", response200)

        addStandardResponses(theOpenApi,theOperation.responses)

    }

    private fun addStandardResponses(theOpenApi: OpenAPI,responses: ApiResponses) {
        val response4xx = ApiResponse()
        var example = Example()
        example.value = getErrorOperationOutcome()
        var exampleResponse = mutableListOf<Example>()
        exampleResponse.add(example)
        response4xx.content = provideContentFhirResource(
            theOpenApi,
            exampleResponse,
            "OperationOutcome",
            null
        )
        responses.addApiResponse("4xx", response4xx)

        if (cs.restFirstRep.hasSecurity()) {
            val response403 = ApiResponse()
            example = Example()
            example.value = getForbiddenOperationOutcome()
            exampleResponse = mutableListOf()
            exampleResponse.add(example)
            response403.content = provideContentFhirResource(
                theOpenApi,
                exampleResponse,
                "OperationOutcome",
                null
            )
            responses.addApiResponse("403", response403)
        }
    }
    private fun getSuccessOperationOutcome() : String? {
        val operationOutcome = OperationOutcome()
        operationOutcome.meta = Meta().setLastUpdatedElement(InstantType("2021-04-14T11:35:00+00:00"))
        val issue = operationOutcome.addIssue()
        issue.severity = OperationOutcome.IssueSeverity.INFORMATION
        issue.code = OperationOutcome.IssueType.INFORMATIONAL
        return ctx?.newJsonParser()?.setPrettyPrint(true)?.encodeResourceToString(operationOutcome)
    }

    private fun getForbiddenOperationOutcome() : String? {
        val operationOutcome = OperationOutcome()
        operationOutcome.meta = Meta().setLastUpdatedElement(InstantType("2021-04-14T11:35:00+00:00"))
        val issue = operationOutcome.addIssue()
        issue.severity = OperationOutcome.IssueSeverity.ERROR
        issue.code = OperationOutcome.IssueType.FORBIDDEN
        issue.details = CodeableConcept().addCoding(
            Coding().setSystem("https://fhir.nhs.uk/CodeSystem/Spine-ErrorOrWarningCode")
                .setCode("ACCESS_DENIED"))
        return ctx?.newJsonParser()?.setPrettyPrint(true)?.encodeResourceToString(operationOutcome)
    }

    private fun getErrorOperationOutcome() : String? {
        val operationOutcome = OperationOutcome()
        operationOutcome.meta = Meta().setLastUpdatedElement(InstantType("2021-04-14T11:35:00+00:00"))
        val issue = operationOutcome.addIssue()
        issue.severity = OperationOutcome.IssueSeverity.ERROR
        issue.code = OperationOutcome.IssueType.VALUE
        issue.details = CodeableConcept().addCoding(
            Coding().setSystem("https://fhir.nhs.uk/CodeSystem/Spine-ErrorOrWarningCode")
                .setCode("INVALID_VALUE"))
        issue.diagnostics = "(invalid_request) firstName is missing"
        issue.addExpression("Patient.name.given")
        return ctx?.newJsonParser()?.setPrettyPrint(true)?.encodeResourceToString(operationOutcome)
    }

    private fun getMessageExample(openApi: OpenAPI,supportedMessage : CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent) : Example {

        var supportedDocumentation = ""
        val example = Example()

        if (supportedMessage.hasDefinition()) {
            supportedDocumentation += " \n\n MessageDefinition.url = **"+ supportedMessage.definition+"** \n"

            for (resourceChain in supportChain.fetchAllConformanceResources()!!)
                if (resourceChain is MessageDefinition) {
                    if (resourceChain.url == supportedMessage.definition) {
                        if (resourceChain.hasDescription()) {
                            example.summary = resourceChain.description
                        }
                        if (resourceChain.hasPurpose()) {
                            supportedDocumentation += "\n ### Purpose" + resourceChain.purpose
                        }
                        if (resourceChain.hasEventCoding()) {
                            supportedDocumentation += " \n\n The first Bundle.entry **MUST** be a FHIR MessageHeader with \n MessageHeader.eventCoding = **" + resourceChain.eventCoding.code + "** \n"
                        }
                        if (resourceChain.hasFocus()) {
                            supportedDocumentation += "\n\n | Resource | Profile | Min | Max | \n"
                            supportedDocumentation += "|----------|---------|-----|-----| \n"
                            for (foci in resourceChain.focus) {
                                val min = foci.min
                                val max = foci.max
                                val resource = foci.code
                                var profile = foci.profile
                                if (profile == null) {
                                    profile = ""
                                } else {
                                    profile = getDocumentationPath(profile)
                                    addFhirResourceSchema(openApi, foci.code, foci.profile)
                                    addResoureTag(openApi, foci.code,foci.profile)
                                }
                                val idStr = getProfileName(foci.profile)
                                supportedDocumentation += "| [$resource](https://www.hl7.org/fhir/$resource.html) | [$idStr]($profile) | $min | $max | \n"
                            }
                        }
                        // only process this loop once
                        break
                    }
                }



            supportedDocumentation += "\n"
        }

        if (supportedMessage.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val apiExtension =
                supportedMessage.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")
            for (extension in apiExtension.getExtensionsByUrl("example")) {
                val messageExample =  getExampleFromPackages(true, extension,false)
                example.value = ctx?.newJsonParser()?.encodeResourceToString(messageExample?.get())
            }
        }
        example.description = supportedDocumentation
        return example
    }

    private fun getExampleFromPackages(request: Boolean, extension: Extension, create : Boolean) : Supplier<IBaseResource?>? {
        // TODO Return array of examples including documentation
        val path = (extension.getExtensionByUrl("value").value as Reference).reference
        val pathParts = path.split("/")
        val requestExt = extension.getExtensionByUrl("request")

        if ((requestExt.value as BooleanType).value == request && extension.hasExtension("value")) {
            for (npmPackage in npmPackages!!) {
                if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                    implementationGuideParser?.getResourcesFromPackage(npmPackage)
                        ?.forEach {
                            if (it is Resource) {
                                val resource: Resource = it

                                if (resource.resourceType.name == pathParts.get(0)) {
                                    //println("Match "+ resource.idElement.idPart + " - resource.id=" + resource.id + " - "+ path + " - pathParts.get(1)="+pathParts.get(1))
                                    if (resource.id !=null && (resource.idElement.idPart.equals(pathParts.get(1)) || resource.id.equals(path))) {
                                        //println("*** Matched")
                                        if (create) resource.id = null
                                        return Supplier {
                                            val example: IBaseResource = resource
                                            example
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            println("----- Not found " + request + path)
        }
        return null
    }
    private fun getRequestExample(interaction : CapabilityStatement.ResourceInteractionComponent) : List<Example>{
        //
        val examples = mutableListOf<Example>()
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")
            if (apiExtension.hasExtension("example")) {
                for (exampleExt in apiExtension.getExtensionsByUrl("example")) {
                    val supplierExample = getExampleFromPackages(true, exampleExt, (interaction.code == CapabilityStatement.TypeRestfulInteraction.CREATE))?.get()
                    val exampleOAS = Example()
                    examples.add(exampleOAS)
                    if (supplierExample != null) {
                        exampleOAS.value = ctx?.newJsonParser()?.encodeResourceToString(supplierExample)
                    }
                    if (exampleExt.hasExtension("summary")) {
                        exampleOAS.summary = (exampleExt.getExtensionString("summary") as String)
                    }
                    if (exampleExt.hasExtension("description")) {
                        exampleOAS.description = (exampleExt.getExtensionString("description") as String)
                    }
                }
            }
        }
        return examples
    }

    private fun getOperationResponseExample(operationComponent: CapabilityStatement.CapabilityStatementRestResourceOperationComponent) : List<Example> {
        if (operationComponent.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val apiExtension =
                operationComponent.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")
            return getResponseExample(apiExtension,false, false)
        }
        return emptyList()
    }

    private fun getInteractionResponseExample(interaction : CapabilityStatement.ResourceInteractionComponent) : List<Example> {


        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val searchSet = (interaction.code == CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE)
            val createCall = (interaction.code == CapabilityStatement.TypeRestfulInteraction.CREATE
                    || interaction.code == CapabilityStatement.TypeRestfulInteraction.UPDATE)
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")
            return getResponseExample(apiExtension,searchSet,createCall)
        }
        return emptyList()
    }
    private fun getResponseExample(apiExtension : Extension,searchExample : Boolean,createCall : Boolean) : List<Example> {
        val examples = mutableListOf<Example>()
        if (apiExtension.hasExtension("example")) {

            for (exampleExt in apiExtension.getExtensionsByUrl("example")) {
                val exampleOAS = Example()
                examples.add(exampleOAS)
                val supplierExample = getExampleFromPackages(false, exampleExt, false)

                if (supplierExample != null && supplierExample.get() != null) {
                    var example = supplierExample.get()
                    // Allow autogeneration of searchset bundles from resource examples
                    if (searchExample && supplierExample.get() !is Bundle) {
                        val bundle = Bundle()
                        bundle.type = Bundle.BundleType.SEARCHSET
                        bundle.addLink(
                            Bundle.BundleLinkComponent()
                                .setRelation("self")
                                .setUrl(exampleServer + exampleServerPrefix + (example as Resource).resourceType + "?parameterExample=123&page=1")
                        )
                        bundle.addLink(
                            Bundle.BundleLinkComponent()
                                .setRelation("next")
                                .setUrl(exampleServer + exampleServerPrefix + (example).resourceType + "?parameterExample=123&page=2")
                        )
                        bundle.entry.add(
                            Bundle.BundleEntryComponent().setResource(example)
                                .setFullUrl(exampleServer + exampleServerPrefix + (example).resourceType + "/" + (supplierExample.get() as Resource).id)
                        )
                        bundle.total = 1
                        example = bundle
                    }

                    if (createCall) {
                        val operation = OperationOutcome()
                        operation.issue.add(
                            OperationOutcome.OperationOutcomeIssueComponent()
                                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                        )
                    }
                    if (exampleExt.hasExtension("summary")) {
                        exampleOAS.summary = (exampleExt.getExtensionString("summary") as String)
                    }
                    if (exampleExt.hasExtension("description")) {
                        exampleOAS.description =
                            escapeMarkdown((exampleExt.getExtensionString("description") as String), true)
                    }
                    exampleOAS.value = ctx?.newJsonParser()?.encodeResourceToString(example)

                }
            }
        }
        return examples
    }
    private fun getOperationResponeExample(interaction: CapabilityStatement.CapabilityStatementRestResourceOperationComponent) : Supplier<IBaseResource?>? {
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")
            for (extension in apiExtension.getExtensionsByUrl("example")) {
                return getExampleFromPackages(false, extension,false)
            }
        }
        return null
    }
    private fun getOperationExample(request: Boolean,interaction: CapabilityStatement.CapabilityStatementRestResourceOperationComponent) : List<Example> {
        val examples = mutableListOf<Example>()
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Examples")

            for (exampleExt in apiExtension.getExtensionsByUrl("example")) {

                val supplierExample = getExampleFromPackages(request, exampleExt, false)
                if (supplierExample != null) {
                    if (supplierExample != null && supplierExample.get() != null) {
                        val exampleOAS = Example()
                        examples.add(exampleOAS)

                        var example = supplierExample.get()
                        if (exampleExt.hasExtension("summary")) {
                            exampleOAS.summary = (exampleExt.getExtensionString("summary") as String)
                        }
                        if (exampleExt.hasExtension("description")) {
                            exampleOAS.description =
                                escapeMarkdown((exampleExt.getExtensionString("description") as String), true)
                        }
                        exampleOAS.value = ctx?.newJsonParser()?.encodeResourceToString(example)
                    }
                }
            }
        }
        return examples
    }

    private fun genericExampleSupplier(
        theFhirContext: FhirContext?,
        theResourceType: String?
    ): List<Example> {
        val exampleList = mutableListOf<Example>()
        val example = Example()
        exampleList.add(example)
        if (theResourceType == "CapabilityStatement") {
            example.value = ctx?.newJsonParser()?.encodeResourceToString(this.cs)
        } else if (theResourceType == "OperationOutcome") {
            example.value = getSuccessOperationOutcome()
        }
        else {
            if (theResourceType != null) {
                val resource = theFhirContext!!.getResourceDefinition(theResourceType).newInstance()
                example.value = ctx?.newJsonParser()?.encodeResourceToString(resource)
            }
        }
        return exampleList
    }


    private fun provideContentFhirResource(
        theOpenApi: OpenAPI,
        examples: List<Example>,
        resourceType: String?,
        profile: String?
    ): Content {
        val retVal = Content()
        var resourceType2 = resourceType
        //addSchemaFhirResource(theOpenApi)

        if (examples.isEmpty()) {
           val example = Example()
           val generic = genericExampleSupplier(ctx,resourceType)
           example.value = generic.get(0).value

            val theExampleSupplier = ctx?.newJsonParser()?.parseResource(example.value as String)

            if (resourceType2 == null && theExampleSupplier != null)
                resourceType2 = theExampleSupplier.fhirType()
           // if (resourceType2 != null) addJSONSchema(theOpenApi, resourceType2)
            val jsonSchema = resourceType2?.let { getMediaType(theOpenApi, it, profile) }

            if (theExampleSupplier != null) {
                if (jsonSchema != null) {
                    jsonSchema.example = example.value
                }
            }
            retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
            val xmlSchema = resourceType2?.let { getMediaType(theOpenApi, it, profile) }

            if (theExampleSupplier != null) {
                if (xmlSchema != null) {
                    xmlSchema.example = example.value
                }
            }
            if (generateXML) retVal.addMediaType(Constants.CT_FHIR_XML_NEW, xmlSchema)
        } else {
            val jsonSchema = getMediaType(theOpenApi,resourceType2, profile)

            // Ensure schema is added
            //if (resourceType2 != null) addJSONSchema(theOpenApi, resourceType2)
            retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
            jsonSchema.examples = mutableMapOf<String,Example>()
            for (example in examples) {
                var key = example.summary
                if (example.value == null) {
                    val generic = genericExampleSupplier(ctx,resourceType)
                    example.value = generic.get(0).value
                }
                if (key == null) key = "example"
                jsonSchema.examples[key] = example
            }
        }
        return retVal
    }

    private fun addResourceIdParameter(theOperation: Operation) {
        val parameter = Parameter()
        parameter.name = "id"
        parameter.setIn("path")
        parameter.description = "The resource ID"
        parameter.example = "6160eb19-6fc3-4b43-953a-54ea01dc1cf4"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)
    }

    private fun addResourceAPIMParameter(theOperation: Operation) {

        return;
        /*
        var parameter = Parameter()

        if (cs.restFirstRep.hasSecurity()) {
            parameter.name = "Authorization"
            parameter.setIn("header")
            parameter.required = true
            parameter.description =
                "An [OAuth 2.0 bearer token](https://digital.nhs.uk/developer/guides-and-documentation/security-and-authorisation#user-restricted-apis).\n" +
                        "\n" +
                        "Required in all environments except sandbox."
            parameter.example = "Bearer g1112R_ccQ1Ebbb4gtHBP1aaaNM"
            parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1)).pattern("^Bearer\\ [[:ascii:]]+$")
            parameter.style = Parameter.StyleEnum.SIMPLE
            theOperation.addParametersItem(parameter)

            parameter = Parameter()
        }

        parameter.name = "NHSD-Session-URID"
        parameter.setIn("header")
        parameter.description = "The user role ID (URID) for the current session. Also known as a user role profile ID (URPID)."
        parameter.example = "555254240100"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1)).pattern("^[0-9]+$")
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)

        parameter = Parameter()
        parameter.name = "X-Request-ID"
        parameter.required = true
        parameter.setIn("header")
        parameter.description = "A globally unique identifier (GUID) for the request, which we use to de-duplicate repeated requests and to trace the request if you contact our helpdesk"
        parameter.example = "60E0B220-8136-4CA5-AE46-1D97EF59D068"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1)).pattern("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)

        parameter = Parameter()
        parameter.name = "X-Correlation-ID"
        parameter.setIn("header")
        parameter.description = "An optional ID which you can use to track transactions across multiple systems. It can have any value, but we recommend avoiding `.` characters."
        parameter.example = "11C46F5F-CDEF-4865-94B2-0EE0EDCC26DA"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)

         */
    }
/*
    protected fun getIndexTemplate(): ClassLoaderTemplateResource? {
        return ClassLoaderTemplateResource(
            myResourcePathToClasspath["/swagger-ui/index.html"],
            StandardCharsets.UTF_8.name()
        )
    }

    fun setBannerImage(theBannerImage: String?) {
        myBannerImage = theBannerImage
    }

    fun getBannerImage(): String? {
        return myBannerImage
    }
*/

    private fun getSearchParameterDocumentation(nextSearchParam: CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent,
                                                originalResourceType: String,
                                                parameter: Parameter, first : Boolean) : String
    {
        var searchParameter : SearchParameter?


        val parameters = nextSearchParam.name.split(".")

        val modifiers = parameters.get(0).split(":")

        val name = modifiers.get(0)


        if (!nextSearchParam.hasDefinition()) {
            searchParameter = searchParameterSupport.getSearchParameter(originalResourceType,name)

        } else searchParameter = searchParameterSupport.getSearchParameterByUrl(nextSearchParam.definition)


        // Create local copy as we are changing the definition

        searchParameter = searchParameter?.copy()

        // Filter expression to get resource focused expression

        var expression : String = searchParameter?.expression.toString()
        if (expression.split("|").size>1) {
            val exps = expression.split("|")
            for (exp in exps) {
                if (exp.replace(" ","").startsWith(originalResourceType)) {
                    if (searchParameter != null) {
                        searchParameter.expression = exp.removeSuffix(" ")
                    }
                }
            }
        }

        // Filter description to get resource focus description

        var description = ""
        if (searchParameter?.description != null) {
            var desc = searchParameter.description
            if (desc.split("*").size>1) {
                val exps = desc.split("*")
                for (exp in exps) {
                    if (exp.replace(" [","").startsWith(originalResourceType)) {
                        desc = exp
                    }
                }
                searchParameter.description = desc
            }
        }


        // Check for identifer and swap definition
        if (modifiers.size>1 && searchParameter != null) {
            val modifier = modifiers.get(1)
            // Don't alter original

            if (modifier == "identifier") {
                searchParameter.code += ":" + modifier
                searchParameter.type = Enumerations.SearchParamType.TOKEN
                val chain = searchParameter.expression.split(".")
                var expressionStr = ""
                // May fail if a complex chain is use ... but maybe that should not be allowed due to complexity
                for (chainItem in chain) if (!chainItem.startsWith("where(resolve()")) expressionStr += chainItem + "."
                if (!expressionStr.endsWith("identifier.")) expressionStr += "identifier"
                expressionStr = expressionStr.removeSuffix(".")
                searchParameter.expression = expressionStr
            } else {
                // Assume resource
                searchParameter.expression += ".where(resolve() is $modifier)"
            }
        }


        val type = searchParameter?.type?.display
        expression = searchParameter?.expression.toString()

        // Removed unsafe charaters
        if (searchParameter != null) {
            description = escapeMarkdown(description, false)
            expression = expression.replace("|","&#124;")
        }

        if (searchParameter != null) {
            when (searchParameter.type) {
                Enumerations.SearchParamType.TOKEN -> {
                    val array =  ArraySchema()
                    array.items = StringSchema().format("token")
                    // Should really be added to the StringSchema only but this gets around UI issues
                    array.format("token")
                    parameter.schema = array
                   // parameter.schema.type = "string"
                    parameter.schema.example = "[system]|[code],[code],[system]"
                }
                Enumerations.SearchParamType.REFERENCE -> {
                    parameter.schema = StringSchema().format("reference")
                    parameter.schema.example = "[type]/[id] or [id] or [uri]"
                }
                Enumerations.SearchParamType.DATE -> {
                    val array =  ArraySchema()
                    array.items = StringSchema().format("date")
                    // Should really be added to the StringSchema only but this gets around UI issues
                    array.format("date")
                    parameter.schema = array
                    parameter.description = "See FHIR documentation for more details."
                    parameter.schema.example = "eq2013-01-14"
                }
                Enumerations.SearchParamType.STRING -> {
                    parameter.schema = StringSchema().type("string")
                    parameter.schema.example = "LS15"
                }
                else -> {
                    parameter.schema = StringSchema().format(nextSearchParam.type.toCode())
                }
            }
        }

        if (parameters.size>1) {
            description += "\n\n Chained search parameter. Please see [chained](http://www.hl7.org/fhir/search.html#chaining)"
        }
        if (first) description += "\n\n | Name | Type |  Expression | \n |--------|--------|--------| \n "
        if (searchParameter != null) {
            description += "| $name | [" + type?.lowercase() + " ](https://www.hl7.org/fhir/search.html#" + type?.lowercase() + ")|  $expression | \n"
        } else {
            description += "\n\n Caution: This does not appear to be a valid search parameter. **Please check HL7 FHIR conformance.**"
        }

        if (parameters.size>1) {
            if (searchParameter?.type != Enumerations.SearchParamType.REFERENCE) {
                description += "\n\n Caution: This does not appear to be a valid search parameter. Chained search paramters **MUST** always be on reference types Please check Hl7 FHIR conformance."
            } else {
                //val secondNames= parameters.get(1).split(":")
                var resourceType: String?

                // A bit coarse
                resourceType = "Resource"
                if (searchParameter.hasTarget() ) {
                    for (resource in searchParameter.target) {
                        if (!resource.code.equals("Group")) resourceType=resource.code
                    }
                }

                val newSearchParam = CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                newSearchParam.name = parameters.get(1)
                // Add back in remaining chained parameters
                for (i in 3..parameters.size) {
                    newSearchParam.name += "."+parameters.get(i)
                }
                description += resourceType?.let { getSearchParameterDocumentation(newSearchParam, it, parameter,false) }
            }
        }
        return description
    }

    private fun escapeMarkdown(markdown: String, tableReplace : Boolean): String {
        var description = markdown.replace("\r","<br/>")
        description = description.replace("\n","")
        if (tableReplace) description = description.replace("|","&#124;")
        return description
    }

    private fun getMediaType(openApi: OpenAPI, resourceType: String?, profile: String?) : MediaType {
        val mediaType = MediaType().schema(ObjectSchema().`$ref`(
            "#/components/schemas/$resourceType"
            //"https://hl7.org/fhir/R4/fhir.schema.json#/definitions/$resourceType"
        ))
        addFhirResourceSchema(openApi,resourceType, profile)
        return mediaType
    }

    /*
    private fun addSchemaFhirResource(openApi: OpenAPI, schema : Schema<Any?>, schemaName : String?) {
        ensureComponentsSchemasPopulated(openApi)
        if (!openApi.components.schemas.containsKey(schemaName)) {
            openApi.components.addSchemas(schemaName,schema)
        }
    }
*/

    private fun ensureComponentsSchemasPopulated(theOpenApi: OpenAPI) {
        if (theOpenApi.components == null) {
            theOpenApi.components = Components()
        }
        if (theOpenApi.components.schemas == null) {
            theOpenApi.components.schemas = LinkedHashMap()
        }
    }

    private fun addFhirResourceSchema(openApi: OpenAPI, resourceType: String?, profile: String?) {
        // Add schema
        ensureComponentsSchemasPopulated(openApi)
        if (!openApi.components.schemas.containsKey(resourceType)) {

            val schema = ObjectSchema()

            var schemaList = mutableMapOf<String, Schema<Any>>()

            schema.description =
                "HL7 FHIR Schema [$resourceType](https://hl7.org/fhir/R4/fhir.schema.json#/definitions/$resourceType)." + ". HL7 FHIR Documentation [$resourceType](\"https://www.hl7.org/fhir/$resourceType.html\")"


            // This doesn't appear to be used. Consider removing
            schema.externalDocs = ExternalDocumentation()
            schema.externalDocs.description = resourceType

            schema.externalDocs.url = "https://www.hl7.org/fhir/$resourceType.html"

            if (resourceType != null) {
                schemaList.put(resourceType, schema)
            }
            if (profile != null) {
                val structureDefinition = getProfile(profile)
                if (structureDefinition is StructureDefinition) {

                    if (structureDefinition.hasDescription()) {
                        schema.description += "\n\n " + structureDefinition.description
                    }
                    if (structureDefinition.hasPurpose()) {
                        schema.description += "\n\n " + structureDefinition.purpose
                    }

                    for (element in structureDefinition.snapshot.element) {
                        if ((element.hasDefinition() ||
                                    element.hasShort() ||
                                    element.hasType() ||
                                    element.hasBinding()) &&
                            (element.hasMustSupport() || (element.hasMin() && element.min > 0))
                        ) {
                            val paths = element.id.split(".")
                            var title = paths[paths.size - 1]
                            /* if (element.hasSliceName()) {
                                title += " (" + element.sliceName + ")"
                            }*/
                            var elementSchema: Schema<Any>? = null

                            if (element.hasType()) {
                                if (element.typeFirstRep.code[0].isUpperCase()) {
                                    elementSchema = Schema<ObjectSchema>().type("object")
                                } else {
                                    elementSchema = Schema<String>()
                                        .type("string")
                                }
                            } else {
                                elementSchema = Schema<String>()
                                    .type("string")
                            }

                            if (element.hasBase() && element.base.max.equals("*")) {
                                elementSchema = ArraySchema().type("array").items(elementSchema)
                            }

                            if (elementSchema != null) {
                                elementSchema.description = getElementDescription(element)
                                if (element.hasMin()) elementSchema.minimum = BigDecimal(element.min)


                                var parent = ""
                                for (i in 0 until (paths.size - 1)) {
                                    if (!parent.isEmpty()) parent += "."
                                    parent += paths[i]
                                }
                                if (schemaList.get(element.id) == null) {
                                    schemaList.put(element.id, elementSchema)
                                }
                                val parentSchema = schemaList.get(parent)
                                if (parentSchema is ArraySchema) {
                                    parentSchema.items.addProperties(title, elementSchema)
                                } else {
                                    parentSchema?.addProperties(title, elementSchema)
                                }
                            }
                        }
                    }
                }
            }
            if (resourceType == null) {
                System.out.println("resourceTyoe null")
            } else {
                openApi.components.addSchemas(resourceType, schema)

            }
        }
    }
    private fun addSchemaProperties(searchParam: CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent, parametersItem : Parameter) {
        if (searchParam.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-QueryParameters")) {
            val extension =
                searchParam.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-QueryParameters")

            if (extension.hasExtension("required")) {
                parametersItem.required = ((extension.getExtensionByUrl("required").value as BooleanType).value)
            }
            if (extension.hasExtension("minimum")) {
                parametersItem.schema.minimum =
                    ((extension.getExtensionByUrl("minimum").value as IntegerType).value).toBigDecimal()
            }
            if (extension.hasExtension("maximum")) {
                parametersItem.schema.maximum =
                    ((extension.getExtensionByUrl("maximum").value as IntegerType).value).toBigDecimal()
            }
            if (extension.hasExtension("exampleParameter")) {
                parametersItem.schema.example =
                    ((extension.getExtensionByUrl("exampleParameter").value as StringType).value)
            }
            if (extension.hasExtension("allowedValues")) {

                val reference = (extension.getExtensionByUrl("allowedValues").value as Reference)
                val valueSet = supportChain.fetchValueSet(reference.reference)
                if (valueSet !=null && valueSet is ValueSet) {
                    if (reference.reference.startsWith("http://hl7.org")) {
                        parametersItem.description += "\n\n A code from FHIR ValueSet [" +  (valueSet).name + "]("+(valueSet).url + ")"
                    } else {
                        parametersItem.description += "\n\n A code from FHIR ValueSet [" +  (valueSet).name + "](https://simplifier.net/guide/nhsdigital/home)"
                    }
                    val expansionOutcome = supportChain.expandValueSet(validationSupportContext,ValueSetExpansionOptions() ,valueSet)
                    if (expansionOutcome != null && expansionOutcome.valueSet != null && expansionOutcome.valueSet is ValueSet) {
                        for (expansion in (expansionOutcome.valueSet as ValueSet).expansion.contains) {
                            if (extension.hasExtension("showCodeAndSystem") && !(extension.getExtensionByUrl("showCodeAndSystem").value as BooleanType).booleanValue()) {
                                parametersItem.schema.addEnumItemObject(expansion.code)
                            } else {
                                parametersItem.schema.addEnumItemObject(expansion.system + "|" + expansion.code)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getProfileDescription(profile: String) :String {
        if (profile.startsWith("https://www.hl7.org")) return ""
        // todo disable for now and try using schema instead
        return ""
        /*
        val structureDefinition = supportChain.fetchStructureDefinition(profile)
        var description = ""
        if (structureDefinition is StructureDefinition) {
            for (element in structureDefinition.snapshot.element) {
                if ((element.hasDefinition() ||
                    element.hasShort() ||
                    element.hasType() ||
                    element.hasBinding()) &&
                    element.hasMustSupport()
                ) {
                    // section title
                    description += "\n\n ### " + element.path.replace(structureDefinition.type + ".", "")
                    if (element.hasSliceName()) {
                       description += " ("+element.sliceName+ ")"
                    }

                description += getElementDescription(element)

                }
            }
        }
        return description

         */
    }

    private fun getElementDescription(element : ElementDefinition) : String {

        var table = "\n\n| | |\n|----|----|"
        // header body
        /*
        if (element.hasMustSupport() && element.mustSupport) {
            description += "\n `mustSupport`"
        }
*/
        table += "\n|Element Id|"+element.id+"|"
        table += "\n|[Cardinality](https://www.hl7.org/fhir/conformance-rules.html#cardinality)|"+element.min+".."+element.max+"|"


        if (element.hasFixed()) {
            if (element.fixed is UriType) {
                table += "\n|Fixed Value|"+(element.fixed as UriType).value+"|"
            }
            if (element.fixed is CodeType) {
                table += "\n|Fixed Value|"+(element.fixed as CodeType).value+"|"
            }
        }

        if (element.hasBinding()) {
            if (element.binding.hasValueSet())
            {
                val valueSet = supportChain.fetchValueSet(element.binding.valueSet)
                var description = "["+(valueSet as ValueSet).name + "](" + getCanonicalUrl(element.binding.valueSet) +")"
                if (element.binding.hasStrength()) description += " (" + element.binding.strength.display + ")"
                if (element.binding.hasDescription()) {
                    var elementDescription = element.binding.description
                    elementDescription = " <br/>" + elementDescription.replace("\\n","\n")
                    description += elementDescription + " "
                }
                table += "\n|[Terminology Binding](https://www.hl7.org/fhir/terminologies.html)|"+description+"|"
            }
        }

        if (element.hasSliceName()) {
            table += "\n|[Slice Name](https://www.hl7.org/fhir/profiling.html#slicing)|"+element.sliceName+"|"
        }
        if (element.hasSlicing()) {
            var description = ""
            if (element.slicing.hasRules()) {
                description += " *"+element.slicing.rules.name + "*"
            }
            if (element.slicing.hasDiscriminator()) {
                for (discrimninator in element.slicing.discriminator) {
                    description += " discriminator - "
                    if (discrimninator.hasType()) {
                        description += " *"+discrimninator.type.name + "*"
                    }
                    if (discrimninator.hasPath()) {
                        description += " *"+discrimninator.path + "*"
                    }
                }
            }
            if (element.slicing.hasDescription()) {
                description += "<br/> "+element.slicing.description
            }
            table += "\n|[Slicing](https://www.hl7.org/fhir/profiling.html#slicing)|"+description+"|"
        }

        // Data type
        if (element.hasType()) {
            var description = ""
            for (type in element.type) {

                description += "["+type.code+"](https://www.hl7.org/fhir/datatypes.html#"+type.code+")"
                var itemDescription=""
                var first = true
                for (target in type.targetProfile) {
                    if (itemDescription.isEmpty()) description+= "("
                    val profile = supportChain.fetchStructureDefinition(target.value)
                    if (!first) {
                        itemDescription += " "
                    } else {
                        first = false
                    }
                    itemDescription += "["+(profile as StructureDefinition).name + "]("+ getCanonicalUrl(target.value) +")"

                }
                first = true
                for (target in type.profile) {
                    if (itemDescription.isEmpty()) description+= "("
                    val profile = supportChain.fetchStructureDefinition(target.value)
                    if (!first) {
                        itemDescription += " "
                    } else {
                        first = false
                    }
                    itemDescription += "["+(profile as StructureDefinition).name+ "](" +getCanonicalUrl(target.value) +")"
                }

                if (itemDescription.isNotEmpty()) description+= itemDescription + ")"
                if (type.hasAggregation()) {
                    for (aggregation in type.aggregation) {
                        description += "<br/> Aggregation - [" + aggregation.code+"](http://www.hl7.org/fhir/valueset-resource-aggregation-mode.html)"
                    }
                }
            }
            table += "\n|[type](https://www.hl7.org/fhir/datatypes.html)|"+description+"|"
        }
        var description = table + "\n\n<br/>"
        // Documentation section
        /*
        if (element.hasShort()) {

            description += "\n\n " + element.short
        }
         */

        if (element.hasDefinition()) {
            description += "\n\n #### Definition"
            description += "\n\n " + element.definition.replace("\\n","\n")
        }

        if (element.hasRequirements()) {
            description += "\n\n #### Requirements"
            description += "\n\n " + element.requirements.replace("\\n","\n")
        }

        if (element.hasComment()) {
            description += "\n\n #### Comment"
            description += "\n\n " + element.comment.replace("\\n","\n")
        }

        if (element.hasConstraint()) {
            var displayConstraints = false
            for (constraint in element.constraint) {
                if (doDisplay(constraint.key)) displayConstraints = true
            }

            if (displayConstraints) {
                description += "\n\n #### Constraints \n"
                for (constraint in element.constraint) {
                    if (doDisplay(constraint.key)) description += "\n- **" + constraint.key + "** (*" + constraint.severity + "*) " + constraint.human.replace(
                        "\\n",
                        "\n"
                    )
                }
            }
        }

        return description
    }

    private fun doDisplay(key : String) : Boolean {
        if (key.startsWith("ext")) return false
        if (key.startsWith("ele")) return false
        if (key.startsWith("dom")) return false
        return true
    }

    private fun addResoureTag(openApi: OpenAPI,resourceType: String?, profile: String?) {
        // potential flaw here if multiple profiles are used.
        for (tags in openApi.tags) {
            if (tags.name.equals(resourceType)) return
        }
        // Try using schema for describing the profile .
        addFhirResourceSchema(openApi,resourceType,profile)
        val resourceTag = Tag()
        resourceTag.name = resourceType

        //addFhirResourceSchema(openApi, resourceType, nextResource.profile)

        if (profile != null) {
            resourceTag.extensions = mutableMapOf<String,Any>()
            resourceTag.extensions.put("x-HL7-FHIR-Profile",profile)
            val idStr = getProfileName(profile)
            val documentation = getDocumentationPath(profile)
            resourceTag.description = "" //""Resource (schema): [FHIR $resourceType](https://www.hl7.org/fhir/$resourceType.html)"

            var profileDef = getBaseProfile(profile)

            if (profileDef !=null) {
                if (profileDef.hasDescription()) {
                    resourceTag.description += "\n\n "+profileDef.description
                }
                if (profileDef.hasPurpose()) {
                    resourceTag.description += "\n\n "+profileDef.purpose
                }
            }

            resourceTag.description += "\n\n Profile (constraints): [$idStr]($documentation)"
            resourceTag.description += "\n\n" + getProfileDescription(profile)

            profileDef = getProfile(profile)

            if (profileDef !=null) {

                if (profileDef.hasDescription()) {
                    resourceTag.description += "\n\n "+profileDef.description
                }
                if (profileDef.hasPurpose()) {
                    resourceTag.description += "\n\n "+profileDef.purpose
                }
            }

        } else {
            resourceTag.description = "Resource type: $resourceType"
        }
        openApi.addTagsItem(resourceTag)
    }

    fun generateMarkdown(profile :String) : String {
        var mainDescription = ""
        var subDescription = ""
        var index = ""


        if (profile != null) {
            val structureDefinition = getProfile(profile)
            if (structureDefinition is StructureDefinition) {

                if (structureDefinition.hasDescription()) {
                    mainDescription += "\n\n " + structureDefinition.description
                }
                if (structureDefinition.hasPurpose()) {
                    mainDescription += "\n\n " + structureDefinition.purpose
                }

                for (element in structureDefinition.snapshot.element) {
                    val paths = element.path.split(".")
                    if (
                        element.hasMustSupport() || element.hasFixed() || (element.hasSliceName() && !paths[paths.size-1].equals("extension"))
                                || element.id.split(".").size == 1) {
                        val paths = element.id.split(".")
                        var title = ""


                        if (paths.size>1){
                            for (i in 2..paths.size) {
                                if (title.isNotEmpty()) title += "."
                                title += paths[i-1]
                            }
                            index += "\n- <a href=\"#"+title+"\">"+title+"</a>"
                            subDescription += "\n\n<a name=\""+title+"\"></a>\n ## "+title
                            subDescription += getElementDescription(element)
                        } else {
                            mainDescription += getElementDescription(element)
                        }



                    }
                }
            }
        }
        return mainDescription + "\n\n" + index + subDescription
    }



    private fun getPackageCanonicalUrl(profile : String, npmPackage: NpmPackage?) : String {
        val npm= npmPackage?.npm?.get("name").toString().replace("\"","") + "@" + npmPackage?.npm?.get("version").toString().replace("\"","")
        return "https://simplifier.net/resolve?target=simplifier&fhirVersion=R4&scope="+ npm + "&canonical="+ profile.replace("|","&#124;")
    }
    private fun getDocumentationPathNpm(profile : String, npmPackage : NpmPackage) : String {
        return getPackageCanonicalUrl(profile,npmPackage)
    }
    private fun getCanonicalUrl(resourceUrl : String ) :String {
        val npm = npmPackages?.get(npmPackages.size-1) // get last package
        return getPackageCanonicalUrl(resourceUrl, npm)
    }

}
