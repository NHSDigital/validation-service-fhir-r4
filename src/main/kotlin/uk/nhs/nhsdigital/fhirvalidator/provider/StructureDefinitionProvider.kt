package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.OptionalParam
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import ca.uhn.fhir.validation.FhirValidator
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

@Component
class StructureDefinitionProvider (
    @Qualifier("R4") private val fhirContext: FhirContext,
    private val supportChain : ValidationSupportChain) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<StructureDefinition> {
        return StructureDefinition::class.java
    }

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)


    @Search
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = StructureDefinition.SP_URL) url: TokenParam?,
        @OptionalParam(name = StructureDefinition.SP_NAME) name: StringParam?,
        @OptionalParam(name = StructureDefinition.SP_BASE) base: TokenParam?,
        @OptionalParam(name = StructureDefinition.SP_TYPE) type: UriParam?,
        @OptionalParam(name = StructureDefinition.SP_EXT_CONTEXT) contextType: UriParam?
    ): List<StructureDefinition> {
        val params = httpRequest.parameterMap
        System.out.println(params.size)
        if (params.size == 0) throw UnprocessableEntityException("One parameter must be supplied.")
        if (params.size != 1) throw UnprocessableEntityException("Only one parameter can be supplied.")
        val list = mutableListOf<StructureDefinition>()
        for (resource in supportChain.fetchAllStructureDefinitions()) {
            var structureDefinition = resource as StructureDefinition
            if (
                ((url != null)
                        && structureDefinition.url.equals(
                    URLDecoder.decode(
                        url.value,
                        StandardCharsets.UTF_8.name()
                    )
                )) ||
                ((type != null)
                        && structureDefinition.type.equals(
                    URLDecoder.decode(
                        type.value,
                        StandardCharsets.UTF_8.name()
                    )
                )) ||
                ((base != null)
                        && structureDefinition.hasBaseDefinition()
                        && structureDefinition.baseDefinition.equals(
                    URLDecoder.decode(
                        base.value,
                        StandardCharsets.UTF_8.name()
                    )
                )) ||
                ((name != null) && (structureDefinition.name.contains(name.value)
                        ||
                        ((structureDefinition.title != null) && structureDefinition.title.contains(name.value))))
            ) {
                if (structureDefinition.id == null) structureDefinition.id = structureDefinition.name
                if (url == null) {
                    // dirty clone
                    structureDefinition = fhirContext.newJsonParser().parseResource(
                        fhirContext.newJsonParser().encodeResourceToString(structureDefinition)) as StructureDefinition
                    structureDefinition.snapshot = null
                }
                list.add(structureDefinition)
            }
            if (contextType!= null && structureDefinition.hasContext() ) {
                for (context in structureDefinition.context) {
                    if (context.hasType() && context.expressionElement.value.equals(contextType.value))  {
                        if (structureDefinition.id == null) structureDefinition.id = structureDefinition.name
                        if (url == null) {
                            // dirty clone
                            structureDefinition = fhirContext.newJsonParser().parseResource(
                                fhirContext.newJsonParser().encodeResourceToString(structureDefinition)) as StructureDefinition
                            structureDefinition.snapshot = null
                        }
                        list.add(structureDefinition)
                    }
                }
            }
        }
        return list
    }
}
