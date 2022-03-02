package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.fhirvalidator.interceptor.CapabilityStatementInterceptor
import uk.nhs.nhsdigital.fhirvalidator.provider.*
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRRestfulServer(
    fhirContext: FhirContext,
    private val validateProvider: ValidateProvider,
    private val openAPIProvider: OpenAPIProvider,
    private val capabilityStatementProvider: CapabilityStatementProvider,
    private val messageDefinitionProvider: MessageDefinitionProvider,
    private val structureDefinitionProvider: StructureDefinitionProvider,
    private val operationDefinitionProvider: OperationDefinitionProvider,
    private val searchParameterProvider: SearchParameterProvider,
    private val structureMapProvider: StructureMapProvider,
    private val conceptMapProvider: ConceptMapProvider,
    private val namingSystemProvider: NamingSystemProvider,

    private val npmPackages: List<NpmPackage>,
    @Qualifier("SupportChain") private val supportChain: IValidationSupport

    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(validateProvider)
        registerProvider(openAPIProvider)
        registerProvider(capabilityStatementProvider)
        registerProvider(messageDefinitionProvider)
        registerProvider(structureDefinitionProvider)
        registerProvider(operationDefinitionProvider)
        registerProvider(searchParameterProvider)
        registerProvider(structureMapProvider)
        registerProvider(conceptMapProvider)
        registerProvider(namingSystemProvider)

        registerInterceptor(CapabilityStatementInterceptor(fhirContext,npmPackages, supportChain))

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
