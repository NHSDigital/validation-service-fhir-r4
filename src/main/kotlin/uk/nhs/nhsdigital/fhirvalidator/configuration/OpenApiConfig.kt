package uk.nhs.nhsdigital.fhirvalidator.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType

import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration



@Configuration
open class OpenApiConfig {
    var FHIRSERVER = "FHIR Conformance"
    var FHIRSERVER_R4B = "Medication Definition Module Demo"
    var OASVERIFICATION = "OAS FHIR Conformance"
    @Bean
    open fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val oas = OpenAPI()
            .info(
                Info()
                    .title("NHS Digital Interoperability Standards - Conformance Support")
                    .version(fhirServerProperties.server.version)
                    .description(fhirServerProperties.server.name
                            + "\n "
                            + "\n [UK Core Implementation Guide (0.5.1)](https://simplifier.net/guide/ukcoreimplementationguide0.5.0-stu1/home?version=current)"
                            + "\n\n [NHS Digital Implementation Guide (2.6.0)](https://simplifier.net/guide/nhsdigital?version=2.6.0)"
                        )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )

        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))
        oas.path("/FHIR/R4/CapabilityStatement",getPathItem("CapabilityStatement", "Capability Statement", "url", "https://fhir.nhs.uk/CapabilityStatement/apim-medicines-api-example" ))
        oas.path("/FHIR/R4/CodeSystem",getPathItem("CodeSystem", "Code System", "url", "https://fhir.nhs.uk/CodeSystem/NHSD-API-ErrorOrWarningCode" ))

        val lookupItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Given a code/system, or a Coding, get additional details about the concept, including definition, status, designations, and properties. One of the products of this operation is a full decomposition of a code from a structured terminology.")
                    .description("[lookup](https://www.hl7.org/fhir/R4/operation-codesystem-lookup.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("code")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The code that is to be located. If a code is provided, a system must be provided")
                        .schema(StringSchema().format("code"))
                        .example("15517911000001104"))
                    .addParametersItem(Parameter()
                        .name("system")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The system for the code that is to be located")
                        .schema(StringSchema().format("url"))
                        .example("http://snomed.info/sct"))
                    .addParametersItem(Parameter()
                        .name("version")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The version of the system, if one was provided in the source data")
                        .schema(StringSchema()))
                    .addParametersItem(Parameter()
                            .name("coding")
                            .`in`("query")
                            .required(false)
                            .style(Parameter.StyleEnum.SIMPLE)
                            .description("The system for the code that is to be located")
                            .schema(StringSchema().format("Coding")))
                    .addParametersItem(Parameter()
                        .name("date")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The date for which the information should be returned.")
                        .schema(StringSchema().format("dateTime")))
                    .addParametersItem(Parameter()
                        .name("displayLanguage")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The requested language for display (see \$expand.displayLanguage)")
                        .schema(StringSchema().format("code")))
              /*      .addParametersItem(Parameter()
                        .name("property")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SPACEDELIMITED)
                        .explode(true)
                        .description("A property that the client wishes to be returned in the output. If no properties are specified, the server chooses what to return.")
                        .schema(StringSchema().format("code").maxItems(10))
                        .example("code display property fullySpecifiedName")) */
                    )

        oas.path("/FHIR/R4/CodeSystem/\$lookup",lookupItem)

        oas.path("/FHIR/R4/ConceptMap",getPathItem("ConceptMap", "Concept Map", "url" , "https://fhir.nhs.uk/ConceptMap/eps-issue-code-to-fhir-issue-type"))
        oas.path("/FHIR/R4/MessageDefinition",getPathItem("MessageDefinition", "Message Definition", "url" , "https://fhir.nhs.uk/MessageDefinition/prescription-order"))
        oas.path("/FHIR/R4/NamingSystem",getPathItem("NamingSystem", "Naming System", "value", "https://fhir.hl7.org.uk/Id/gmc-number" ))
        oas.path("/FHIR/R4/OperationDefinition",
            getPathItem("OperationDefinition", "Operation Definition", "url", "https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message" )
        )
        oas.path("/FHIR/R4/SearchParameter",getPathItem("SearchParameter", "Search Parameter", "url" , "https://fhir.nhs.uk/SearchParameter/immunization-procedure-code"))

        oas.path("/FHIR/R4/StructureDefinition",
            getPathItem("StructureDefinition", "Structure Definition (profile)", "url", "https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient" )
                .addParametersItem(Parameter()
                    .name("base")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("Definition that this type is constrained/specialized from")
                    .schema(StringSchema().format("reference"))
                )
                .addParametersItem(Parameter()
                    .name("name")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("Computationally friendly name of the structure definition")
                    .schema(StringSchema())
                )
        )
        oas.path("/FHIR/R4/StructureMap",getPathItem("StructureMap", "Structure Map", "url" , "http://fhir.nhs.uk/StructureMap/MedicationRepeatInformation-Extension-3to4"))
        oas.path("/FHIR/R4/ValueSet",getPathItem("ValueSet", "Value Set", "url" , "https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"))

        val validateCodeItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Validate that a coded value is in the set of codes allowed by a value set.")
                    .description("[validate-code](https://www.hl7.org/fhir/R4/operation-valueset-validate-code.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("url")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Value set Canonical URL. The server must know the value set (e.g. it is defined explicitly in the server's value sets, or it is defined implicitly by some code system known to the server")
                        .schema(StringSchema().format("uri"))
                        .example("https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"))
                    .addParametersItem(Parameter()
                        .name("code")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The code that is to be validated. If a code is provided, a system or a context must be provided.")
                        .schema(StringSchema().format("code"))
                        .example("15517911000001104"))
                    .addParametersItem(Parameter()
                        .name("system")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The system for the code that is to be validated")
                        .schema(StringSchema().format("uri"))
                        .example("http://snomed.info/sct"))
                    .addParametersItem(Parameter()
                        .name("display")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The display associated with the code, if provided. If a display is provided a code must be provided. If no display is provided, the server cannot validate the display value, but may choose to return a recommended display name using the display parameter in the outcome. Whether displays are case sensitive is code system dependent")
                        .schema(StringSchema())
                        .example("Methotrexate 10mg/0.2ml solution for injection pre-filled syringes"))
            )
        oas.path("/FHIR/R4/ValueSet/\$validate-code",validateCodeItem)

        oas.path("/FHIR/R4/ValueSet/\$expand",PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("The definition of a value set is used to create a simple collection of codes suitable for use for data entry or validation.").responses(getApiResponses())
                    .description("[expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html)")
                    .addParametersItem(Parameter()
                        .name("url")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("A canonical reference to a value set. The server must know the value set (e.g. it is defined explicitly in the server's value sets, or it is defined implicitly by some code system known to the server")
                        .schema(StringSchema().format("uri"))
                        .example("https://fhir.hl7.org.uk/ValueSet/UKCore-MedicationPrecondition"))
                    .addParametersItem(Parameter()
                        .name("filter")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("(EXPERIMENTAL - ValueSet must be in UKCore or NHSDigital IG) A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(StringSchema())
                        .example("Otalgia"))
                    )
            .post(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("The definition of a value set is used to create a simple collection of codes suitable for use for data entry or validation. Body should be a FHIR ValueSet").responses(getApiResponses())
                    .description("[expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html)")
                    .responses(getApiResponsesXMLJSON())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    ))
            )
        )

        val convertItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Switch between XML and JSON formats")
                    .addParametersItem(Parameter()
                        .name("Accept")
                        .`in`("header")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Select response format")
                        .schema(StringSchema()._enum(listOf("application/fhir+xml","application/fhir+json"))))
                    .responses(getApiResponsesXMLJSON())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType().schema(StringSchema()._default("{\"resourceType\":\"CapabilityStatement\"}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                        )))
        oas.path("/FHIR/R4/\$convert",convertItem)

        val capabilityStatementItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Converts a FHIR CapabilityStatement to openapi v3 format")
                    .responses(getApiResponsesMarkdown())
                    .requestBody(RequestBody().content(Content().addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{\"resourceType\":\"CapabilityStatement\"}")))))
            )
        oas.path("/FHIR/R4/\$openapi",capabilityStatementItem)

        val markdownItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Converts a FHIR profile to a simplifier compatible markdown format")
                    .responses(getApiResponsesMarkdown())
                    .addParametersItem(Parameter()
                        .name("url")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the resource.")
                        .schema(StringSchema())
                        .example("https://fhir.nhs.uk/StructureDefinition/NHSDigital-Organization"))
                    //.requestBody(RequestBody().content(Content().addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{\"resourceType\":\"Patient\"}")))))
            )
        oas.path("/FHIR/R4/\$markdown",markdownItem)

        val validateItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("The validate operation checks whether the attached content would be acceptable either generally, as a create, an update or as a delete to an existing resource.")
                    .description("[validate](https://www.hl7.org/fhir/R4/resource-operation-validate.html)")
                    .responses(getApiResponsesXMLJSON())
                    .addParametersItem(Parameter()
                        .name("profile")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the profile. If no profile uri is supplied, NHS Digital defaults will be used.")
                        .schema(StringSchema().format("token"))
                        .example("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient"))
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{\"resourceType\":\"Patient\"}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    ))
            )
        oas.path("/FHIR/R4/\$validate",validateItem)

        // FHIR R4B/R5


        val medicineItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER_R4B)
                    .summary("EXPERIMENTAL A medicinal product, being a substance or combination of substances that is intended to treat, prevent or diagnose a disease, or to restore, correct or modify physiological functions by exerting a pharmacological, immunological or metabolic action.")
                    .description("[Medication Definition Module](https://www.hl7.org/fhir/medication-definition-module.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("name")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The full product name")
                        .schema(StringSchema())
                        .example("Methotrexate 5mg"))
            )
        oas.path("/FHIR/R5/MedicinalProductDefinition",medicineItem)

        val medicineReadItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER_R4B)
                    .summary("EXPERIMENTAL A medicinal product, being a substance or combination of substances that is intended to treat, prevent or diagnose a disease, or to restore, correct or modify physiological functions by exerting a pharmacological, immunological or metabolic action.")
                    .description("[Medication Definition Module](https://www.hl7.org/fhir/medication-definition-module.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The product dm+d/SNOMED CT code")
                        .schema(StringSchema())
                        .example("39720311000001101"))
            )
        oas.path("/FHIR/R5/MedicinalProductDefinition/{id}",medicineReadItem)

        val medicinePackItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER_R4B)
                    .summary("EXPERIMENTAL A medically related item or items, in a container or package..")
                    .description("[Medication Definition Module](https://www.hl7.org/fhir/medication-definition-module.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("name")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("A name for this package.")
                        .schema(StringSchema())
                        .example("Methotrexate 5mg"))
            )
        oas.path("/FHIR/R5/PackagedProductDefinition",medicinePackItem)

        val medicinePackReadItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER_R4B)
                    .summary("EXPERIMENTAL A medically related item or items, in a container or package..")
                    .description("[Medication Definition Module](https://www.hl7.org/fhir/medication-definition-module.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The product pack dm+d/SNOMED CT code")
                        .schema(StringSchema())
                        .example("1029811000001106"))
            )
        oas.path("/FHIR/R5/PackagedProductDefinition/{id}",medicinePackReadItem)

        val verifyOASItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(OASVERIFICATION)
                    .summary("Verifies a self contained OAS file for FHIR Conformance. Response format is the same as the FHIR \$validate operation")
                    .description("This is a proof of concept.")
                    .responses(getApiResponsesRAWJSON())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/x-yaml",MediaType().schema(StringSchema()))
                        .addMediaType("application/json",MediaType().schema(StringSchema()))))
            )
        oas.path("/\$verifyOAS",verifyOASItem)
        return oas

    }

    fun getApiResponses() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content().addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }

    fun getApiResponsesMarkdown() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content().addMediaType("text/markdown", MediaType().schema(StringSchema()._default("{}")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }
    fun getApiResponsesXMLJSON() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content()
            .addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
            .addMediaType("application/fhir+xml", MediaType().schema(StringSchema()._default("<>")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }

    fun getApiResponsesRAWJSON() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content()
            .addMediaType("application/json", MediaType().schema(StringSchema()._default("{}")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }
    fun getPathItem(name : String,fullName : String, param : String, example : String ) : PathItem {
        val pathItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("search-type")
                    .description("[search](http://www.hl7.org/fhir/search.html) for "+name +" instances.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name(param)
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the "+fullName)
                        .schema(StringSchema().format("token"))
                        .example(example)))
        return pathItem
    }
}
