package uk.nhs.nhsdigital.fhirvalidator.service

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import uk.nhs.nhsdigital.fhirvalidator.util.applyProfile
import uk.nhs.nhsdigital.fhirvalidator.util.getResourcesOfType
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Service

@Service
class CapabilityStatementApplier(
    val supportChain: ValidationSupportChain
) {
    private val restResources = supportChain.fetchAllConformanceResources()?.filterIsInstance(CapabilityStatement::class.java)
        ?.flatMap { it.rest }
        ?.flatMap { it.resource }

    fun applyCapabilityStatementProfiles(resource: IBaseResource) {
        restResources?.forEach { applyRestResource(resource, it) }
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
