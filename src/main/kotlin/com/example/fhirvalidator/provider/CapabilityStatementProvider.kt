package com.example.fhirvalidator.provider

import ca.uhn.fhir.rest.annotation.Metadata
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.RestfulServer
import com.example.fhirvalidator.service.ImplementationGuideParser
import com.example.fhirvalidator.util.applyProfile
import com.example.fhirvalidator.util.getResourcesOfType
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.hapi.rest.server.ServerCapabilityStatementProvider
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.utilities.cache.NpmPackage
import javax.servlet.http.HttpServletRequest

class CapabilityStatementProvider(restfulServer: RestfulServer,
                                  npmPackages: List<NpmPackage>) :
    ServerCapabilityStatementProvider() {

    init {
        super.setRestfulServer(restfulServer)
    }
    var capabilityStatement = CapabilityStatement()
    var npmPackages = npmPackages

    @Metadata
    override fun getServerConformance(
        theRequest: HttpServletRequest,
        theRequestDetails: RequestDetails
    ): CapabilityStatement {

        capabilityStatement = super.getServerConformance(theRequest, theRequestDetails)
        capabilityStatement.publisher = "NHS Digital"
        var apiExtension = capabilityStatement.addExtension();
        apiExtension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-APIDefinition";

        npmPackages.forEach{
            var implementationGuideExtension = apiExtension.addExtension().setUrl("implementationGuide")
            implementationGuideExtension.addExtension().setUrl("version").setValue(StringType(it.version()))
            implementationGuideExtension.addExtension().setUrl("name").setValue(StringType(it.name()))
        }

        // process capabilityStatements from IG

        return  capabilityStatement
    }
}
