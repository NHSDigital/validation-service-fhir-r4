package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.IServerConformanceProvider
import ca.uhn.fhir.rest.server.RestfulServer
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import javax.servlet.http.HttpServletRequest

@Component
class metadataProvider(private val fhirContext: FhirContext, private val npmPackages: List<NpmPackage>) :
    IServerConformanceProvider<CapabilityStatement>
{
    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)

    override fun getServerConformance(p0: HttpServletRequest?, p1: RequestDetails?): CapabilityStatement {
        val cs = CapabilityStatement();
        val apiextension = Extension();
        apiextension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Package"
        npmPackages.forEach {
            val packageExtension = Extension();
            packageExtension.url="FHIRPakcage"
            packageExtension.extension.add(Extension().setUrl("name").setValue(StringType(it.name())))
            packageExtension.extension.add(Extension().setUrl("version").setValue(StringType(it.version())))
            apiextension.extension.add(packageExtension)
        }
        val packageExtension = Extension();
        packageExtension.url="openApi"
        packageExtension.extension.add(Extension().setUrl("documentation").setValue(UriType("https://simplifier.net/guide/NHSDigital/Home")))
        packageExtension.extension.add(Extension().setUrl("description").setValue(StringType("NHS Digital FHIR Implementation Guide")))
        apiextension.extension.add(packageExtension)
        cs.status = Enumerations.PublicationStatus.ACTIVE
        cs.kind = CapabilityStatement.CapabilityStatementKind.INSTANCE
        cs.fhirVersion = Enumerations.FHIRVersion._4_0_1

        val rest = CapabilityStatement.CapabilityStatementRestComponent()
        cs.rest.add(rest)
        val operation = CapabilityStatement.CapabilityStatementRestResourceOperationComponent()
        operation.name = "validate"
        operation.definition = "https://fhir.nhs.uk/OperationDefinition/validate"
        rest.operation.add(operation)

        // CapabilityStatement

        var resource = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource)
        resource.type = "CapabilityStatement"
        resource.profile = "https://fhir.nhs.uk/StructureDefinition/NHSDigital-CapabilityStatement"
        resource.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the CapabilityStatement")
        )

        // CapabilityStatement

        val resource2 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource2)
        resource2.type = "StructureDefinition"
        resource2.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource2.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the StructurDefinition")
        )

        // MessageDefinition

        val resource3 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource3)
        resource3.type = "MessageDefinition"
        resource3.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource3.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the MessageDefintiion")
        )

        // OperationDefinition

        val resource4 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource4)
        resource4.type = "OperationDefinition"
        resource4.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource4.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the OperationDefintiion")
        )

        // SearchParameter

        val resource5 = CapabilityStatement.CapabilityStatementRestResourceComponent()
        rest.resource.add(resource5)
        resource5.type = "SearchParameter"
        resource5.interaction.add(CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE))
        resource5.searchParam.add(
            CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent()
                .setName("url")
                .setType(Enumerations.SearchParamType.TOKEN)
                .setDocumentation("The uri that identifies the SearchParameter")
        )


        for (npmPackage in npmPackages) {
            if (!npmPackage.name().equals("hl7.fhir.r4.core")) {
                for (resourceIG in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    CapabilityStatement::class.java
                )) {
                    for (restComponent in resourceIG.rest) {
                        for (component in restComponent.resource) {

                            if (component.hasProfile()) {
                                var resourceComponent = getResourceComponent(component.type, cs)
                                if (resourceComponent == null) {
                                    resourceComponent = CapabilityStatement.CapabilityStatementRestResourceComponent()
                                    rest.resource.add(resourceComponent)
                                }
                                resourceComponent.type = component.type
                                resourceComponent.profile = component.profile

                            }
                        }
                    }
                }

                val message = CapabilityStatement.CapabilityStatementMessagingComponent()

                message.documentation = npmPackage.name()
                for (resourceIG in implementationGuideParser!!.getResourcesOfTypeFromPackage(
                    npmPackage,
                    MessageDefinition::class.java
                )) {
                    if (resourceIG.hasUrl()) {
                        val messageDefinition = CapabilityStatement.CapabilityStatementMessagingSupportedMessageComponent()
                            .setDefinition(resourceIG.url)

                        message.supportedMessage.add(messageDefinition)
                    }
                }
                if (message.supportedMessage.size>0)  cs.messaging.add(message)
            }
        }


        cs.extension.add(apiextension)
        return cs
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

    override fun setRestfulServer(p0: RestfulServer?) {

    }
}
