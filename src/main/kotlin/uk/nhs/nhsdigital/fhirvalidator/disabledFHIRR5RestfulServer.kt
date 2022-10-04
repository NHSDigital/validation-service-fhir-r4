package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.fhirvalidator.interceptor.CapabilityStatementInterceptorR5
import uk.nhs.nhsdigital.fhirvalidator.providerR5.CodeSystemProviderR5
import uk.nhs.nhsdigital.fhirvalidator.providerR5.ValueSetProviderR5
import java.util.*

// disabled @WebServlet("/FHIR/R5/*", loadOnStartup = 1)
class disabledFHIRR5RestfulServer(
    @Qualifier("R5") fhirContext: FhirContext,
    private val valueSetProviderR5: ValueSetProviderR5,
    private val codeSystemProviderR5: CodeSystemProviderR5

    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(valueSetProviderR5)
        registerProvider(codeSystemProviderR5)

        registerInterceptor(CapabilityStatementInterceptorR5())

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
