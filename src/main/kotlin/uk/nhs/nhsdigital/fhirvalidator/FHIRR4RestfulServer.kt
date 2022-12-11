package uk.nhs.nhsdigital.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.fhirvalidator.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.fhirvalidator.interceptor.AWSAuditEventLoggingInterceptor
import uk.nhs.nhsdigital.fhirvalidator.interceptor.CapabilityStatementInterceptor
import uk.nhs.nhsdigital.fhirvalidator.provider.*
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    private val validateR4Provider: ValidateR4Provider,
    private val openAPIProvider: OpenAPIProvider,
    private val markdownProvider: MarkdownProvider,
    private val capabilityStatementProvider: CapabilityStatementProvider,
    private val messageDefinitionProvider: MessageDefinitionProvider,
    private val structureDefinitionProvider: StructureDefinitionProvider,
    private val operationDefinitionProvider: OperationDefinitionProvider,
    private val searchParameterProvider: SearchParameterProvider,
    private val structureMapProvider: StructureMapProvider,
    private val conceptMapProvider: ConceptMapProvider,
    private val namingSystemProvider: NamingSystemProvider,
    private val valueSetProvider: ValueSetProvider,
    private val codeSystemProvider: CodeSystemProvider,
    private val chatGPProvider: FHIRtoTextProvider,

    private val npmPackages: List<NpmPackage>,
    @Qualifier("SupportChain") private val supportChain: IValidationSupport,
    public val fhirServerProperties: FHIRServerProperties
) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(validateR4Provider)
        registerProvider(openAPIProvider)
        registerProvider(markdownProvider)
        registerProvider(capabilityStatementProvider)
        registerProvider(messageDefinitionProvider)
        registerProvider(structureDefinitionProvider)
        registerProvider(operationDefinitionProvider)
        registerProvider(searchParameterProvider)
        registerProvider(structureMapProvider)
        registerProvider(conceptMapProvider)
        registerProvider(namingSystemProvider)
        registerProvider(valueSetProvider)
        registerProvider(codeSystemProvider)
        registerProvider(chatGPProvider)

        registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,npmPackages, supportChain, fhirServerProperties))


        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)


        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
