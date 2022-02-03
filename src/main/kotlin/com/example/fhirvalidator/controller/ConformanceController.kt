package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import com.example.fhirvalidator.service.ImplementationGuideParser
import com.example.fhirvalidator.service.OpenAPIParser
import io.swagger.v3.core.util.Json
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.web.bind.annotation.*

@RestController
class ConformanceController(
    private val fhirContext: FhirContext,
    private val npmPackages: List<NpmPackage>,
    private val searchParameters : Bundle
) {
    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext!!)
    private val openapi = OpenAPIParser(
        fhirContext,
        npmPackages
    )

    @GetMapping("metadata",produces = ["application/json", "application/fhir+json"])
    fun metadata(): String {
        val cs = getCapabilityStatement()
        return fhirContext.newJsonParser().encodeResourceToString(cs);
    }

    @GetMapping("/\$openapi",produces = ["application/json"])
    fun openapi(): String {
        val cs = getCapabilityStatement()
        //return Yaml.pretty(openapi.generateOpenApi(cs))
        return Json.pretty(openapi.generateOpenApi(cs));
    }

    @PostMapping("/\$openapi",produces = ["application/json"])
    fun postOpenapi( @RequestBody input: String): String {
        var inputResource : IBaseResource
        try {
            inputResource = fhirContext.newJsonParser().parseResource(input)
        } catch (ex : Exception) {
            inputResource = fhirContext.newXmlParser().parseResource(input)
        }
        if (inputResource is CapabilityStatement) {
            val cs : CapabilityStatement = inputResource
            return Json.pretty(openapi.generateOpenApi(cs));
        }
        return "Nowt found"
    }

    @GetMapping("CapabilityStatement/\$openapi",produces = ["application/json", "application/fhir+json"], params = ["url"])
    fun capabilityStatementOpenAPI(@RequestParam(name="url") url : String ): String {
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    CapabilityStatement::class.java
                )) {
                    if (resource.url.equals(url)) {
                        return Json.pretty(openapi.generateOpenApi(resource));
                    }
                }
            }
        }
        return ""
    }

    @GetMapping("CapabilityStatement",produces = ["application/json", "application/fhir+json"], params = ["url"])
    fun capabilityStatement(@RequestParam(name="url") url : String ): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    CapabilityStatement::class.java
                )) {
                    if (resource.url.equals(url)) {
                        bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                    }
                }
            }
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    @GetMapping("MessageDefinition",produces = ["application/json", "application/fhir+json"], params = ["url"])
    fun messageDefinition(@RequestParam(name="url") url : String ): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    MessageDefinition::class.java
                )) {
                    if (resource.url.equals(url)) {
                        bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                    }
                }
            }
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    @GetMapping("OperationDefinition",produces = ["application/json", "application/fhir+json"], params = ["url"])
    fun operationDefinition(@RequestParam(name="url") url : String ): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    OperationDefinition::class.java
                )) {
                    if (resource.url.equals(url)) {
                        bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                    }
                }
            }
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    @GetMapping("SearchParameter",produces = ["application/json", "application/fhir+json"], params = ["url"])
    fun searchParameter(@RequestParam(name="url") url : String ): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;
        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    SearchParameter::class.java
                )) {
                    if (resource.url.equals(url)) {
                        bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                    }
                }
            }
        }
        for (entry in searchParameters.entry) {
            if (entry.resource is SearchParameter) {
                if ((entry.resource as SearchParameter).url.equals(url)) {
                    bundle.entry.add(Bundle.BundleEntryComponent().setResource(entry.resource))
                }
            }
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    @GetMapping("StructureDefinition",produces = ["application/json", "application/fhir+json"])
    fun structureDefinition(@RequestParam url : String): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;

        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    StructureDefinition::class.java
                )) {
                    if (resource.url.equals(url)) {
                        bundle.entry.add(Bundle.BundleEntryComponent().setResource(resource))
                    }
                }
            }
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    fun getCapabilityStatement() : CapabilityStatement{
        val cs = CapabilityStatement();
        val apiextension = Extension();
        apiextension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition"
        npmPackages.forEach {
            val packageExtension = Extension();
            packageExtension.url="implementationGuide"
            packageExtension.extension.add(Extension().setUrl("name").setValue(StringType(it.name())))
            packageExtension.extension.add(Extension().setUrl("version").setValue(StringType(it.version())))
            apiextension.extension.add(packageExtension)
        }
        val packageExtension = Extension();
        packageExtension.url="openApi"
        packageExtension.extension.add(Extension().setUrl("documentation").setValue(UriType("https://simplifier.net/guide/NHSDigital/Home")))
        packageExtension.extension.add(Extension().setUrl("description").setValue(StringType("NHS Digital FHIR Implementation Guide")))
        apiextension.extension.add(packageExtension)
        cs.status = Enumerations.PublicationStatus.ACTIVE
        cs.kind = CapabilityStatement.CapabilityStatementKind.INSTANCE
        cs.fhirVersion = Enumerations.FHIRVersion._4_0_1

        val rest = CapabilityStatement.CapabilityStatementRestComponent()
        cs.rest.add(rest)
        val operation = CapabilityStatement.CapabilityStatementRestResourceOperationComponent()
        operation.name = "validate"
        operation.definition = "https://fhir.nhs.uk/OperationDefinition/validate"
        rest.operation.add(operation)

        // CapabilityStatement

        var resource = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource)
        resource.type = "CapabilityStatement"
        resource.profile = "https://fhir.nhs.uk/StructureDefinition/NHSDigital-CapabilityStatement"
        resource.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the CapabilityStatement")
        )

        // CapabilityStatement

        val resource2 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource2)
        resource2.type = "StructureDefinition"
        resource2.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource2.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the StructurDefinition")
        )

        // MessageDefinition

        val resource3 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource3)
        resource3.type = "MessageDefinition"
        resource3.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource3.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the MessageDefintiion")
        )

        // OperationDefinition

        val resource4 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource4)
        resource4.type = "OperationDefinition"
        resource4.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource4.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the OperationDefintiion")
        )

        // SearchParameter

        val resource5 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource5)
        resource5.type = "SearchParameter"
        resource5.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource5.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the SearchParameter")
        )


        for (npmPackage in npmPackages!!) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    CapabilityStatement::class.java
                )) {
                    for (restComponent in resource.rest) {
                        for (component in restComponent.resource) {

                            if (component.hasProfile()) {
                                var resourceComponent = getResourceComponent(component.type, cs)
                                if (resourceComponent == null) {
                                    resourceComponent = CapabilityStatement.CapabilityStatementRestResourceComponent()
                                    rest.resource.add(resourceComponent)
                                }
                                resourceComponent.type = component.type
                                resourceComponent.profile = component.profile

                            }
                        }
                    }
                }

                val message = CapabilityStatement.CapabilityStatementMessagingComponent()

                message.documentation = npmPackage.name()
                for (resource in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    MessageDefinition::class.java
                )) {
                    if (resource.hasUrl()) {
                        val messageDefinition = CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent()
                        .setDefinition(resource.url)

                        message.supportedMessage.add(messageDefinition)
                    }
                }
                if (message.supportedMessage.size>0)  cs.messaging.add(message)
            }
        }


        cs.extension.add(apiextension)
        return cs
    }

    fun getResourceComponent(type : String, cs : CapabilityStatement ) : CapabilityStatement.CapabilityStatementRestResourceComponent? {
        for (rest in cs.rest) {
            for (resource in rest.resource) {
               // println(type + " - " +resource.type)
                if (resource.type.equals(type))
                    return resource
            }
        }
        return null
    }

}
