package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
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
import org.thymeleaf.templateresource.ClassLoaderTemplateResource
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import java.util.stream.Collectors

class OpenAPIParser(private val ctx: FhirContext?,
                    private val npmPackages: List<NpmPackage>?,
                    private val searchParameters : Bundle) {


    val PAGE_SYSTEM = "System Level Operations"
    val FHIR_CONTEXT_CANONICAL = FhirContext.forR4()

    private var generateXML = false;
    private var cs: CapabilityStatement = CapabilityStatement()
    private val exampleServer = "http://example.org/"
    private val exampleServerPrefix = "FHIR/R4/"

    private val myResourcePathToClasspath: Map<String, String> = HashMap()

    private var myBannerImage: String? = null

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(ctx!!)

    fun removeTrailingSlash(theUrl: String?): String? {
        var theUrl = theUrl
        while (theUrl != null && theUrl.endsWith("/")) {
            theUrl = theUrl.substring(0, theUrl.length - 1)
        }
        return theUrl
    }


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

        if (cs.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition")) {
            val apiDefinition = cs.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition")
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
            if (apiDefinition.hasExtension("implementationGuide")) {
                var igDescription = "\n\n | FHIR Implementation Guide | Version |\n |-----|-----|\n"
                apiDefinition.extension.forEach{
                    if (it.url.equals("implementationGuide")) {
                        val name = it.getExtensionByUrl("name").value as StringType
                        var url = "https://simplifier.net/guide/NHSDigital/Home"
                        var version = ""
                        if (it.hasExtension("version")) {
                            version = (it.getExtensionByUrl("version").value as StringType).value
                        }
                        if (name.value.startsWith("uk.nhsdigital.medicines")) url = "https://simplifier.net/guide/nhsdigital-medicines/home"
                        if (name.value.startsWith("ukcore.")) url = "https://simplifier.net/guide/hl7fhirukcorer4release1/home"
                        igDescription += " |[$name]($url)|$version|\n"

                    }
                }
                openApi.info.description += igDescription
            }

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
        addFhirResourceResponse(this.ctx, openApi, capabilitiesOperation, "CapabilityStatement",null)
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
            addFhirResourceResponse(ctx, openApi, transaction, null, null)
            addFhirResourceRequestBody(openApi, transaction, emptyList(), "Bundle")
        }

        // System History Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.HISTORYSYSTEM)) {
            val systemHistory = getPathItem(paths, "/_history", PathItem.HttpMethod.GET)
            systemHistory.addTagsItem(PAGE_SYSTEM)
            systemHistory.summary =
                "server-history: Fetch the resource change history across all resource types on the server"
            addFhirResourceResponse(ctx, openApi, systemHistory, null, null)
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
            val resourceTag = Tag()
            resourceTag.name = resourceType

            addFhirResourceSchema(openApi, resourceType, nextResource.profile)

            if (nextResource.hasProfile()) {
                    val profile=nextResource.profile
                    val idStr = getProfileName(profile)
                    val documentation = getDocumentationPath(profile)
                    resourceTag.description = "Profile : [$idStr]($documentation) (additional rules to the resource) \n Resource type: [$resourceType](https://www.hl7.org/fhir/$resourceType.html) (also contains links to code generations schemas)"
                   // resourceTag.externalDocs = ExternalDocumentation()
                   // resourceTag.externalDocs.url = "Profile: [$idStr]($documentation)"

            } else {
                resourceTag.description = "Resource type: $resourceType"
            }

            if (nextResource.hasInteraction()) openApi.addTagsItem(resourceTag)


            for (resftfulIntraction in nextResource.interaction) {
                var requestExample = getRequestExample(resftfulIntraction)
                if (requestExample == null) requestExample = genericExampleSupplier(ctx, resourceType)

                when (resftfulIntraction.code) {
                    // Search
                    CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.description = "See also [FHIR Search](http://www.hl7.org/fhir/search.html).\n\n"
                        operation.summary = "search-type: Search for $resourceType instances."
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        if (nextResource.hasExtension("http://hl7.org/fhir/StructureDefinition/capabilitystatement-search-parameter-combination")) {
                            var comboDoc = "\n\n **Required Parameters** \n\n One of the following paramters(s) is **required** \n\n" +
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
                        addFhirResourceResponse(ctx, openApi, operation, resourceType,resftfulIntraction)
                        for (nextSearchParam in nextResource.searchParam) {
                            val parametersItem = Parameter()
                            operation.addParametersItem(parametersItem)
                            parametersItem.name = nextSearchParam.name
                            parametersItem.setIn("query")
                            parametersItem.description = nextSearchParam.documentation
                            parametersItem.description += getSearchParameterDocumentation(nextSearchParam,resourceType, parametersItem)

                            parametersItem.style = Parameter.StyleEnum.SIMPLE

                            if (nextSearchParam.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
                                val extension = nextSearchParam.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")
                                if (extension.hasExtension("required"))
                                    parametersItem.required = ((extension.getExtensionByUrl("required").value as BooleanType).value)
                            }
                        }
                        addResourceAPIMParameter(operation)
                    }
                    // Instance Read
                    CapabilityStatement.TypeRestfulInteraction.READ -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.summary = "read-instance: Read $resourceType instance."
                        operation.description = "See also [FHIR read](http://www.hl7.org/fhir/http.html#read)\n\n"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation,resourceType,resftfulIntraction)
                    }
                    // Instance Update
                    CapabilityStatement.TypeRestfulInteraction.UPDATE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PUT)
                        operation.addTagsItem(resourceType)
                        operation.summary =
                            "update-instance: Update an existing $resourceType instance, or create using a client-assigned ID."
                        operation.description = "See also [FHIR update](http://www.hl7.org/fhir/http.html#update)\n\n"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addFhirResourceRequestBody(openApi, operation,  requestExample, resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }
                    // Type Create
                    CapabilityStatement.TypeRestfulInteraction.CREATE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.POST)
                        operation.addTagsItem(resourceType)
                        operation.summary = "create-type: Create a new $resourceType instance."
                        operation.description = "See also [FHIR create](http://www.hl7.org/fhir/http.html#create)\n\n"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        addResourceAPIMParameter(operation)
                        addFhirResourceRequestBody(openApi, operation, requestExample, resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }
                    // Instance Patch
                    CapabilityStatement.TypeRestfulInteraction.PATCH -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PATCH)
                        operation.addTagsItem(resourceType)
                        operation.summary = "instance-patch: Patch a resource instance of type $resourceType by ID."
                        operation.description = "See also [FHIR patch](http://www.hl7.org/fhir/http.html#patch)\n\n"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addResourceAPIMParameter(operation)
                        addFhirResourceSchema(openApi,"JSONPATCH",null)
                        addPatchResourceRequestBody(openApi, operation, FHIR_CONTEXT_CANONICAL, patchExampleSupplier(resourceType), resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }

                        // Instance Delete
                        CapabilityStatement.TypeRestfulInteraction.DELETE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.DELETE)
                        operation.addTagsItem(resourceType)
                        operation.summary = "instance-delete: Perform a logical delete on a resource instance."
                            operation.description = "See also [FHIR delete](http://www.hl7.org/fhir/http.html#delete)\n\n"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description += resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }

                }
            }


            // Instance VRead
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.VREAD)) {
                val operation = getPathItem(
                    paths,
                    "/$resourceType/{id}/_history/{version_id}", PathItem.HttpMethod.GET
                )
                operation.addTagsItem(resourceType)
                operation.summary = "vread-instance: Read $resourceType instance with specific version."
                operation.description = "See also [FHIR vread](http://www.hl7.org/fhir/http.html#vread)\n\n"
                addResourceIdParameter(operation)
                addResourceVersionIdParameter(operation)
                addResourceAPIMParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null,null)
            }

            // Type history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                val operation = getPathItem(paths, "/$resourceType/_history", PathItem.HttpMethod.GET)
                operation.addTagsItem(resourceType)
                operation.summary =
                    "type-history: Fetch the resource change history for all resources of type $resourceType."
                operation.description = "See also [FHIR history](http://www.hl7.org/fhir/http.html#history)\n\n"
                addResourceAPIMParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null,null)
            }

            // Instance history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                val operation = getPathItem(
                    paths,
                    "/$resourceType/{id}/_history", PathItem.HttpMethod.GET
                )
                operation.addTagsItem(resourceType)
                operation.summary =
                    "instance-history: Fetch the resource change history for all resources of type $resourceType"
                operation.description = "See also [FHIR history](http://www.hl7.org/fhir/http.html#history)\n\n"
                addResourceIdParameter(operation)
                addResourceAPIMParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null,null)
            }


            // Resource-level Operations
            for (nextOperation in nextResource.operation) {
                addFhirOperation(ctx, openApi, paths, resourceType, nextOperation)
            }
        }
        return openApi
    }
    private fun addFhirResourceSchema(openApi: OpenAPI, resourceType: String?, profile: String?) {
        // Add schema
        if (!openApi.components.schemas.containsKey(resourceType)) {
            if (resourceType == "JSONPATCH") {
                val schema = ObjectSchema()
                schema.description = "See [JSON Patch](http://jsonpatch.com/)"
                openApi.components.addSchemas(resourceType, schema)
                return
            }
            val schema = ObjectSchema()
            if (profile != null) {
                val idStr = getProfileName(profile)
                val documentation = getDocumentationPath(profile)
                schema.description = "See [$idStr]($documentation) for the FHIR Profile on resource [$resourceType](https://www.hl7.org/fhir/$resourceType.html). For HL7 FHIR R4 Schema see [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html)"

            } else {
                schema.description = "See [HL7 FHIR $resourceType](https://www.hl7.org/fhir/$resourceType.html). For HL7 FHIR R4 Schema see [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html)"
            }
            // This doesn't appear to be used. Consider removing
            schema.externalDocs = ExternalDocumentation()
            schema.externalDocs.description = resourceType
            schema.externalDocs.url = "https://www.hl7.org/fhir/$resourceType.html"
            openApi.components.addSchemas(resourceType, schema)
        }
    }

    private fun getDocumentationPath(profile : String) : String? {
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    StructureDefinition::class.java
                )) {
                    if (resource.url == profile) {
                        return getDocumentationPathNpm(profile,npmPackage)
                    }
                }
            }
        }
        return "https://simplifier.net/guide/nhsdigital/home";
    }

    private fun getProfileUrl(idStr : String, packageName : String) : String {

        if (packageName.startsWith("uk.nhsdigital.medicines.r4"))
            return "https://simplifier.net/guide/NHSDigital-Medicines/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md"
        if (packageName.startsWith("uk.nhsdigital.r4"))
            return "https://simplifier.net/guide/NHSDigital/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md"
        if (packageName.contains("ukcore"))
            return "https://simplifier.net/guide/HL7FHIRUKCoreR4Release1/Home/ProfilesandExtensions/Profile" + idStr
        return "https://simplifier.net/guide/nhsdigital/home";
    }
    private fun getProfileName(profile : String) : String {
        val uri = URI(profile)
        val path: String = uri.getPath()
        return path.substring(path.lastIndexOf('/') + 1)
    }

    private fun getDocumentationPathNpm(profile : String, npmPackage : NpmPackage) : String? {
        val idStr = getProfileName(profile)
        val profileUrl = getProfileUrl(idStr,npmPackage.name())
        return profileUrl;
    }

    private fun patchExampleSupplier(resourceType: String?): String? {

        if (resourceType.equals("MedicationDispense")) {
            val patch1 = JSONObject()
                .put("op", "replace")
                .put("path", "/status")
                .put("value", "in-progress")

            val patch2 = JSONObject()
                .put("op", "add")
                .put("path", "/whenPrepared")
                .put("value", JSONArray().put("2022-02-08T00:00:00+00:00"))

            val jsonString: String = JSONObject()
                .put("patches", JSONArray().put(patch1).put(patch2))
                .toString()

            return jsonString

        }
        if (resourceType.equals("MedicationRequest")) {
            val patch1 = JSONObject()
                .put("op", "replace")
                .put("path", "/status")
                .put("value", "cancelled")

            val jsonString: String = JSONObject()
                .put("patches", JSONArray().put(patch1))
                .toString()

            return jsonString

        }
        val patch1 = JSONObject()
            .put("op", "add")
            .put("path", "/foo")
            .put("value", JSONArray().put("bar"))

        val patch2 = JSONObject()
            .put("op", "add")
            .put("path", "/foo2")
            .put("value", JSONArray().put("barbar"))

        val jsonString: String = JSONObject()
            .put("patches", JSONArray().put(patch1).put(patch2))
            .toString()

        return jsonString
    }


    private fun addSchemaFhirResource(theOpenApi: OpenAPI) {
        ensureComponentsSchemasPopulated(theOpenApi)
        /*
        if (!theOpenApi.components.schemas.containsKey(FHIR_JSON_RESOURCE)) {
            val fhirJsonSchema = ObjectSchema()
            fhirJsonSchema.description = "This API is based on HL7 FHIR R4 Schema. See [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html) for details. Note: the FHIR schema needs to be used in conjunction with FHIR profiles, which define additional rules and constrainst on top of the core resource schemas. These profiles are found in FHIR Implementation Guides."
            theOpenApi.components.addSchemas(FHIR_JSON_RESOURCE, fhirJsonSchema)
        }
        if (!theOpenApi.components.schemas.containsKey(FHIR_XML_RESOURCE)) {
            val fhirXmlSchema = ObjectSchema()
            fhirXmlSchema.description = "This API is based on HL7 FHIR R4 Schema. See [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html) for details. Note: the FHIR schema needs to be used in conjunction with FHIR profiles, which define additional rules and constrainst on top of the core resource schemas. These profiles are found in FHIR Implementation Guides."

            theOpenApi.components.addSchemas(FHIR_XML_RESOURCE, fhirXmlSchema)
        }

         */
    }

    private fun ensureComponentsSchemasPopulated(theOpenApi: OpenAPI) {
        if (theOpenApi.components == null) {
            theOpenApi.components = Components()
        }
        if (theOpenApi.components.schemas == null) {
            theOpenApi.components.schemas = LinkedHashMap()
        }
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
        for (npmPackage in npmPackages!!) {
            for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                npmPackage,
                OperationDefinition::class.java
            )) {

                if (resource.url == theOperation.definition) {
                    operationDefinition.set(resource)
                    break
                }
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
        theOpenApi: OpenAPI,
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
        if (theOperationComponent.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
            //
            val exampleOperation = getOperationExample(false,theOperationComponent)
            if (exampleOperation != null && exampleOperation.get() !=null) {
                theOperation.responses = ApiResponses()
                val response200 = ApiResponse()
                response200.description = "Success"
                val exampleList = mutableListOf<Example>()
                exampleList.add(Example().value(ctx?.newJsonParser()?.encodeResourceToString(exampleOperation.get())))
                response200.content = provideContentFhirResource(
                    theOpenApi,
                    exampleList,
                    (exampleOperation.get())?.fhirType()
                )
                theOperation.responses.addApiResponse("200",response200)
            } else {
                addFhirResourceResponse(theFhirContext, theOpenApi, theOperation, "Parameters", null)
            }

            //theOperation.requestBody.content = provideContentFhirResource(theOpenApi,ctx,exampleOperation, null)
        } else {
            addFhirResourceResponse(theFhirContext, theOpenApi, theOperation, "Parameters", null)
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
            for (nextSearchParam in theOperationDefinition.parameter) {
                if (nextSearchParam.use != OperationDefinition.OperationParameterUse.OUT) {
                    val param = exampleRequestBody.addParameter()
                    param.name = nextSearchParam.name
                    val paramType = nextSearchParam.type
                    when (StringUtils.defaultString(paramType)) {
                        "uri", "url", "code", "string" -> {
                            val type =
                                FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
                            type.valueAsString = "example"
                            param.value = type as Type
                        }
                        "integer" -> {
                            val type =
                                FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
                            type.valueAsString = "0"
                            param.value = type as Type
                        }
                        "boolean" -> {
                            val type =
                                FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType)!!.newInstance() as IPrimitiveType<*>
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
                            val resource = FHIR_CONTEXT_CANONICAL.getResourceDefinition(theResourceType).newInstance()
                            resource.setId("1")
                            param.resource = resource as Resource
                        }
                    }
                }
            }
            var exampleRequestBodyString =
                FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true)
                    .encodeResourceToString(exampleRequestBody)
            val operationExample = getOperationExample(true,theOperationComponent)
            if (operationExample != null && operationExample.get() !=null ) {
                exampleRequestBodyString = FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true)
                    .encodeResourceToString(operationExample.get())
            }
            theOperation.requestBody = RequestBody()
            theOperation.requestBody.content = Content()

            if (!theOperationDefinition.url.equals("http://hl7.org/fhir/OperationDefinition/MessageHeader-process-message")
                && !theOperationDefinition.url.equals("https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message")) {
                if (theOperationComponent.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
                    //
                    val exampleOperation = getOperationExample(true,theOperationComponent)
                    if (exampleOperation != null && exampleOperation.get() !=null) exampleRequestBodyString = ctx?.newJsonParser()?.encodeResourceToString(exampleOperation.get())
                    //theOperation.requestBody.content = provideContentFhirResource(theOpenApi,ctx,exampleOperation, null)
                }
                mediaType.example = exampleRequestBodyString
            } else {
                mediaType.examples = mutableMapOf<String,Example>()
            }
            // TODO add in correct schema
            mediaType.schema = Schema<Any?>().type("object").title("Bundle")

            theOperation.requestBody.content.addMediaType(Constants.CT_FHIR_JSON_NEW, mediaType)

        }
        if (theOperationDefinition.hasParameter()) {
            var inDoc = "\n\n ## Parameters (In) \n\n |Name | Cardinality | Type | Documentation |\n |-------|-----------|-------------|------------|"
            var outDoc = "\n\n ## Parameters (Out) \n\n |Name | Cardinality | Type | Documentation |\n |-------|-----------|-------------|------------|"
            for (parameter in theOperationDefinition.parameter) {
                var entry = "\n |"+parameter.name + "|" + parameter.min + ".." + parameter.max + "|"
                if (parameter.hasType()) entry += parameter.type + "|"
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
                    mediaType.examples.put(idStr, getMessageExample(supportedMessage))
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
        return when (theMethod) {
            PathItem.HttpMethod.POST -> {
                assert(pathItem!!.post == null) { "Have duplicate POST at path: $thePath" }
                pathItem.post(Operation()).post
            }
            PathItem.HttpMethod.GET -> {
                assert(pathItem!!.get == null) { "Have duplicate GET at path: $thePath" }
                pathItem[Operation()].get
            }
            PathItem.HttpMethod.PUT -> {
                assert(pathItem!!.put == null)
                pathItem.put(Operation()).put
            }
            PathItem.HttpMethod.PATCH -> {
                assert(pathItem!!.patch == null)
                pathItem.patch(Operation()).patch
            }
            PathItem.HttpMethod.DELETE -> {
                assert(pathItem!!.delete == null)
                pathItem.delete(Operation()).delete
            }
            PathItem.HttpMethod.HEAD, PathItem.HttpMethod.OPTIONS, PathItem.HttpMethod.TRACE -> throw IllegalStateException()
            else -> throw IllegalStateException()
        }
    }


    private fun addPatchResourceRequestBody(
        theOpenApi: OpenAPI,
        theOperation: Operation,
        theExampleFhirContext: FhirContext?,
        theExampleSupplier: String?,
        theResourceType: String?
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
        theResourceType: String?
    ) {
        val requestBody = RequestBody()
        requestBody.content = provideContentFhirResource(theOpenApi, theExampleSupplier,theResourceType)
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
        resftfulIntraction : CapabilityStatement.ResourceInteractionComponent?
    ) {
        theOperation.responses = ApiResponses()
        val response200 = ApiResponse()
        response200.description = "Success"
        if (resftfulIntraction != null) {
            var exampleResponse = getResponseExample(resftfulIntraction)

            if (exampleResponse == null && theResourceType != null) {
                val example = Example()
                example.value = ctx?.newJsonParser()?.parseResource("{ \"resourceType\" : \"" + theResourceType + "\" }")
                exampleResponse = mutableListOf<Example>()
                exampleResponse.add(example)
            }

            if (resftfulIntraction == null && theResourceType!=null && theResourceType == "CapabilityStatement") {
                val example = Example()
                example.value = ctx?.newJsonParser()?.encodeResourceToString(cs)
                exampleResponse = mutableListOf<Example>()
                exampleResponse.add(example)
            }

            if (exampleResponse != null) {
                response200.content = provideContentFhirResource(
                    theOpenApi,
                    exampleResponse,
                    theResourceType
                )
            }
        }
        if (response200.content == null) {

            response200.content = provideContentFhirResource(
                theOpenApi,
                genericExampleSupplier(theFhirContext, theResourceType),
                theResourceType
            )
        }

        theOperation.responses.addApiResponse("200", response200)
    }

    private fun getMessageExample(supportedMessage : CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent) : Example? {

        var supportedDocumentation = ""
        var example : Example = Example()
        if (supportedMessage.hasDefinition()) {
            supportedDocumentation += " \n\n MessageDefinition.url = **"+ supportedMessage.definition+"** \n"
            for (npmPackage in npmPackages!!) {
                if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                    for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                        npmPackage,
                        MessageDefinition::class.java
                    )) {
                        if (resource.url == supportedMessage.definition) {
                            if (resource.hasDescription()) {
                                example.summary = resource.description
                            }
                            if (resource.hasPurpose()) {
                                supportedDocumentation += "\n ### Purpose" + resource.purpose
                            }
                            if (resource.hasEventCoding()) {
                                supportedDocumentation += " \n\n MessageDefinition.eventCoding = **" + resource.eventCoding.code + "** \n"
                            }
                            if (resource.hasFocus()) {
                                supportedDocumentation += "\n\n | Resource | Profile | Min | Max | \n"
                                supportedDocumentation += "|----------|---------|-----|-----| \n"
                                for (foci in resource.focus) {
                                    var min = foci.min
                                    var max = foci.max
                                    var resource = foci.code
                                    var profile = foci.profile
                                    if (profile==null) { profile="" }
                                    else {
                                        profile = getDocumentationPath(profile)
                                    }
                                    var idStr = "Not specified"
                                    idStr = getProfileName(foci.profile)
                                    supportedDocumentation += "| [$resource](https://www.hl7.org/fhir/$resource.html) | [$idStr]($profile) | $min | $max | \n"
                                }
                            }
                        }
                    }
                }
            }
            supportedDocumentation += "\n"
        }

        if (supportedMessage.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
            val apiExtension =
                supportedMessage.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")
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
                                ///println(resource.resourceType.name + " - "+pathParts.get(0))
                                if (resource.resourceType.name == pathParts.get(0)) {
                                    //println("Match "+ resource.idElement.idPart + " - resource.id=" + resource.id + " - "+ path + " - pathParts.get(1)="+pathParts.get(1))
                                    if (resource.id !=null && (resource.idElement.idPart.equals(pathParts.get(1)) || resource.id.equals(path))) {
                                        //println("*** Matched")
                                        if (create) resource.id = null
                                        return Supplier {
                                            var example: IBaseResource? = resource
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
        var examples = mutableListOf<Example>()
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")
            if (apiExtension.hasExtension("example")) {
                for (exampleExt in apiExtension.getExtensionsByUrl("example")) {
                    var supplierExample = getExampleFromPackages(true, exampleExt, (interaction.code == CapabilityStatement.TypeRestfulInteraction.CREATE))?.get()
                    var exampleOAS = Example()
                    examples.add(exampleOAS)
                    if (supplierExample != null && supplierExample !=null) {
                        var example = supplierExample
                        exampleOAS.value = ctx?.newJsonParser()?.encodeResourceToString(example)
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


    private fun getResponseExample(interaction : CapabilityStatement.ResourceInteractionComponent) : List<Example> {

        var examples = mutableListOf<Example>()

        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
                val apiExtension = interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")

                if (apiExtension.hasExtension("example")) {

                    for (exampleExt in apiExtension.getExtensionsByUrl("example")) {
                        var exampleOAS = Example()
                        examples.add(exampleOAS)
                        var supplierExample = getExampleFromPackages(false, exampleExt,false)

                        if (supplierExample != null && supplierExample.get() !=null) {
                            var example = supplierExample.get()
                            if (interaction.code == CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE) {
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
                                        .setUrl(exampleServer + exampleServerPrefix + (example as Resource)?.resourceType + "?parameterExample=123&page=2")
                                )
                                bundle.entry.add(
                                    Bundle.BundleEntryComponent().setResource(example as Resource)
                                        .setFullUrl(exampleServer + exampleServerPrefix + (example as Resource).resourceType + "/" + (supplierExample.get() as Resource).id)
                                )
                                bundle.total = 1
                                example = bundle
                            }

                            if (interaction.code == CapabilityStatement.TypeRestfulInteraction.CREATE
                                || interaction.code == CapabilityStatement.TypeRestfulInteraction.UPDATE
                            ) {
                                val operation = OperationOutcome()
                                operation.issue.add(
                                    OperationOutcome.OperationOutcomeIssueComponent()
                                        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                        .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                )
                            }
                            if (exampleExt.hasExtension("summary")) {
                                exampleOAS.summary = (exampleExt.getExtensionString("summary") as MarkdownType).value
                            }
                            if (exampleExt.hasExtension("description")) {
                                exampleOAS.description = (exampleExt.getExtensionString("description") as MarkdownType).value
                            }
                            exampleOAS.value = ctx?.newJsonParser()?.encodeResourceToString(example)

                    }
                }
            }

        }
        return examples
    }
    private fun getOperationExample(request: Boolean,interaction: CapabilityStatement.CapabilityStatementRestResourceOperationComponent) : Supplier<IBaseResource?>? {
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-OAS")
            for (extension in apiExtension.getExtensionsByUrl("example")) {
                 return getExampleFromPackages(true, extension,false)
            }
        }
        return null
    }

    private fun genericExampleSupplier(
        theFhirContext: FhirContext?,
        theResourceType: String?
    ): List<Example> {
        val exampleList = mutableListOf<Example>()
        var example = Example()
        exampleList.add(example)
        if (theResourceType == "CapabilityStatement") {
            example.value = ctx?.newJsonParser()?.encodeResourceToString(this.cs)
        } else {
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
        resourceType: String?
    ): Content? {
        val retVal = Content()
        var resourceType2 = resourceType
        addSchemaFhirResource(theOpenApi)

        if (examples.size == 1) {
            if (examples.get(0).value == null) {
                val generic = genericExampleSupplier(ctx,resourceType)
                examples.get(0).value = generic.get(0).value
            }
            if (examples.get(0).value is List<*>) {
                val generic = genericExampleSupplier(ctx,resourceType)
                examples.get(0).value = generic.get(0).value
            }

            val theExampleSupplier = ctx?.newJsonParser()?.parseResource(examples.get(0).value as String)

            if (resourceType2 == null && theExampleSupplier != null)
                resourceType2 = theExampleSupplier?.fhirType()
            if (resourceType2 != null) addFhirResourceSchema(theOpenApi, resourceType2, null)
            val jsonSchema = MediaType().schema(
                ObjectSchema().`$ref`(
                    "#/components/schemas/" + resourceType2
                )
            )
            if (theExampleSupplier != null) {
                jsonSchema.example = examples.get(0).value
            }
            retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
            val xmlSchema = MediaType().schema(
                ObjectSchema().`$ref`(
                    "#/components/schemas/" + resourceType2
                )
            )

            if (theExampleSupplier != null) {
                xmlSchema.example = examples.get(0).value
            }
            if (generateXML) retVal.addMediaType(Constants.CT_FHIR_XML_NEW, xmlSchema)
        } else {
            val jsonSchema = MediaType().schema(
                ObjectSchema().`$ref`(
                    "#/components/schemas/" + resourceType2
                )
            )
            retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
            jsonSchema.examples = mutableMapOf<String,Example>()
            for (example in examples) {
                var key = example.summary
                if (example.value == null) {
                    val generic = genericExampleSupplier(ctx,resourceType)
                    example.value = generic.get(0).value
                }
                if (key == null) key = "example"
                   jsonSchema.examples.put(key,example)
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
            parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
            parameter.style = Parameter.StyleEnum.SIMPLE
            theOperation.addParametersItem(parameter)

            parameter = Parameter()
        }

        parameter.name = "NHSD-Session-URID"
        parameter.setIn("header")
        parameter.description = "The user role ID (URID) for the current session. Also known as a user role profile ID (URPID)."
        parameter.example = "555254240100"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
        parameter.style = Parameter.StyleEnum.SIMPLE
        theOperation.addParametersItem(parameter)

        parameter = Parameter()
        parameter.name = "X-Request-ID"
        parameter.required = true
        parameter.setIn("header")
        parameter.description = "A globally unique identifier (GUID) for the request, which we use to de-duplicate repeated requests and to trace the request if you contact our helpdesk"
        parameter.example = "60E0B220-8136-4CA5-AE46-1D97EF59D068"
        parameter.schema = Schema<Any?>().type("string").minimum(BigDecimal(1))
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
    }

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

    fun getSearchParameter(url : String) : SearchParameter? {
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
    private fun getSearchParameterDocumentation(nextSearchParam: CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent,
                                                resourceType: String,
                                                parameter: Parameter) : String
    {
        var searchParameter : SearchParameter?


        val parameters = nextSearchParam.name.split(".")

        val modifiers = parameters.get(0).split(":")

        var name = modifiers.get(0)

        if (nextSearchParam.hasType()) {
            when (nextSearchParam.type) {
                Enumerations.SearchParamType.TOKEN -> {
                    parameter.schema = StringSchema()
                    parameter.schema.example = "[system][code]"
                }
                Enumerations.SearchParamType.REFERENCE -> {
                    parameter.schema = StringSchema()
                    parameter.schema.example = "[type]/[id] or [id] or [uri]"
                }
                Enumerations.SearchParamType.DATE -> {
                    parameter.schema = StringSchema()
                    parameter.description = "See FHIR documentation for more details."
                    parameter.schema.example = "eq2013-01-14"
                }
                Enumerations.SearchParamType.STRING -> {
                    parameter.schema = StringSchema()
                    parameter.schema.example = "LS15"
                }
            }
        }

        if (!nextSearchParam.hasDefinition()) {
            searchParameter = getSearchParameter("http://hl7.org/fhir/SearchParameter/$resourceType-"+ name)
            if (searchParameter == null) searchParameter = getSearchParameter("http://hl7.org/fhir/SearchParameter/individual-"+ name)
            if (searchParameter == null) {
                searchParameter = getSearchParameter("http://hl7.org/fhir/SearchParameter/clinical-"+ name)
                if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                    searchParameter = null
                }
            }
            if (searchParameter == null) {
                searchParameter = getSearchParameter("http://hl7.org/fhir/SearchParameter/conformance-"+ name)
                if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                    searchParameter = null
                }
            }
            if (searchParameter == null) {
                searchParameter = getSearchParameter("http://hl7.org/fhir/SearchParameter/medications-"+ name)
                if (searchParameter != null && !searchParameter.expression.contains(resourceType)) {
                    searchParameter = null
                }
            }
        } else searchParameter = getSearchParameter(nextSearchParam.definition)

        if (searchParameter == null && name.startsWith("_")) {
            searchParameter = SearchParameter()
            searchParameter?.code = name.replace("_","")
            searchParameter?.description = "Special search parameter, see [FHIR Search](http://www.hl7.org/fhir/search.html)"
            searchParameter?.expression = ""
            searchParameter?.type = Enumerations.SearchParamType.SPECIAL
        }

        var code : String? = searchParameter?.code
        var expression : String = searchParameter?.expression.toString()
        if (expression.split("|").size>1) {
            val exps = expression.split("|")
            for (exp in exps) {
                if (exp.replace(" ","").startsWith(resourceType)) {
                    expression = exp
                }
            }
        }
        var type = searchParameter?.type?.display
        var description = ""
        if (searchParameter?.description != null) {
            var desc = searchParameter?.description
            if (desc.split("*").size>1) {
                val exps = desc.split("*")
                for (exp in exps) {
                    if (exp.replace(" [","").startsWith(resourceType)) {
                        desc = exp
                    }
                }
            }
            description += "\n\n "+desc
        }

        if (modifiers.size>1 && searchParameter != null) {
            val modifier = modifiers.get(1)
            code += ":" + modifier
            name += ":" + modifier
            if (modifier == "identifier") {
                code += ":" + modifier
                type = "token"
                expression += ".identifier | "+ expression +".where(resolve() is Resource).identifier"
            }
        }

        if (searchParameter != null) {
            description = escapeMarkdown(description, false)
            expression = expression.replace("|","&#124;")
        }

        if (parameters.size>1) {
            description += "\n\n Chained search parameter. Please see [chained](http://www.hl7.org/fhir/search.html#chaining)"
            if (searchParameter == null) {
                description += "\n\n Caution: **$name** does not appear to be a valid search parameter. **Please check HL7 FHIR conformance.**"
            } else {
                description += "\n\n | Name |  Expression | \n |--------|--------| \n | $name |  $expression | \n"
            }
        } else {
            if (searchParameter != null) {
                description += "\n\n | Type |  Expression | \n |--------|--------| \n | [" + type?.lowercase() + " ](https://www.hl7.org/fhir/search.html#" + type?.lowercase() + ")|  $expression | \n"
            } else {
                description += "\n\n Caution: This does not appear to be a valid search parameter. **Please check HL7 FHIR conformance.**"
            }
        }
        if (parameters.size>1) {
            if (searchParameter?.type != Enumerations.SearchParamType.REFERENCE) {
                description += "\n\n Caution: This does not appear to be a valid search parameter. Chained search paramters **MUST** always be on reference types Please check Hl7 FHIR conformance."
            } else {
                val secondNames= parameters.get(1).split(":")
                var resourceType: String?
                if (secondNames.size>1) resourceType = secondNames.get(1) else resourceType = "Resource"
                var newSearchParam = CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                newSearchParam.name = secondNames.get(0)
                // Add back in remaining chained parameters
                for (i in 3..parameters.size) {
                    newSearchParam.name += "."+parameters.get(i)
                }
                description += getSearchParameterDocumentation(newSearchParam,resourceType, parameter)
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

}
