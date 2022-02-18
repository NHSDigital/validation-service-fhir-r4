package uk.nhs.nhsdigital.fhirvalidator.service

import uk.nhs.nhsdigital.fhirvalidator.util.applyProfile
import uk.nhs.nhsdigital.fhirvalidator.util.getResourcesOfType
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Service

@Service
class CapabilityStatementApplier(
    implementationGuideParser: ImplementationGuideParser,
    npmPackages: List<NpmPackage>
) {
    private val restResources = npmPackages
        .flatMap { implementationGuideParser.getResourcesOfTypeFromPackage(it, CapabilityStatement::class.java) }
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