package com.example.fhirvalidator.service

import com.example.fhirvalidator.util.applyProfile
import com.example.fhirvalidator.util.getResourcesOfType
import jakarta.annotation.Resource
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

open class BaseCapabilityStatementApplier(
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

@Service
class CapabilityStatementApplier (
    implementationGuideParser: ImplementationGuideParser,
    @Autowired
    @Qualifier("npmPackages")
    npmPackages: List<NpmPackage>
) : BaseCapabilityStatementApplier(implementationGuideParser, npmPackages) {

}

@Service
class CapabilityStatementApplierNext (
    implementationGuideParser: ImplementationGuideParser,
    @Autowired
    @Qualifier("npmPackagesNext")
    npmPackagesNext: List<NpmPackage>
) : BaseCapabilityStatementApplier(implementationGuideParser, npmPackagesNext) {

}
