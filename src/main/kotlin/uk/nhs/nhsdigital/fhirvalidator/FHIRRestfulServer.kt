package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
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
    private val metadataProvider: metadataProvider,
    private val structureMapProvider: StructureMapProvider,
    private val conceptMapProvider: ConceptMapProvider,
    private val namingSystemProvider: NamingSystemProvider
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

        metadataProvider.setRestfulServer(this)
       // todo serverConformanceProvider = metadataProvider

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
