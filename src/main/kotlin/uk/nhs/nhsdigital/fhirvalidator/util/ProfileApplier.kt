package uk.nhs.nhsdigital.fhirvalidator.util

import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.instance.model.api.IPrimitiveType
import org.hl7.fhir.r4.model.Bundle

fun getResourcesOfType(resource: IBaseResource, resourceType: String?): List<IBaseResource> {
    val matchingResources = mutableListOf<IBaseResource>()
    if (resource.fhirType() == resourceType) {
        matchingResources.add(resource)
    }
    if (resource is Bundle) {
        resource.entry.stream()
            .map { it.resource }
            .filter { it.fhirType() == resourceType }
            .forEach { matchingResources.add(it) }
    }
    return matchingResources
}

fun applyProfile(resources: List<IBaseResource>, profile: IPrimitiveType<String>) {
    resources.stream().forEach {
        it.meta.profile.clear()
        it.meta.addProfile(profile.value)
    }
}
