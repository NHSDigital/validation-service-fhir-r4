package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.fhirvalidator.interceptor.CapabilityStatementInterceptorR4B
import uk.nhs.nhsdigital.fhirvalidator.interceptor.CapabilityStatementInterceptorR5
import uk.nhs.nhsdigital.fhirvalidator.providerR5.CodeSystemProviderR5
import uk.nhs.nhsdigital.fhirvalidator.providerR5.ValueSetProviderR5
import java.util.*

// disabled @WebServlet("/FHIR/R4B/*", loadOnStartup = 1)
class FHIRR4BRestfulServer(
    @Qualifier("R4B") fhirContext: FhirContext,

    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))


        registerInterceptor(CapabilityStatementInterceptorR4B())

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
