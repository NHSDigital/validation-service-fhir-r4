package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.util.BundleUtil
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeSystem
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.r4.model.ValueSet
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParserAlt(private val fhirContext: FhirContext) {
    //TODO - using this class would be much nicer than downloading the package files, but it doesn't work as simplifier is not a FHIR R4 server
    // Not sure if there's a way to download R4 resources from a STU3 server using the HAPI FHIR client

    fun <T : IBaseResource> getResourcesOfType(client: IGenericClient, resourceType: Class<T>): List<T> {
        val results = ArrayList<IBaseResource>()

        var bundle = client.search<Bundle>().forResource(resourceType).encodedJson().execute()
        results.addAll(BundleUtil.toListOfResources(fhirContext, bundle))

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = client.loadPage().next(bundle).encodedJson().execute()
            results.addAll(BundleUtil.toListOfResources(fhirContext, bundle))
        }

        return results.filterIsInstance(resourceType)
    }

    fun createPrePopulatedValidationSupport(implementationGuideUrl: String): PrePopulatedValidationSupport {
        val prePopulatedSupport = PrePopulatedValidationSupport(fhirContext)

        val client = fhirContext.newRestfulGenericClient(implementationGuideUrl)
        getResourcesOfType(client, StructureDefinition::class.java).forEach(prePopulatedSupport::addStructureDefinition)
        getResourcesOfType(client, CodeSystem::class.java).forEach(prePopulatedSupport::addCodeSystem)
        getResourcesOfType(client, ValueSet::class.java).forEach(prePopulatedSupport::addValueSet)

        return prePopulatedSupport
    }
}