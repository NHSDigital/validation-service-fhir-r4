package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.Constants
import ca.uhn.fhir.util.HapiExtensions
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
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
import org.thymeleaf.templateresource.ClassLoaderTemplateResource
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import java.util.stream.Collectors

class OpenAPIParser(private val ctx: FhirContext?, private val npmPackages: List<NpmPackage>?) {

    val FHIR_JSON_RESOURCE = "FHIR-JSON-RESOURCE"
    val FHIR_XML_RESOURCE = "FHIR-XML-RESOURCE"
    val PAGE_SYSTEM = "System Level Operations"
    val PAGE_ALL = "All"
    val FHIR_CONTEXT_CANONICAL = FhirContext.forR4()
    val REQUEST_DETAILS = "REQUEST_DETAILS"
    val RACCOON_PNG = "raccoon.png"
    private var mySwaggerUiVersion = "3.0.0"
    private var cs: CapabilityStatement = CapabilityStatement()


    private val myResourcePathToClasspath: Map<String, String> = HashMap()
    private val myExtensionToContentType: Map<String, String> = HashMap()
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
            addFhirResourceRequestBody(openApi, transaction, ctx, null, "Bundle")
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
                    val documentation = getDocumentationPath(profile)
                    resourceTag.description = "See $documentation for documentation. Resource type: $resourceType"
            } else {
                resourceTag.description = "Resource type: $resourceType"
            }

            openApi.addTagsItem(resourceTag)


            for (resftfulIntraction in nextResource.interaction) {
                var requestExample = getRequestExample(resftfulIntraction)
                if (requestExample == null) requestExample = genericExampleSupplier(ctx, resourceType)

                when (resftfulIntraction.code) {
                    // Search
                    CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.description = "This is a search type"
                        operation.summary = "search-type: Search for $resourceType instances"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
                        }
                        addFhirResourceResponse(ctx, openApi, operation, resourceType,resftfulIntraction)
                        for (nextSearchParam in nextResource.searchParam) {
                            val parametersItem = Parameter()
                            operation.addParametersItem(parametersItem)
                            parametersItem.name = nextSearchParam.name
                            parametersItem.setIn("query")
                            parametersItem.description = nextSearchParam.documentation
                            parametersItem.style = Parameter.StyleEnum.SIMPLE
                        }
                    }
                    // Instance Read
                    CapabilityStatement.TypeRestfulInteraction.READ -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.GET)
                        operation.addTagsItem(resourceType)
                        operation.summary = "read-instance: Read $resourceType instance"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addFhirResourceResponse(ctx, openApi, operation,resourceType,resftfulIntraction)
                    }
                    // Instance Update
                    CapabilityStatement.TypeRestfulInteraction.UPDATE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PUT)
                        operation.addTagsItem(resourceType)
                        operation.summary =
                            "update-instance: Update an existing $resourceType instance, or create using a client-assigned ID"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addFhirResourceRequestBody(openApi, operation, ctx, requestExample, resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }
                    // Type Create
                    CapabilityStatement.TypeRestfulInteraction.CREATE -> {
                        val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.POST)
                        operation.addTagsItem(resourceType)
                        operation.summary = "create-type: Create a new $resourceType instance"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
                        }
                        addFhirResourceRequestBody(openApi, operation, ctx,requestExample, resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }
                    // Instance Patch
                    CapabilityStatement.TypeRestfulInteraction.PATCH -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PATCH)
                        operation.addTagsItem(resourceType)
                        operation.summary = "instance-patch: Patch a resource instance of type $resourceType by ID"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
                        }
                        addResourceIdParameter(operation)
                        addFhirResourceRequestBody(openApi, operation, FHIR_CONTEXT_CANONICAL, patchExampleSupplier(), resourceType)
                        addFhirResourceResponse(ctx, openApi, operation, "OperationOutcome",resftfulIntraction)
                    }

                        // Instance Delete
                        CapabilityStatement.TypeRestfulInteraction.DELETE -> {
                        val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.DELETE)
                        operation.addTagsItem(resourceType)
                        operation.summary = "instance-delete: Perform a logical delete on a resource instance"
                        if (resftfulIntraction.hasDocumentation()) {
                            operation.description = resftfulIntraction.documentation
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
                operation.summary = "vread-instance: Read $resourceType instance with specific version"
                addResourceIdParameter(operation)
                addResourceVersionIdParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null,null)
            }

            // Type history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                val operation = getPathItem(paths, "/$resourceType/_history", PathItem.HttpMethod.GET)
                operation.addTagsItem(resourceType)
                operation.summary =
                    "type-history: Fetch the resource change history for all resources of type $resourceType"
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
                addResourceIdParameter(operation)
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
            val schema = ObjectSchema()
            if (profile != null) {

                val documentation = getDocumentationPath(profile)
                schema.description = "See $documentation for the FHIR Profile on resource [$resourceType](https://www.hl7.org/fhir/$resourceType.html). HL7 FHIR R4 Schema can be found here [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html)"
            } else {
                schema.description = "See resource [$resourceType](https://www.hl7.org/fhir/$resourceType.html). HL7 FHIR R4 Schema can be found here [HL7 FHIR Downloads](https://www.hl7.org/fhir/downloads.html)"
            }
            openApi.components.addSchemas(resourceType, schema)
        }
    }

    private fun getDocumentationPath(profile : String) : String? {
        val uri = URI(profile)
        val path: String = uri.getPath()
        val idStr = path.substring(path.lastIndexOf('/') + 1)
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    StructureDefinition::class.java
                )) {
                    if (resource.url == profile) {
                        if (npmPackage.name().startsWith("uk.nhsdigital.medicines.r4"))
                            return "[$idStr](https://simplifier.net/guide/NHSDigital-Medicines/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md" + ")"
                        if (npmPackage.name().startsWith("uk.nhsdigital.r4"))
                            return "[$idStr](https://simplifier.net/guide/NHSDigital/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md" + ")"
                        if (npmPackage.name().startsWith(""))
                            return "[$idStr](https://simplifier.net/guide/HL7FHIRUKCoreR4Release1/Home/ProfilesandExtensions/Profile" + idStr + ")"
                    }
                }
            }
        }
        return "[$profile](https://simplifier.net/guide/nhsdigital/home)";
    }

    private fun getDocumentationPathNpm(profile : String, npmPackage : NpmPackage) : String? {
        val uri = URI(profile)
        val path: String = uri.getPath()
        val idStr = path.substring(path.lastIndexOf('/') + 1)

        if (npmPackage.name().startsWith("uk.nhsdigital.medicines.r4"))
            return "[$idStr](https://simplifier.net/guide/NHSDigital-Medicines/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md" + ")"
        if (npmPackage.name().startsWith("uk.nhsdigital.r4"))
            return "[$idStr](https://simplifier.net/guide/NHSDigital/Home/FHIRAssets/AllAssets/Profiles/" + idStr + ".guide.md" + ")"
        if (npmPackage.name().startsWith(""))
            return "[$idStr](https://simplifier.net/guide/HL7FHIRUKCoreR4Release1/Home/ProfilesandExtensions/Profile" + idStr + ")"

        return "[$profile](https://simplifier.net/guide/nhsdigital/home)";
    }

    private fun patchExampleSupplier(): Supplier<IBaseResource?>? {
        return Supplier {
            val example = Parameters()
            val operation = example
                .addParameter()
                .setName("operation")
            operation.addPart().setName("type").value = StringType("add")
            operation.addPart().setName("path").value = StringType("Patient")
            operation.addPart().setName("name").value = StringType("birthDate")
            operation.addPart().setName("value").value = DateType("1930-01-01")
            example
        }
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
        val definitionId = IdType(theOperation.definition)
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
        if (operationDefinition.get() == null) return
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
        addFhirResourceResponse(theFhirContext, theOpenApi, theOperation, null,null)
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
            val operationExample = getOperationRequestExample(theOperationComponent)
            if (operationExample != null && operationExample.get() !=null ) {
                exampleRequestBodyString = FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true)
                    .encodeResourceToString(operationExample.get())
            }
            theOperation.requestBody = RequestBody()
            theOperation.requestBody.content = Content()
            val mediaType = MediaType()
            mediaType.example = exampleRequestBodyString

            mediaType.schema = Schema<Any?>().type("object").title("FHIR Resource")
            theOperation.requestBody.content.addMediaType(Constants.CT_FHIR_JSON_NEW, mediaType)

        }
        if (theOperationDefinition.url.equals("http://hl7.org/fhir/OperationDefinition/MessageHeader-process-message")
            || theOperationDefinition.url.equals("https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message")) {
            var supportedDocumentation = ""

            for (messaging in cs.messaging) {
                if (messaging.hasDocumentation()) {
                    supportedDocumentation += messaging.documentation +" \n"
                }
                supportedDocumentation += "\n Supported Messages \n"
                for (supportedMessage in messaging.supportedMessage) {
                    if (supportedMessage.hasDefinition()) {
                        val uri = URI(supportedMessage.definition)
                        val path: String = uri.getPath()
                        val idStr = path.substring(path.lastIndexOf('/') + 1)
                        supportedDocumentation += "\n\n $idStr \n --------------------\n `"+ supportedMessage.definition+"` \n"
                        for (npmPackage in npmPackages!!) {
                            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                                    npmPackage,
                                    MessageDefinition::class.java
                                )) {
                                    if (resource.url == supportedMessage.definition) {
                                        if (resource.hasDescription()) {
                                            supportedDocumentation += " " + resource.description + "\n"
                                        }
                                        if (resource.hasEventCoding()) {
                                            supportedDocumentation += " \n\n MessageHeader.eventCoding = **" + resource.eventCoding.code + "** \n"
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
                                                    profile = getDocumentationPathNpm(profile,npmPackage)
                                                }
                                                supportedDocumentation += "| $resource | $profile | $min | $max | \n"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        supportedDocumentation += "\n"

                    }
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

    private fun addFhirResourceRequestBody(
        theOpenApi: OpenAPI,
        theOperation: Operation,
        theExampleFhirContext: FhirContext?,
        theExampleSupplier: Supplier<IBaseResource?>?,
        theResourceType: String?
    ) {
        val requestBody = RequestBody()
        requestBody.content = provideContentFhirResource(theOpenApi, theExampleFhirContext, theExampleSupplier,theResourceType)
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
                val example = ctx?.newJsonParser()?.parseResource("{ \"resourceType\" : \"" + theResourceType + "\" }")
                exampleResponse = Supplier {
                    var example: IBaseResource? = example
                    example
                }
                if (resftfulIntraction.code == CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE) {
                    val bundle = Bundle()
                    bundle.type = Bundle.BundleType.SEARCHSET
                    bundle.entry.add(Bundle.BundleEntryComponent().setResource(example as Resource?))
                    bundle.total = 0
                    exampleResponse = Supplier {
                        var example: IBaseResource? = bundle
                        example
                    }
                }
            }

            if (exampleResponse != null) response200.content = provideContentFhirResource(
                theOpenApi,
                theFhirContext,
                exampleResponse,
                theResourceType
            )
        }
        if (response200.content == null) {
            response200.content = provideContentFhirResource(
                theOpenApi,
                theFhirContext,
                genericExampleSupplier(theFhirContext, theResourceType),
                theResourceType
            )
        }

        theOperation.responses.addApiResponse("200", response200)
    }

    private fun getRequestExample(interaction : CapabilityStatement.ResourceInteractionComponent) : Supplier<IBaseResource?>? {
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")
            if (apiExtension.hasExtension("request")) {
                val request = apiExtension.getExtensionByUrl("request")
                if (request.hasExtension("resource") && request.hasExtension("id")) {
                    for (npmPackage in npmPackages!!) {
                        if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                            implementationGuideParser?.getResourcesFromPackage(npmPackage)
                                ?.forEach {
                                        if (it is Resource) {
                                            val resource: Resource = it
                                            //println(resource.resourceType.name + " - "+(request.getExtensionByUrl("resource").value as CodeType).value)
                                            if (resource.resourceType.name == (request.getExtensionByUrl("resource").value as CodeType).value ) {
                                               // println("Match "+ resource.idElement.idPart + " - "+ (request.getExtensionByUrl("id").value as StringType).value )
                                                if (resource.idElement.idPart == (request.getExtensionByUrl("id").value as StringType).value ) {
                                                 //   println("Matched")
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
                }
            }
        }
        return null
    }


    private fun getResponseExample(interaction : CapabilityStatement.ResourceInteractionComponent) : Supplier<IBaseResource?>? {
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")
            if (apiExtension.hasExtension("response")) {
                val request = apiExtension.getExtensionByUrl("response")
                if (request.hasExtension("resource") && request.hasExtension("id")) {
                    for (npmPackage in npmPackages!!) {
                        if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                            implementationGuideParser?.getResourcesFromPackage(npmPackage)
                                ?.forEach {
                                    if (it is Resource) {
                                        val resource: Resource = it
                                        //println(resource.resourceType.name + " - "+(request.getExtensionByUrl("resource").value as CodeType).value)
                                        if (resource.resourceType.name == (request.getExtensionByUrl("resource").value as CodeType).value ) {
                                            // println("Match "+ resource.idElement.idPart + " - "+ (request.getExtensionByUrl("id").value as StringType).value )
                                            if (resource.idElement.idPart == (request.getExtensionByUrl("id").value as StringType).value ) {
                                                //   println("Matched")
                                                if (interaction.code == CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE) {
                                                    val bundle = Bundle()
                                                    bundle.type = Bundle.BundleType.SEARCHSET
                                                    bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                                                    bundle.total = 1
                                                    return Supplier {
                                                        var example: IBaseResource? = bundle
                                                        example
                                                    }
                                                }
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
                }
            }
        }
        if (interaction.code == CapabilityStatement.TypeRestfulInteraction.CREATE
            || interaction.code == CapabilityStatement.TypeRestfulInteraction.UPDATE) {
            val operation = OperationOutcome()
            operation.issue.add(OperationOutcome.OperationOutcomeIssueComponent()
                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION))
            return Supplier {
                var example: IBaseResource? = operation
                example
            }
        }
        return null
    }
    private fun getOperationRequestExample(interaction: CapabilityStatement.CapabilityStatementRestResourceOperationComponent) : Supplier<IBaseResource?>? {
        if (interaction.hasExtension("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")) {
            val apiExtension =
                interaction.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition-Examples")
            if (apiExtension.hasExtension("request")) {
                val request = apiExtension.getExtensionByUrl("request")
                if (request.hasExtension("resource") && request.hasExtension("id")) {
                    for (npmPackage in npmPackages!!) {
                        if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                            implementationGuideParser?.getResourcesFromPackage(npmPackage)
                                ?.forEach {
                                    if (it is Resource) {
                                        val resource: Resource = it
                                        //println(resource.resourceType.name + " - "+(request.getExtensionByUrl("resource").value as CodeType).value)
                                        if (resource.resourceType.name == (request.getExtensionByUrl("resource").value as CodeType).value ) {
                                            //println("Match "+ resource.idElement.idPart + " - "+ (request.getExtensionByUrl("id").value as StringType).value )
                                            if (resource.idElement.idPart == (request.getExtensionByUrl("id").value as StringType).value ) {
                                                //   println("Matched")
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
                }
            }
        }
        return null
    }
    private fun genericExampleSupplier(
        theFhirContext: FhirContext?,
        theResourceType: String?
    ): Supplier<IBaseResource?>? {
        if (theResourceType == "CapabilityStatement") return Supplier {
            var example: IBaseResource? = this.cs
            example
        }
        return if (theResourceType == null) {
            null
        } else Supplier {
            var example: IBaseResource? = null
            if (theResourceType != null) {
                example = theFhirContext!!.getResourceDefinition(theResourceType).newInstance()
            }
            example
        }
    }


    private fun provideContentFhirResource(
        theOpenApi: OpenAPI,
        theExampleFhirContext: FhirContext?,
        theExampleSupplier: Supplier<IBaseResource?>?,
        resourceType: String?
    ): Content? {
        var resourceType2 = resourceType
        addSchemaFhirResource(theOpenApi)

        val retVal = Content()
        if (resourceType2 == null && theExampleSupplier?.get() != null)
            resourceType2 = theExampleSupplier?.get()?.fhirType()
        if (resourceType2 != null) addFhirResourceSchema(theOpenApi, resourceType2,null)
        val jsonSchema = MediaType().schema(
            ObjectSchema().`$ref`(
                "#/components/schemas/"+resourceType2
            )
        )
        if (theExampleSupplier != null) {
            jsonSchema.example = theExampleFhirContext!!.newJsonParser().setPrettyPrint(true)
                .encodeResourceToString(theExampleSupplier.get())
        }
        retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
        val xmlSchema = MediaType().schema(
            ObjectSchema().`$ref`(
                "#/components/schemas/"+resourceType2
            )
        )
        val schema = ObjectSchema().externalDocs
        if (theExampleSupplier != null) {
            xmlSchema.example = theExampleFhirContext!!.newXmlParser().setPrettyPrint(true)
                .encodeResourceToString(theExampleSupplier.get())
        }
        retVal.addMediaType(Constants.CT_FHIR_XML_NEW, xmlSchema)
        return retVal
    }

    private fun addResourceIdParameter(theOperation: Operation) {
        val parameter = Parameter()
        parameter.name = "id"
        parameter.setIn("path")
        parameter.description = "The resource ID"
        parameter.example = "123"
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
}
