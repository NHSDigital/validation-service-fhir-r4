package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.fhirvalidator.providerR5.CapabilityStatementInterceptorR4B
import uk.nhs.nhsdigital.fhirvalidator.providerR5.MedicinalProductDefinitionProviderR4B
import uk.nhs.nhsdigital.fhirvalidator.providerR5.PackagedProductDefinitionProviderR4B
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R5/*", loadOnStartup = 1)
class FHIRR5RestfulServer(
    @Qualifier("R5") fhirContext: FhirContext,
    private val medicinalProductDefinitionProviderR4B: MedicinalProductDefinitionProviderR4B,
    private val packagedProductDefinitionProviderR4B: PackagedProductDefinitionProviderR4B
    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(medicinalProductDefinitionProviderR4B)
        registerProvider(packagedProductDefinitionProviderR4B)

        registerInterceptor(CapabilityStatementInterceptorR4B())

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
