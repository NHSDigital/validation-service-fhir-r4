package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import uk.nhs.nhsdigital.fhirvalidator.shared.PrePopulatedValidationSupport
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport =
            PrePopulatedValidationSupport(fhirContext)
        getResourcesFromPackage(npmPackage).forEach(prePopulatedSupport::addResource)
        return prePopulatedSupport
    }

    fun <T : Resource> getResourcesOfTypeFromPackage(npmPackage: NpmPackage, resourceType: Class<T>): List<T> {
        return getResourcesFromPackage(npmPackage).filterIsInstance(resourceType)
    }

    fun <T : Resource> getResourcesOfType(npmPackages: List<NpmPackage>?, resourceType: Class<T>): List<T> {
        var list : ArrayList<T> = ArrayList<T>()
        if (npmPackages != null) {
            for (npmPackage in npmPackages) {
                val result = getResourcesOfTypeFromPackage(npmPackage,resourceType)
                if (result.size>0) list.addAll(result)
            }
        }
        return list
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
