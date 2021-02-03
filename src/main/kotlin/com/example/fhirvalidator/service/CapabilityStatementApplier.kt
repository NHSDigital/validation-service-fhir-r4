package com.example.fhirvalidator.service

import com.example.fhirvalidator.util.applyProfile
import com.example.fhirvalidator.util.getResourcesOfType
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Service

@Service
class CapabilityStatementApplier(
    implementationGuideParser: ImplementationGuideParser,
    npmPackages: List<NpmPackage>
) {
    private val restResources = npmPackages
        .flatMap { implementationGuideParser.getResourcesOfType(it, CapabilityStatement()) }
        .flatMap { it.rest }
        .flatMap { it.resource }

    fun applyCapabilityStatementProfiles(resource: IBaseResource) {
        restResources.forEach { applyRestResource(resource, it) }
    }

    private fun applyRestResource(
        resource: IBaseResource,
        restResource: CapabilityStatement.CapabilityStatementRestResourceComponent
    ) {
        val matchingResources = getResourcesOfType(resource, restResource.type)
        if (restResource.hasProfile()) {
            applyProfile(matchingResources, restResource.profileElement)
        }
    }
}