package com.example.fhirvalidator.server

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.example.fhirvalidator.provider.CapabilityStatementProvider
import com.example.fhirvalidator.provider.ValidateProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class FHIRRestfulServer(
    fhirContext: FhirContext,
    validateProvider: ValidateProvider,
    capabilityStatementProvider: CapabilityStatementProvider
) : RestfulServer(fhirContext) {

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(validateProvider)

        serverConformanceProvider = capabilityStatementProvider

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
