package com.example.fhirvalidator.openapi;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.HapiExtensions;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.*;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.api.Constants;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.thymeleaf.templateresource.ClassLoaderTemplateResource;

import static org.apache.commons.lang3.StringUtils.defaultString;
import com.example.fhirvalidator.service.ImplementationGuideParser;

public class OpenApi {
    public static final String FHIR_JSON_RESOURCE = "FHIR-JSON-RESOURCE";
    public static final String FHIR_XML_RESOURCE = "FHIR-XML-RESOURCE";
    public static final String PAGE_SYSTEM = "System Level Operations";
    public static final String PAGE_ALL = "All";
    public static final FhirContext FHIR_CONTEXT_CANONICAL = FhirContext.forR4();
    public static final String REQUEST_DETAILS = "REQUEST_DETAILS";
    public static final String RACCOON_PNG = "raccoon.png";
    private final String mySwaggerUiVersion;


    private final Map<String, String> myResourcePathToClasspath = new HashMap<>();
    private final Map<String, String> myExtensionToContentType = new HashMap<>();
    private String myBannerImage;

    private FhirContext ctx;
    private List<NpmPackage> npmPackages;
    private ImplementationGuideParser implementationGuideParser;

    public OpenApi(FhirContext _ctx, List<NpmPackage> _npmPackages,ImplementationGuideParser _implementationGuideParser) {
        this.ctx = _ctx;
        this.mySwaggerUiVersion = "3.0.0";
        this.npmPackages = _npmPackages;
        this.implementationGuideParser = _implementationGuideParser;
    }


    public String removeTrailingSlash(String theUrl) {
        while (theUrl != null && theUrl.endsWith("/")) {
            theUrl = theUrl.substring(0, theUrl.length() - 1);
        }
        return theUrl;
    }


    public OpenAPI generateOpenApi(CapabilityStatement cs) {

        OpenAPI openApi = new OpenAPI();

        openApi.setInfo(new Info());
        openApi.getInfo().setDescription(cs.getDescription());
        openApi.getInfo().setTitle(cs.getSoftware().getName());
        openApi.getInfo().setVersion(cs.getSoftware().getVersion());
        openApi.getInfo().setContact(new Contact());
        openApi.getInfo().getContact().setName(cs.getContactFirstRep().getName());
        openApi.getInfo().getContact().setEmail(cs.getContactFirstRep().getTelecomFirstRep().getValue());

        Server server = new Server();
        openApi.addServersItem(server);
        server.setUrl(cs.getImplementation().getUrl());
        server.setDescription(cs.getSoftware().getName());

        Paths paths = new Paths();
        openApi.setPaths(paths);


        Tag serverTag = new Tag();
        serverTag.setName(PAGE_SYSTEM);
        serverTag.setDescription("Server-level operations");
        openApi.addTagsItem(serverTag);

        Operation capabilitiesOperation = getPathItem(paths, "/metadata", PathItem.HttpMethod.GET);
        capabilitiesOperation.addTagsItem(PAGE_SYSTEM);
        capabilitiesOperation.setSummary("server-capabilities: Fetch the server FHIR CapabilityStatement");
        addFhirResourceResponse(ctx, openApi, capabilitiesOperation, "CapabilityStatement");

        Set<CapabilityStatement.SystemRestfulInteraction> systemInteractions = cs.getRestFirstRep().getInteraction().stream().map(t -> t.getCode()).collect(Collectors.toSet());
        // Transaction Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.TRANSACTION) || systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.BATCH)) {
            Operation transaction = getPathItem(paths, "/", PathItem.HttpMethod.POST);
            transaction.addTagsItem(PAGE_SYSTEM);
            transaction.setSummary("server-transaction: Execute a FHIR Transaction (or FHIR Batch) Bundle");
            addFhirResourceResponse(ctx, openApi, transaction, null);
            addFhirResourceRequestBody(openApi, transaction, ctx, null);
        }

        // System History Operation
        if (systemInteractions.contains(CapabilityStatement.SystemRestfulInteraction.HISTORYSYSTEM)) {
            Operation systemHistory = getPathItem(paths, "/_history", PathItem.HttpMethod.GET);
            systemHistory.addTagsItem(PAGE_SYSTEM);
            systemHistory.setSummary("server-history: Fetch the resource change history across all resource types on the server");
            addFhirResourceResponse(ctx, openApi, systemHistory, null);
        }

        // System-level Operations
        for (CapabilityStatement.CapabilityStatementRestResourceOperationComponent nextOperation : cs.getRestFirstRep().getOperation()) {
            // TODO    addFhirOperation(ctx, openApi, theRequestDetails, capabilitiesProvider, paths, null, nextOperation);
        }


        for (CapabilityStatement.CapabilityStatementRestResourceComponent nextResource : cs.getRestFirstRep().getResource()) {
            String resourceType = nextResource.getType();

            Set<CapabilityStatement.TypeRestfulInteraction> typeRestfulInteractions = nextResource.getInteraction().stream().map(t -> t.getCodeElement().getValue()).collect(Collectors.toSet());

            Tag resourceTag = new Tag();
            resourceTag.setName(resourceType);
            resourceTag.setDescription("The " + resourceType + " FHIR resource type");
            openApi.addTagsItem(resourceTag);

            // Instance Read
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.READ)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}", PathItem.HttpMethod.GET);
                operation.addTagsItem(resourceType);
                operation.setSummary("read-instance: Read " + resourceType + " instance");
                addResourceIdParameter(operation);
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Instance VRead
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.VREAD)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}/_history/{version_id}", PathItem.HttpMethod.GET);
                operation.addTagsItem(resourceType);
                operation.setSummary("vread-instance: Read " + resourceType + " instance with specific version");
                addResourceIdParameter(operation);
                addResourceVersionIdParameter(operation);
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Type Create
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.CREATE)) {
                Operation operation = getPathItem(paths, "/" + resourceType, PathItem.HttpMethod.POST);
                operation.addTagsItem(resourceType);
                operation.setSummary("create-type: Create a new " + resourceType + " instance");
                addFhirResourceRequestBody(openApi, operation, ctx, genericExampleSupplier(ctx, resourceType));
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Instance Update
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.UPDATE)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}", PathItem.HttpMethod.PUT);
                operation.addTagsItem(resourceType);
                operation.setSummary("update-instance: Update an existing " + resourceType + " instance, or create using a client-assigned ID");
                addResourceIdParameter(operation);
                addFhirResourceRequestBody(openApi, operation, ctx, genericExampleSupplier(ctx, resourceType));
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Type history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/_history", PathItem.HttpMethod.GET);
                operation.addTagsItem(resourceType);
                operation.setSummary("type-history: Fetch the resource change history for all resources of type " + resourceType);
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Instance history
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}/_history", PathItem.HttpMethod.GET);
                operation.addTagsItem(resourceType);
                operation.setSummary("instance-history: Fetch the resource change history for all resources of type " + resourceType);
                addResourceIdParameter(operation);
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Instance Patch
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.PATCH)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}", PathItem.HttpMethod.PATCH);
                operation.addTagsItem(resourceType);
                operation.setSummary("instance-patch: Patch a resource instance of type " + resourceType + " by ID");
                addResourceIdParameter(operation);
                addFhirResourceRequestBody(openApi, operation, FHIR_CONTEXT_CANONICAL, patchExampleSupplier());
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Instance Delete
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.DELETE)) {
                Operation operation = getPathItem(paths, "/" + resourceType + "/{id}", PathItem.HttpMethod.DELETE);
                operation.addTagsItem(resourceType);
                operation.setSummary("instance-delete: Perform a logical delete on a resource instance");
                addResourceIdParameter(operation);
                addFhirResourceResponse(ctx, openApi, operation, null);
            }

            // Search
            if (typeRestfulInteractions.contains(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE)) {
                Operation operation = getPathItem(paths, "/" + resourceType, PathItem.HttpMethod.GET);
                operation.addTagsItem(resourceType);
                operation.setDescription("This is a search type");
                operation.setSummary("search-type: Search for " + resourceType + " instances");
                addFhirResourceResponse(ctx, openApi, operation, null);

                for (CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent nextSearchParam : nextResource.getSearchParam()) {
                    Parameter parametersItem = new Parameter();
                    operation.addParametersItem(parametersItem);

                    parametersItem.setName(nextSearchParam.getName());
                    parametersItem.setIn("query");
                    parametersItem.setDescription(nextSearchParam.getDocumentation());
                    parametersItem.setStyle(Parameter.StyleEnum.SIMPLE);
                }
            }

            // Resource-level Operations
            for (CapabilityStatement.CapabilityStatementRestResourceOperationComponent nextOperation : nextResource.getOperation()) {
                addFhirOperation(ctx, openApi, paths, resourceType, nextOperation);
            }

        }

        return openApi;
    }

    private Supplier<IBaseResource> patchExampleSupplier() {
        return () -> {
            Parameters example = new Parameters();
            Parameters.ParametersParameterComponent operation = example
                    .addParameter()
                    .setName("operation");
            operation.addPart().setName("type").setValue(new StringType("add"));
            operation.addPart().setName("path").setValue(new StringType("Patient"));
            operation.addPart().setName("name").setValue(new StringType("birthDate"));
            operation.addPart().setName("value").setValue(new DateType("1930-01-01"));
            return example;
        };
    }

    private void addSchemaFhirResource(OpenAPI theOpenApi) {
        ensureComponentsSchemasPopulated(theOpenApi);

        if (!theOpenApi.getComponents().getSchemas().containsKey(FHIR_JSON_RESOURCE)) {
            ObjectSchema fhirJsonSchema = new ObjectSchema();
            fhirJsonSchema.setDescription("A FHIR resource");
            theOpenApi.getComponents().addSchemas(FHIR_JSON_RESOURCE, fhirJsonSchema);
        }

        if (!theOpenApi.getComponents().getSchemas().containsKey(FHIR_XML_RESOURCE)) {
            ObjectSchema fhirXmlSchema = new ObjectSchema();
            fhirXmlSchema.setDescription("A FHIR resource");
            theOpenApi.getComponents().addSchemas(FHIR_XML_RESOURCE, fhirXmlSchema);
        }
    }

    private void ensureComponentsSchemasPopulated(OpenAPI theOpenApi) {
        if (theOpenApi.getComponents() == null) {
            theOpenApi.setComponents(new Components());
        }
        if (theOpenApi.getComponents().getSchemas() == null) {
            theOpenApi.getComponents().setSchemas(new LinkedHashMap<>());
        }
    }



    private void addFhirOperation(FhirContext theFhirContext, OpenAPI theOpenApi, Paths thePaths, String theResourceType, CapabilityStatement.CapabilityStatementRestResourceOperationComponent theOperation)
    {

            OperationDefinition operationDefinition = null;
            IdType definitionId = new IdType(theOperation.getDefinition());

            npmPackages.forEach(
                    it -> {
                        implementationGuideParser.getResourcesOfTypeFromPackage(it,CapabilityStatement.class);
                    }
            );


            if (operationDefinition == null) return;

            if (!operationDefinition.getAffectsState()) {

                // GET form for non-state-affecting operations
                if (theResourceType != null) {
                    if (operationDefinition.getType()) {
                        Operation operation = getPathItem(thePaths, "/" + theResourceType + "/$" + operationDefinition.getCode(), PathItem.HttpMethod.GET);
                        populateOperation(theFhirContext, theOpenApi, theResourceType, operationDefinition, operation, true);
                    }
                    if (operationDefinition.getInstance()) {
                        Operation operation = getPathItem(thePaths, "/" + theResourceType + "/{id}/$" + operationDefinition.getCode(), PathItem.HttpMethod.GET);
                        addResourceIdParameter(operation);
                        populateOperation(theFhirContext, theOpenApi, theResourceType, operationDefinition, operation, true);
                    }
                } else {
                    if (operationDefinition.getSystem()) {
                        Operation operation = getPathItem(thePaths, "/$" + operationDefinition.getCode(), PathItem.HttpMethod.GET);
                        populateOperation(theFhirContext, theOpenApi, null, operationDefinition, operation, true);
                    }
                }

            } else {

                // POST form for all operations
                if (theResourceType != null) {
                    if (operationDefinition.getType()) {
                        Operation operation = getPathItem(thePaths, "/" + theResourceType + "/$" + operationDefinition.getCode(), PathItem.HttpMethod.POST);
                        populateOperation(theFhirContext, theOpenApi, theResourceType, operationDefinition, operation, false);
                    }
                    if (operationDefinition.getInstance()) {
                        Operation operation = getPathItem(thePaths, "/" + theResourceType + "/{id}/$" + operationDefinition.getCode(), PathItem.HttpMethod.POST);
                        addResourceIdParameter(operation);
                        populateOperation(theFhirContext, theOpenApi, theResourceType, operationDefinition, operation, false);
                    }
                } else {
                    if (operationDefinition.getSystem()) {
                        Operation operation = getPathItem(thePaths, "/$" + operationDefinition.getCode(), PathItem.HttpMethod.POST);
                        populateOperation(theFhirContext, theOpenApi, null, operationDefinition, operation, false);
                    }
                }

            }

    }


    private void populateOperation(FhirContext theFhirContext, OpenAPI theOpenApi, String theResourceType, OperationDefinition theOperationDefinition, Operation theOperation, boolean theGet) {
        if (theResourceType == null) {
            theOperation.addTagsItem(PAGE_SYSTEM);
        } else {
            theOperation.addTagsItem(theResourceType);
        }
        theOperation.setSummary(theOperationDefinition.getTitle());
        theOperation.setDescription(theOperationDefinition.getDescription());
        addFhirResourceResponse(theFhirContext, theOpenApi, theOperation, null);

        if (theGet) {

            for (OperationDefinition.OperationDefinitionParameterComponent nextParameter : theOperationDefinition.getParameter()) {
                Parameter parametersItem = new Parameter();
                theOperation.addParametersItem(parametersItem);

                parametersItem.setName(nextParameter.getName());
                parametersItem.setIn("query");
                parametersItem.setDescription(nextParameter.getDocumentation());
                parametersItem.setStyle(Parameter.StyleEnum.SIMPLE);
                parametersItem.setRequired(nextParameter.getMin() > 0);

                List<Extension> exampleExtensions = nextParameter.getExtensionsByUrl(HapiExtensions.EXT_OP_PARAMETER_EXAMPLE_VALUE);
                if (exampleExtensions.size() == 1) {
                    parametersItem.setExample(exampleExtensions.get(0).getValueAsPrimitive().getValueAsString());
                } else if (exampleExtensions.size() > 1) {
                    for (Extension next : exampleExtensions) {
                        String nextExample = next.getValueAsPrimitive().getValueAsString();
                        parametersItem.addExample(nextExample, new Example().value(nextExample));
                    }
                }

            }

        } else {

            Parameters exampleRequestBody = new Parameters();
            for (OperationDefinition.OperationDefinitionParameterComponent nextSearchParam : theOperationDefinition.getParameter()) {
                Parameters.ParametersParameterComponent param = exampleRequestBody.addParameter();
                param.setName(nextSearchParam.getName());
                String paramType = nextSearchParam.getType();
                switch (defaultString(paramType)) {
                    case "uri":
                    case "url":
                    case "code":
                    case "string": {
                        IPrimitiveType<?> type = (IPrimitiveType<?>) FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType).newInstance();
                        type.setValueAsString("example");
                        param.setValue((Type) type);
                        break;
                    }
                    case "integer": {
                        IPrimitiveType<?> type = (IPrimitiveType<?>) FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType).newInstance();
                        type.setValueAsString("0");
                        param.setValue((Type) type);
                        break;
                    }
                    case "boolean": {
                        IPrimitiveType<?> type = (IPrimitiveType<?>) FHIR_CONTEXT_CANONICAL.getElementDefinition(paramType).newInstance();
                        type.setValueAsString("false");
                        param.setValue((Type) type);
                        break;
                    }
                    case "CodeableConcept": {
                        CodeableConcept type = new CodeableConcept();
                        type.getCodingFirstRep().setSystem("http://example.com");
                        type.getCodingFirstRep().setCode("1234");
                        param.setValue(type);
                        break;
                    }
                    case "Coding": {
                        Coding type = new Coding();
                        type.setSystem("http://example.com");
                        type.setCode("1234");
                        param.setValue(type);
                        break;
                    }
                    case "Reference":
                        Reference reference = new Reference("example");
                        param.setValue(reference);
                        break;
                    case "Resource":
                        if (theResourceType != null) {
                            IBaseResource resource = FHIR_CONTEXT_CANONICAL.getResourceDefinition(theResourceType).newInstance();
                            resource.setId("1");
                            param.setResource((Resource) resource);
                        }
                        break;
                }

            }

            String exampleRequestBodyString = FHIR_CONTEXT_CANONICAL.newJsonParser().setPrettyPrint(true).encodeResourceToString(exampleRequestBody);
            theOperation.setRequestBody(new RequestBody());
            theOperation.getRequestBody().setContent(new Content());
            MediaType mediaType = new MediaType();
            mediaType.setExample(exampleRequestBodyString);
            mediaType.setSchema(new Schema().type("object").title("FHIR Resource"));
            theOperation.getRequestBody().getContent().addMediaType(Constants.CT_FHIR_JSON_NEW, mediaType);


        }
    }


    protected Operation getPathItem(Paths thePaths, String thePath, PathItem.HttpMethod theMethod) {
        PathItem pathItem;

        if (thePaths.containsKey(thePath)) {
            pathItem = thePaths.get(thePath);
        } else {
            pathItem = new PathItem();
            thePaths.addPathItem(thePath, pathItem);
        }

        switch (theMethod) {
            case POST:
                assert pathItem.getPost() == null : "Have duplicate POST at path: " + thePath;
                return pathItem.post(new Operation()).getPost();
            case GET:
                assert pathItem.getGet() == null : "Have duplicate GET at path: " + thePath;
                return pathItem.get(new Operation()).getGet();
            case PUT:
                assert pathItem.getPut() == null;
                return pathItem.put(new Operation()).getPut();
            case PATCH:
                assert pathItem.getPatch() == null;
                return pathItem.patch(new Operation()).getPatch();
            case DELETE:
                assert pathItem.getDelete() == null;
                return pathItem.delete(new Operation()).getDelete();
            case HEAD:
            case OPTIONS:
            case TRACE:
            default:
                throw new IllegalStateException();
        }
    }

    private void addFhirResourceRequestBody(OpenAPI theOpenApi, Operation theOperation, FhirContext theExampleFhirContext, Supplier<IBaseResource> theExampleSupplier) {
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(provideContentFhirResource(theOpenApi, theExampleFhirContext, theExampleSupplier));
        theOperation.setRequestBody(requestBody);
    }

    private void addResourceVersionIdParameter(Operation theOperation) {
        Parameter parameter = new Parameter();
        parameter.setName("version_id");
        parameter.setIn("path");
        parameter.setDescription("The resource version ID");
        parameter.setExample("1");
        parameter.setSchema(new Schema().type("string").minimum(new BigDecimal(1)));
        parameter.setStyle(Parameter.StyleEnum.SIMPLE);
        theOperation.addParametersItem(parameter);
    }

    private void addFhirResourceResponse(FhirContext theFhirContext, OpenAPI theOpenApi, Operation theOperation, String theResourceType) {
        theOperation.setResponses(new ApiResponses());
        ApiResponse response200 = new ApiResponse();
        response200.setDescription("Success");
        response200.setContent(provideContentFhirResource(theOpenApi, theFhirContext, genericExampleSupplier(theFhirContext, theResourceType)));
        theOperation.getResponses().addApiResponse("200", response200);
    }

    private Supplier<IBaseResource> genericExampleSupplier(FhirContext theFhirContext, String theResourceType) {
        if (theResourceType == null) {
            return null;
        }
        return () -> {
            IBaseResource example = null;
            if (theResourceType != null) {
                example = theFhirContext.getResourceDefinition(theResourceType).newInstance();
            }
            return example;
        };
    }


    private Content provideContentFhirResource(OpenAPI theOpenApi, FhirContext theExampleFhirContext, Supplier<IBaseResource> theExampleSupplier) {
        addSchemaFhirResource(theOpenApi);
        Content retVal = new Content();

        MediaType jsonSchema = new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/" + FHIR_JSON_RESOURCE));
        if (theExampleSupplier != null) {
            jsonSchema.setExample(theExampleFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(theExampleSupplier.get()));
        }
        retVal.addMediaType(Constants.CT_FHIR_JSON_NEW, jsonSchema);

        MediaType xmlSchema = new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/" + FHIR_XML_RESOURCE));
        if (theExampleSupplier != null) {
            xmlSchema.setExample(theExampleFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(theExampleSupplier.get()));
        }
        retVal.addMediaType(Constants.CT_FHIR_XML_NEW, xmlSchema);
        return retVal;
    }

    private void addResourceIdParameter(Operation theOperation) {
        Parameter parameter = new Parameter();
        parameter.setName("id");
        parameter.setIn("path");
        parameter.setDescription("The resource ID");
        parameter.setExample("123");
        parameter.setSchema(new Schema().type("string").minimum(new BigDecimal(1)));
        parameter.setStyle(Parameter.StyleEnum.SIMPLE);
        theOperation.addParametersItem(parameter);
    }

    protected ClassLoaderTemplateResource getIndexTemplate() {
        return new ClassLoaderTemplateResource(myResourcePathToClasspath.get("/swagger-ui/index.html"), StandardCharsets.UTF_8.name());
    }

    public void setBannerImage(String theBannerImage) {
        myBannerImage = theBannerImage;
    }

    public String getBannerImage() {
        return myBannerImage;
    }

}


