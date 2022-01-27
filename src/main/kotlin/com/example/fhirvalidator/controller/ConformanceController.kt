package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import com.example.fhirvalidator.service.OpenAPIParser
import io.swagger.v3.core.util.Json
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ConformanceController(
    private val fhirContext: FhirContext,
    private val npmPackages: List<NpmPackage>
) {
    private val openapi = OpenAPIParser(
        fhirContext,
        npmPackages
    )

    @GetMapping("metadata",produces = ["application/json", "application/fhir+json"])
    fun metadata(): String {
        val cs = getCapabilityStatement()
        return fhirContext.newJsonParser().encodeResourceToString(cs);
    }

    @GetMapping("openapi.json",produces = ["application/json"])
    fun openapi(): String {
        val cs = getCapabilityStatement()

        return Json.pretty(openapi.generateOpenApi(cs));
    }

    @GetMapping("CapabilityStatement",produces = ["application/json", "application/fhir+json"])
    fun capabilityStatement(): String {
        val bundle = Bundle();
        bundle.type = Bundle.BundleType.SEARCHSET;
        bundle.entry.add(Bundle.BundleEntryComponent().setResource(getCapabilityStatement()))

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
        cs.status = Enumerations.PublicationStatus.ACTIVE
        cs.kind = CapabilityStatement.CapabilityStatementKind.INSTANCE
        cs.fhirVersion = Enumerations.FHIRVersion._4_0_1

        val rest = CapabilityStatement.CapabilityStatementRestComponent()
        cs.rest.add(rest)
        val operation = CapabilityStatement.CapabilityStatementRestResourceOperationComponent()
        operation.name = "validate"
        operation.definition = "https://fhir.nhs.uk/OperationDefinition/validation"
        rest.operation.add(operation)

        val resource = CapabilityStatement.CapabilityStatementRestResourceComponent()
        resource.type = "CapabilityStatement"
        resource.profile = "https://fhir.nhs.uk/StructureDefinition/NHSDigital-CapabilityStatement"
        resource.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the capability statement")
        )

        rest.resource.add(resource)



        cs.extension.add(apiextension)
        return cs
    }
}
