package com.example.fhirvalidator.provider

import ca.uhn.fhir.rest.annotation.Metadata
import ca.uhn.fhir.rest.api.server.RequestDetails
import org.hl7.fhir.r4.hapi.rest.server.ServerCapabilityStatementProvider
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
class CapabilityStatementProvider(
    private val npmPackages: List<NpmPackage>
) : ServerCapabilityStatementProvider() {

    @Metadata
    override fun getServerConformance(
        servletRequest: HttpServletRequest,
        requestDetails: RequestDetails
    ): CapabilityStatement {

        val capabilityStatement = super.getServerConformance(servletRequest, requestDetails)
        capabilityStatement.publisher = "NHS Digital"
        val apiExtension = capabilityStatement.addExtension()
        apiExtension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition"

        npmPackages.forEach {
            val implementationGuideExtension = apiExtension.addExtension().setUrl("implementationGuide")
            implementationGuideExtension.addExtension().setUrl("version").setValue(StringType(it.version()))
            implementationGuideExtension.addExtension().setUrl("name").setValue(StringType(it.name()))
        }

        return capabilityStatement
    }
}
