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
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration



@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val oas = OpenAPI()
            .info(
                Info()
                    .title("NHS Digital Interoperability - Conformance Support")
                    .version(fhirServerProperties.server.version)
                    .description(fhirServerProperties.server.name)
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )

        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))
        oas.path("/FHIR/R4/CapabilityStatement",getPathItem("CapabilityStatement", "Capability Statement", "url", "https://fhir.nhs.uk/CapabilityStatement/apim-medicines-api-example" ))
        oas.path("/FHIR/R4/CodeSystem",getPathItem("CodeSystem", "Code System", "url", "https://fhir.nhs.uk/CodeSystem/NHSD-API-ErrorOrWarningCode" ))
        oas.path("/FHIR/R4/ConceptMap",getPathItem("ConceptMap", "Concept Map", "url" , "https://fhir.nhs.uk/ConceptMap/eps-issue-code-to-fhir-issue-type"))
        oas.path("/FHIR/R4/MessageDefinition",getPathItem("MessageDefinition", "Message Definition", "url" , "https://fhir.nhs.uk/MessageDefinition/prescription-order"))
        oas.path("/FHIR/R4/NamingSystem",getPathItem("NamingSystem", "Naming System", "value", "https://fhir.hl7.org.uk/Id/gmc-number" ))
        oas.path("/FHIR/R4/OperationDefinition",getPathItem("OperationDefinition", "Operation Definition", "url", "https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message" ))
        oas.path("/FHIR/R4/SearchParameter",getPathItem("SearchParameter", "Search Parameter", "url" , "https://fhir.nhs.uk/SearchParameter/immunization-procedure-code"))

        oas.path("/FHIR/R4/StructureDefinition",getPathItem("StructureDefinition", "Structure Definition (profile)", "url", "https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient" ))
        oas.path("/FHIR/R4/StructureMap",getPathItem("StructureMap", "Structure Map", "url" , "http://fhir.nhs.uk/StructureMap/MedicationRepeatInformation-Extension-3to4"))
        oas.path("/FHIR/R4/ValueSet",getPathItem("ValueSet", "Value Set", "url" , "https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"))


        return oas
          //  .path("FHIR/R4/CodeSystem",
          //      PathItem().get(Operation().addParametersItem(
          //          Parameter().name("url").required(true).`in`("token").description("The uri that identifies the code system")
          //      )))
           // .path("FHIR/R4/CodeSystem/\$lookup",
           //     PathItem().get(Operation()))
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
    fun getPathItem(name : String,fullName : String, param : String, example : String ) : PathItem {
        val pathItem = PathItem()
            .get(
                Operation()
                    .summary("search-type")
                    .description("[search](http://www.hl7.org/fhir/search.html) for "+name +" instances.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name(param)
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the "+fullName)
                        .schema(StringSchema())
                        .example(example)))
        return pathItem
    }
}
