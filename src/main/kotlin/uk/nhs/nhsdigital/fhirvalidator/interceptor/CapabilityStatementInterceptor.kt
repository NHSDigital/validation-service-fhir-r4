package uk.nhs.nhsdigital.fhirvalidator.interceptor

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import org.hl7.fhir.instance.model.api.IBaseConformance
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import uk.nhs.nhsdigital.fhirvalidator.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser

@Interceptor
class CapabilityStatementInterceptor(
    fhirContext: FhirContext,
    private val npmPackages: List<NpmPackage>,
    private val supportChain: IValidationSupport,
    private val fhirServerProperties: FHIRServerProperties
) {

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)

    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    fun customize(theCapabilityStatement: IBaseConformance) {

        // Cast to the appropriate version
        val cs: CapabilityStatement = theCapabilityStatement as CapabilityStatement

        // Customize the CapabilityStatement as desired
        val apiextension = Extension();
        apiextension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Package"
        npmPackages.forEach {
            val packageExtension = Extension();
            packageExtension.url="FHIRPackage"
            packageExtension.extension.add(Extension().setUrl("name").setValue(StringType(it.name())))
            packageExtension.extension.add(Extension().setUrl("version").setValue(StringType(it.version())))
            apiextension.extension.add(packageExtension)
        }
        val packageExtension = Extension();
        packageExtension.url="openApi"
        packageExtension.extension.add(Extension().setUrl("documentation").setValue(UriType("https://simplifier.net/guide/NHSDigital/Home")))
        packageExtension.extension.add(Extension().setUrl("description").setValue(StringType("NHS Digital FHIR Implementation Guide")))
        apiextension.extension.add(packageExtension)
        cs.extension.add(apiextension)

        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resourceIG in supportChain.fetchAllConformanceResources()?.filterIsInstance<CapabilityStatement>()!!) {
                    for (restComponent in resourceIG.rest) {
                        for (component in restComponent.resource) {

                            if (component.hasProfile()) {
                                var resourceComponent = getResourceComponent(component.type, cs)
                                if (resourceComponent != null) {
                                    resourceComponent.type = component.type
                                    resourceComponent.profile = component.profile
                                }

                            }
                        }
                    }
                }
                val message = CapabilityStatement.CapabilityStatementMessagingComponent()
                message.documentation = npmPackage.name()
                for (resourceIG in supportChain.fetchAllConformanceResources()?.filterIsInstance<MessageDefinition>()!!) {
                    if (resourceIG.hasUrl()) {
                        val messageDefinition = CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent()
                            .setDefinition(resourceIG.url)

                        message.supportedMessage.add(messageDefinition)
                    }
                }
                if (message.supportedMessage.size>0)  cs.messaging.add(message)
            }
        }
        for (ops in cs.restFirstRep.operation) {
            val operation = getOperationDefinition(ops.name)
            if (operation !=null) {
                ops.definition = operation.url
            }
        }
        cs.name = fhirServerProperties.server.name
        cs.software.name = fhirServerProperties.server.name
        cs.software.version = fhirServerProperties.server.version
        cs.publisher = "NHS Digital"
        cs.implementation.url = "https://simplifier.net/guide/nhsdigital"
        cs.implementation.description = "NHS Digital FHIR Implementation Guide"
    }

    fun getResourceComponent(type : String, cs : CapabilityStatement ) : CapabilityStatement.CapabilityStatementRestResourceComponent? {
        for (rest in cs.rest) {
            for (resource in rest.resource) {
                // println(type + " - " +resource.type)
                if (resource.type.equals(type))
                    return resource
            }
        }
        return null
    }

    fun getOperationDefinition(operationCode : String) : OperationDefinition? {
        val operation= operationCode.removePrefix("$")
        for (resource in supportChain.fetchAllConformanceResources()!!) {
            if (resource is OperationDefinition) {
                if (resource.code.equals(operation)) {
                    return resource
                }
            }
        }
        return null
    }
}
