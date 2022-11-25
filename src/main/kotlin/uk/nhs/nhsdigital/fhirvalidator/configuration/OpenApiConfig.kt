package uk.nhs.nhsdigital.fhirvalidator.configuration


import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType

import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration



@Configuration
open class OpenApiConfig {
   var VALIDATION = "Validation"
    var UTILITY = "Utility"
    var EXPANSION = "ValueSet Expansion (inc. Filtering)"
    var CONFORMANCE = "FHIR Package Queries"
   // val SVCM = "FHIR Terminology"
  //  val SVCM_95 = "Query Value Set"
  //  val SVCM_96 = "Query Code System"
  //  val SVCM_97 = "Expand Value Set"
    val SVCM_98 = "Lookup Code"
   // val SVCM_99 = "Validate Code"
   // val SVCM_100 = "Query Concept Map"
   // val SVCM_101 = "Translate Code"
    var MEDICATION_DEFINITION = "Experimental - FHIR R5 Medication Definition"
    var EXPERIMENTAL = "Experimental"

    @Bean
    open fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val oas = OpenAPI()
            .info(
                Info()
                    .title("Conformance Support (R4)")
                    .version(fhirServerProperties.server.version)
                    .description(fhirServerProperties.server.name
                            + "\n "
                            + "\n [UK Core Implementation Guide (1.0.0-pre-release)](https://simplifier.net/guide/ukcoreimplementationguide0.5.0-stu1/home?version=current)"
                            + "\n\n [NHS Digital Implementation Guide (2.6.0)](https://simplifier.net/guide/nhsdigital?version=2.6.0)"
                        )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )
        oas.addServersItem(
            Server().description(fhirServerProperties.server.name).url(fhirServerProperties.server.baseUrl)
        )

        // VALIDATION

        oas.addTagsItem(io.swagger.v3.oas.models.tags.Tag()
            .name(VALIDATION)
            .description("[Validation](https://www.hl7.org/fhir/R4/validation.html)")
        )

        oas.addTagsItem(io.swagger.v3.oas.models.tags.Tag()
            .name(EXPANSION)
            .description("[expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html)")
        )
        oas.addTagsItem(io.swagger.v3.oas.models.tags.Tag()
            .name(SVCM_98)
            .description("[lookup](https://www.hl7.org/fhir/R4/operation-codesystem-lookup.html)")
        )
/*
        oas.addTagsItem(io.swagger.v3.oas.models.tags.Tag()
            .name(SVCM)
            .description("[HL7 FHIR Terminology](https://www.hl7.org/fhir/R4/terminologies-systems.html)")
            .externalDocs(ExternalDocumentation()
                .description("")
                .url(""))
        )
  */

        /*
        oas.addTagsItem(getTerminologyTag("95",SVCM_95))
        oas.addTagsItem(getTerminologyTag("96",SVCM_96))
        oas.addTagsItem(getTerminologyTag("97",SVCM_97))
        oas.addTagsItem(getTerminologyTag("98",SVCM_98))
       // oas.addTagsItem(getTerminologyTag("99",SVCM_99))
        oas.addTagsItem(getTerminologyTag("100",SVCM_100))
        oas.addTagsItem(getTerminologyTag("101",SVCM_101))
*/
        val validateItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(VALIDATION)
                    .summary(
                        "The validate operation checks whether the attached content would be acceptable either generally, as a create, an update or as a delete to an existing resource.")
                    .description("Validating a resource means, checking that the following aspects of the resource are valid: \n"
                            + " - **Structure:** Check that all the content in the resource is described by the specification, and nothing extra is present \n"
                            + " - **Cardinality:** Check that the cardinality of all properties is correct (min & max) \n"
                            + " - **Value Domains:** Check that the values of all properties conform to the rules for the specified types (including checking that enumerated codes are valid) \n"
                            + " - **Coding/CodeableConcept bindings:** Check that codes/displays provided in the Coding/CodeableConcept types are valid \n"
                            + " - **Invariants:** Check that the invariants (co-occurrence rules, etc.) have been followed correctly \n"
                            + " - **Profiles:** Check that any rules in profiles have been followed (including those listed in the Resource.meta.profile, or in CapabilityStatement, or in an ImplementationGuide, or otherwise required by context) \n"
                            + " - **Questionnaires:** Check that a QuestionnaireResponse is valid against its matching Questionnaire \n"
                            + " \n \n"
                            + "The validate operation checks whether the attached content would be acceptable either generally, as a create, an update or as a delete to an existing resource. \n"
                            + "Note that this operation is not the only way to validate resources - see [Validating Resources](https://www.hl7.org/fhir/R4/validation.html) for further information. \n"
                            + "\n"
                            + "The official URL for this operation definition is \n"
                            + " **http://hl7.org/fhir/OperationDefinition/Resource-validate** ")
                    .responses(getApiResponsesXMLJSON_JSONDefault())
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

        val fhirPathItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(UTILITY)
                    .summary("Experimental fhir path expression evaluation")
                    .description("[fhir path](https://www.hl7.org/fhir/R4/fhirpath.html)")
                    .responses(getApiResponsesXMLJSON_JSONDefault())
                    .addParametersItem(Parameter()
                        .name("expression")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("FHIRPath expression")
                        .schema(StringSchema())
                        .example("identifier.where(system='https://fhir.nhs.uk/Id/nhs-number')"))
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{\"resourceType\":\"Patient\"}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    ))
            )
        oas.path("/FHIR/R4/\$fhirpathEvaluate",fhirPathItem)


        val convertItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(UTILITY)
                    .summary("Switch between XML and JSON formats")
                    .responses(getApiResponsesXMLJSON_XMLDefault())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType().schema(StringSchema()._default("{\"resourceType\":\"CapabilityStatement\"}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    )))
        oas.path("/FHIR/R4/\$convert",convertItem)

        oas.path("/FHIR/R4/StructureDefinition",
            getPathItem(CONFORMANCE,"StructureDefinition", "Structure Definition (profile)", "url", "https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient" ,"" )
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
                .addParametersItem(Parameter()
                    .name("type")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("Type defined or constrained by this structure")
                    .schema(StringSchema())
                    .example("Patient")
                )
                .addParametersItem(Parameter()
                    .name("ext-context")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The system is the URL for the context-type: e.g. http://hl7.org/fhir/extension-context-type#element|CodeableConcept.text")
                    .schema(StringSchema())
                    .example("Patient")
                )
        )


        oas.path("/FHIR/R4/MessageDefinition",getPathItem(CONFORMANCE,"MessageDefinition", "Message Definition", "url" , "https://fhir.nhs.uk/MessageDefinition/prescription-order", ""))


        // SVCM

        // ITI-95 Query Value Set
        var pathItem = getPathItem(getTerminologyTagName(CONFORMANCE),"ValueSet", "Value Set", "url" , "https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code",
        "This transaction is used by the Terminology Consumer to find value sets based on criteria it\n" +
                "provides in the query parameters of the request message, or to retrieve a specific value set. The\n" +
                "request is received by the Terminology Repository. The Terminology Repository processes the\n" +
                "request and returns a response of the matching value sets.")
        oas.path("/FHIR/R4/ValueSet",pathItem)

        // ITI 96 Query Code System

        pathItem = getPathItem(getTerminologyTagName(CONFORMANCE),"CodeSystem", "Code System", "url", "https://fhir.nhs.uk/CodeSystem/NHSD-API-ErrorOrWarningCode",
        "This transaction is used by the Terminology Consumer to solicit information about code systems " +
                "whose data match data provided in the query parameters on the request message. The request is " +
                "received by the Terminology Repository. The Terminology Repository processes the request and " +
                "returns a response of the matching code systems.")
        oas.path("/FHIR/R4/CodeSystem",pathItem)

        // ITI 97 Expand Value Set [
        oas.path("/FHIR/R4/ValueSet/\$expand",PathItem()
            .get(
                Operation()
                    .addTagsItem(getTerminologyTagName(EXPANSION))
                    .summary("Expand a Value Set")
                    .description("This transaction is used by the Terminology Consumer to expand a given ValueSet to return the\n" +
                            "full list of concepts available in that ValueSet. The request is received by the Terminology\n" +
                            "Repository. The Terminology Repository processes the request and returns a response of the\n" +
                            "expanded ValueSet. \n\n" +
                            "FHIR Definition [expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html) "
                    )
                    .responses(getApiResponses())
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
                    .addTagsItem(getTerminologyTagName(EXPANSION))
                    .summary("The definition of a value set is used to create a simple collection of codes suitable for use for data entry or validation. Body should be a FHIR ValueSet").responses(getApiResponses())
                    .description("[expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html)")
                    .responses(getApiResponsesXMLJSON_JSONDefault())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    ))
            )
        )
        val eclItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(getTerminologyTagName(EXPANSION))
                    .summary("Expand a SNOMED CT ecl statement.")
                    .description("This internally uses ValueSet [expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html) operation.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("ecl")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(StringSchema())
                        .example("< 19829001 |Disorder of lung| AND < 301867009 |Edema of trunk|"))
                    .addParametersItem(Parameter()
                        .name("count")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("(EXPERIMENTAL) A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(StringSchema())
                        .example("10"))

            )

        oas.path("/FHIR/R4/ValueSet/\$expandEcl",eclItem)

        val searchItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(getTerminologyTagName(EXPANSION))
                    .summary("Search SNOMED CT for a term.")
                    .description("This internally uses ValueSet [expand](https://www.hl7.org/fhir/R4/operation-valueset-expand.html) operation.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("filter")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("(EXPERIMENTAL) A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(StringSchema())
                        .example("Otalgia"))
                    .addParametersItem(Parameter()
                        .name("count")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("(EXPERIMENTAL) A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(StringSchema())
                        .example("10"))
                    .addParametersItem(Parameter()
                        .name("includeDesignations")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("(EXPERIMENTAL) A text filter that is applied to restrict the codes that are returned (this is useful in a UI context).")
                        .schema(BooleanSchema())
                        .example("true"))

            )

        oas.path("/FHIR/R4/ValueSet/\$expandSCT",searchItem)

        // Lookup Code [ITI-98]
        val lookupItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(getTerminologyTagName(SVCM_98))
                    .summary("Lookup a Code in a Value Set")
                    .description("This transaction is used by the Terminology Consumer to lookup a given code to return the full " +
                            "details. The request is received by the Terminology Repository. The Terminology Repository " +
                            "processes the request and returns a response of the code details as a Parameters Resource." +
                            "\n\nFHIR Definition [lookup](https://www.hl7.org/fhir/R4/operation-codesystem-lookup.html)")
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

        // Validate Code [ITI-99]

        val validateCodeItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(getTerminologyTagName(VALIDATION))
                    .summary("Validate that a coded value is in the set of codes allowed by a value set.")
                    .description("This transaction is used by the Terminology Consumer to validate the existence of a given code " +
                            "in a value set or code system. The request is received by the Terminology Repository. The " +
                            "Terminology Repository processes the request and returns a response as a Parameters Resource." +
                            "\n\nFHIR Definition [validate-code](https://www.hl7.org/fhir/R4/operation-valueset-validate-code.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("url")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Value set Canonical URL. The server must know the value set (e.g. it is defined explicitly in the server's value sets, or it is defined implicitly by some code system known to the server")
                        .schema(StringSchema().format("uri"))
                        //.example("https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code")
                    )
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

        // Query Concept Map [ITI-100]

        oas.path("/FHIR/R4/ConceptMap",getPathItem(getTerminologyTagName(CONFORMANCE),"ConceptMap", "Concept Map", "url" , "https://fhir.nhs.uk/ConceptMap/eps-issue-code-to-fhir-issue-type",
            "This transaction is used by the Terminology Consumer that supports the Translate Option to " +
                    "solicit information about concept maps whose data match data provided in the query parameters " +
                    "on the request message. The request is received by the Terminology Repository that supports the " +
                    "Translate Option. The Terminology Repository processes the request and returns a response of " +
                    "the matching concept maps."))

        // 3.101 Translate Code [ITI-101]

        // Terminology Misc

        val subsumesItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(EXPERIMENTAL)
                    .summary("Test the subsumption relationship between code A and code B given the semantics of subsumption in the underlying code system ")
                    .description("[subsumes](https://hl7.org/fhir/R4/codesystem-operation-subsumes.html)")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("codeA")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The \"A\" code that is to be tested.")
                        .schema(StringSchema().format("code"))
                        .example("15517911000001104"))
                    .addParametersItem(Parameter()
                        .name("codeB")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The \"B\" code that is to be tested.")
                        .schema(StringSchema().format("code"))
                        .example("15513411000001100"))
                    .addParametersItem(Parameter()
                        .name("system")
                        .`in`("query")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The code system in which subsumption testing is to be performed. This must be provided unless the operation is invoked on a code system instance")
                        .schema(StringSchema())
                        .example("http://snomed.info/sct"))

            )

        oas.path("/FHIR/R4/CodeSystem/\$subsumes",subsumesItem)



        // MEDICATION DEFINITION

        val medicineItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(MEDICATION_DEFINITION)
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
                    .addTagsItem(MEDICATION_DEFINITION)
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
                    .addTagsItem(MEDICATION_DEFINITION)
                    .summary("A medically related item or items, in a container or package..")
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
                    .addTagsItem(MEDICATION_DEFINITION)
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

        // Hidden

        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(CONFORMANCE)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))

        oas.path("/FHIR/R4/CapabilityStatement",getPathItem(CONFORMANCE, "CapabilityStatement", "Capability Statement", "url", "https://fhir.nhs.uk/CapabilityStatement/apim-medicines-api-example" ,"" ))
        oas.path("/FHIR/R4/NamingSystem",getPathItem(CONFORMANCE,"NamingSystem", "Naming System", "value", "https://fhir.hl7.org.uk/Id/gmc-number", "" ))
        oas.path("/FHIR/R4/OperationDefinition",
            getPathItem(CONFORMANCE,"OperationDefinition", "Operation Definition", "url", "https://fhir.nhs.uk/OperationDefinition/MessageHeader-process-message", "" )
        )
        oas.path("/FHIR/R4/SearchParameter",getPathItem(CONFORMANCE,"SearchParameter", "Search Parameter", "url" , "https://fhir.nhs.uk/SearchParameter/immunization-procedure-code", ""))
        oas.path("/FHIR/R4/StructureMap",getPathItem(CONFORMANCE, "StructureMap", "Structure Map", "url" , "http://fhir.nhs.uk/StructureMap/MedicationRepeatInformation-Extension-3to4", ""))

        val verifyOASItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(EXPERIMENTAL)
                    .summary("Verifies a self contained OAS file for FHIR Conformance. Response format is the same as the FHIR \$validate operation")
                    .description("This is a proof of concept.")
                    .responses(getApiResponsesRAWJSON())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/x-yaml",MediaType().schema(StringSchema()))
                        .addMediaType("application/json",MediaType().schema(StringSchema()))))
            )
        oas.path("/\$verifyOAS",verifyOASItem)

        val markdownItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(EXPERIMENTAL)
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

        val convertR4Item = PathItem()
            .post(
                Operation()
                    .addTagsItem(UTILITY)
                    .summary("Convert to FHIR R4 (Structure only)")
                    .addParametersItem(Parameter()
                        .name("Accept")
                        .`in`("header")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Select response format")
                        .schema(StringSchema()._enum(listOf("application/fhir+xml","application/fhir+json"))))
                    .responses(getApiResponsesXMLJSON_XMLDefault())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType().schema(StringSchema()._default("{\"resourceType\":\"CapabilityStatement\"}")))
                        .addMediaType("application/fhir+xml",MediaType().schema(StringSchema()))
                    )))
        oas.path("/FHIR/STU3/\$convertR4",convertR4Item)

        val capabilityStatementItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(EXPERIMENTAL)
                    .summary("Converts a FHIR CapabilityStatement to openapi v3 format")
                    .responses(getApiResponsesMarkdown())
                    .requestBody(RequestBody().content(Content().addMediaType("application/fhir+json",MediaType().schema(StringSchema()._default("{\"resourceType\":\"CapabilityStatement\"}")))))
            )
        oas.path("/FHIR/R4/\$openapi",capabilityStatementItem)

        return oas

    }

    private fun getzTerminologyTag(itiRef: String, itiDesc: String): io.swagger.v3.oas.models.tags.Tag? {
        return io.swagger.v3.oas.models.tags.Tag()
            .name(getTerminologyTagName(itiDesc))
            .description("[HL7 FHIR Terminology](https://www.hl7.org/fhir/R4/terminologies-systems.html) \n" +
                    "[IHE Profile: Sharing Valuesets, Codes, and Maps (SVCM) ITI-"+itiRef+"](https://profiles.ihe.net/ITI/TF/Volume1/ch-51.html)")
    }
    private fun getTerminologyTagName(itiDesc: String): String {
        return itiDesc
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
    fun getApiResponsesXMLJSON_JSONDefault() : ApiResponses {

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

    fun getApiResponsesXMLJSON_XMLDefault() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content()
            .addMediaType("application/fhir+xml", MediaType().schema(StringSchema()._default("<>")))
            .addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))

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
    fun getPathItem(tag :String, name : String,fullName : String, param : String, example : String, description : String ) : PathItem {
        val pathItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(tag)
                    .summary("search-type")
                    .description(description)
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
