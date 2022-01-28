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


    fun generateOpenApi(cs: CapabilityStatement): OpenAPI? {
        val openApi = OpenAPI()
        openApi.info = Info()
        openApi.info.description = cs.description
        openApi.info.title = cs.software.name
        openApi.info.version = cs.software.version
        openApi.info.contact = Contact()
        openApi.info.contact.name = cs.contactFirstRep.name
        openApi.info.contact.email = cs.contactFirstRep.telecomFirstRep.value
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
        addFhirResourceResponse(this.ctx, openApi, capabilitiesOperation, "CapabilityStatement")
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
            addFhirResourceResponse(ctx, openApi, transaction, null)
            addFhirResourceRequestBody(openApi, transaction, ctx, null)
        }

        // System History Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.HISTORYSYSTEM)) {
            val systemHistory = getPathItem(paths, "/_history", PathItem.HttpMethod.GET)
            systemHistory.addTagsItem(PAGE_SYSTEM)
            systemHistory.summary =
                "server-history: Fetch the resource change history across all resource types on the server"
            addFhirResourceResponse(ctx, openApi, systemHistory, null)
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
            if (nextResource.hasProfile()) {
                    val profile=nextResource.profile
                    val documentation = getDocumentationPath(profile)
                    resourceTag.description = "See $documentation for documentation. Resource type: $resourceType"
            } else {
                resourceTag.description = "Resource type: $resourceType"
            }

            openApi.addTagsItem(resourceTag)

            // Instance Read
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.READ)) {
                val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.GET)
                operation.addTagsItem(resourceType)
                operation.summary = "read-instance: Read $resourceType instance"
                addResourceIdParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null)
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
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Type Create
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.CREATE)) {
                val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.POST)
                operation.addTagsItem(resourceType)
                operation.summary = "create-type: Create a new $resourceType instance"
                addFhirResourceRequestBody(openApi, operation, ctx, genericExampleSupplier(ctx, resourceType))
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Instance Update
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.UPDATE)) {
                val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PUT)
                operation.addTagsItem(resourceType)
                operation.summary =
                    "update-instance: Update an existing $resourceType instance, or create using a client-assigned ID"
                addResourceIdParameter(operation)
                addFhirResourceRequestBody(openApi, operation, ctx, genericExampleSupplier(ctx, resourceType))
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Type history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                val operation = getPathItem(paths, "/$resourceType/_history", PathItem.HttpMethod.GET)
                operation.addTagsItem(resourceType)
                operation.summary =
                    "type-history: Fetch the resource change history for all resources of type $resourceType"
                addFhirResourceResponse(ctx, openApi, operation, null)
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
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Instance Patch
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.PATCH)) {
                val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.PATCH)
                operation.addTagsItem(resourceType)
                operation.summary = "instance-patch: Patch a resource instance of type $resourceType by ID"
                addResourceIdParameter(operation)
                addFhirResourceRequestBody(openApi, operation, FHIR_CONTEXT_CANONICAL, patchExampleSupplier())
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Instance Delete
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.DELETE)) {
                val operation = getPathItem(paths, "/$resourceType/{id}", PathItem.HttpMethod.DELETE)
                operation.addTagsItem(resourceType)
                operation.summary = "instance-delete: Perform a logical delete on a resource instance"
                addResourceIdParameter(operation)
                addFhirResourceResponse(ctx, openApi, operation, null)
            }

            // Search
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE)) {
                val operation = getPathItem(paths, "/$resourceType", PathItem.HttpMethod.GET)
                operation.addTagsItem(resourceType)
                operation.description = "This is a search type"
                operation.summary = "search-type: Search for $resourceType instances"
                addFhirResourceResponse(ctx, openApi, operation, null)
                for (nextSearchParam in nextResource.searchParam) {
                    val parametersItem = Parameter()
                    operation.addParametersItem(parametersItem)
                    parametersItem.name = nextSearchParam.name
                    parametersItem.setIn("query")
                    parametersItem.description = nextSearchParam.documentation
                    parametersItem.style = Parameter.StyleEnum.SIMPLE
                }
            }

            // Resource-level Operations
            for (nextOperation in nextResource.operation) {
                addFhirOperation(ctx, openApi, paths, resourceType, nextOperation)
            }
        }
        return openApi
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
        if (!theOpenApi.components.schemas.containsKey(FHIR_JSON_RESOURCE)) {
            val fhirJsonSchema = ObjectSchema()
            fhirJsonSchema.description = "A FHIR resource"
            theOpenApi.components.addSchemas(FHIR_JSON_RESOURCE, fhirJsonSchema)
        }
        if (!theOpenApi.components.schemas.containsKey(FHIR_XML_RESOURCE)) {
            val fhirXmlSchema = ObjectSchema()
            fhirXmlSchema.description = "A FHIR resource"
            theOpenApi.components.addSchemas(FHIR_XML_RESOURCE, fhirXmlSchema)
        }
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
                        true
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
                        true
                    )
                }
            } else {
                if (operationDefinition.get()!!.system) {
                    val operation = getPathItem(
                        thePaths, "/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.GET
                    )
                    populateOperation(theFhirContext, theOpenApi, null, operationDefinition.get(), operation, true)
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
                        false
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
                        false
                    )
                }
            } else {
                if (operationDefinition.get()!!.system) {
                    val operation = getPathItem(
                        thePaths, "/$" + operationDefinition.get()!!
                            .code, PathItem.HttpMethod.POST
                    )
                    populateOperation(theFhirContext, theOpenApi, null, operationDefinition.get(), operation, false)
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
        theGet: Boolean
    ) {
        if (theResourceType == null) {
            theOperation.addTagsItem(PAGE_SYSTEM)
        } else {
            theOperation.addTagsItem(theResourceType)
        }
        theOperation.summary = theOperationDefinition!!.title
        theOperation.description = theOperationDefinition.description
        addFhirResourceResponse(theFhirContext, theOpenApi, theOperation, null)
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
            val exampleRequestBodyString =
                FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true).encodeResourceToString(exampleRequestBody)
            theOperation.requestBody = RequestBody()
            theOperation.requestBody.content = Content()
            val mediaType = MediaType()
            mediaType.example = exampleRequestBodyString
            mediaType.schema = Schema<Any?>().type("object").title("FHIR Resource")
            theOperation.requestBody.content.addMediaType(Constants.CT_FHIR_JSON_NEW, mediaType)
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
        theExampleSupplier: Supplier<IBaseResource?>?
    ) {
        val requestBody = RequestBody()
        requestBody.content = provideContentFhirResource(theOpenApi, theExampleFhirContext, theExampleSupplier)
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
        theResourceType: String?
    ) {
        theOperation.responses = ApiResponses()
        val response200 = ApiResponse()
        response200.description = "Success"
        response200.content = provideContentFhirResource(
            theOpenApi,
            theFhirContext,
            genericExampleSupplier(theFhirContext, theResourceType)
        )
        theOperation.responses.addApiResponse("200", response200)
    }

    private fun genericExampleSupplier(
        theFhirContext: FhirContext?,
        theResourceType: String?
    ): Supplier<IBaseResource?>? {
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
        theExampleSupplier: Supplier<IBaseResource?>?
    ): Content? {
        addSchemaFhirResource(theOpenApi)
        val retVal = Content()
        val jsonSchema = MediaType().schema(
            ObjectSchema().`$ref`(
                "#/components/schemas/$FHIR_JSON_RESOURCE"
            )
        )
        if (theExampleSupplier != null) {
            jsonSchema.example = theExampleFhirContext!!.newJsonParser().setPrettyPrint(true)
                .encodeResourceToString(theExampleSupplier.get())
        }
        retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema)
        val xmlSchema = MediaType().schema(
            ObjectSchema().`$ref`(
                "#/components/schemas/$FHIR_XML_RESOURCE"
            )
        )
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
