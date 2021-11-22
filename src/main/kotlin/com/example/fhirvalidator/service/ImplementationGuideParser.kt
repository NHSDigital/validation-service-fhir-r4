package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.hl7.fhir.instance.model.api.IBaseResource
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport = PrePopulatedValidationSupport(fhirContext)
        getResourcesFromPackage(npmPackage).forEach(prePopulatedSupport::addResource)
        return prePopulatedSupport
    }

    fun <T : Resource> getResourcesOfTypeFromPackage(npmPackage: NpmPackage, resourceType: Class<T>): List<T> {
        return getResourcesFromPackage(npmPackage).filterIsInstance(resourceType)
    }

    fun getResourcesFromPackage(npmPackage: NpmPackage): List<IBaseResource> {
        return getResourcesFromFolder(npmPackage, "package")
            .plus(getResourcesFromFolder(npmPackage, "examples"))
    }

    fun getResourcesFromFolder(npmPackage: NpmPackage, folderName: String): List<IBaseResource> {
        val jsonParser = fhirContext.newJsonParser()
        return npmPackage.list(folderName)
            .map { npmPackage.load(folderName, it) }
            .map(jsonParser::parseResource)
    }
}