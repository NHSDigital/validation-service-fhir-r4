package com.example.fhirvalidator.server

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.example.fhirvalidator.provider.CapabilityStatementProvider
import com.example.fhirvalidator.provider.ValidateProvider
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/R4/*")
class FHIRRestfulServer(
    fhirContext: FhirContext,
    private val validateProvider: ValidateProvider,
    private val capabilityStatementProvider: CapabilityStatementProvider
) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(validateProvider)

        serverConformanceProvider = capabilityStatementProvider

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
