package software.nhs.fhirvalidator.common.service

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport = PrePopulatedValidationSupport(fhirContext)

        getResourcesOfType(npmPackage, StructureDefinition()).forEach(prePopulatedSupport::addStructureDefinition)
        getResourcesOfType(npmPackage, CodeSystem()).forEach(prePopulatedSupport::addCodeSystem)
        getResourcesOfType(npmPackage, ValueSet()).forEach(prePopulatedSupport::addValueSet)

        return prePopulatedSupport
    }

    fun <T : Resource> getResourcesOfType(npmPackage: NpmPackage, resourceType: T): List<T> {
        val jsonParser = fhirContext.newJsonParser()
        return npmPackage.listResources(resourceType.fhirType())
            .map(npmPackage::loadResource)
            .map(jsonParser::parseResource)
            .filterIsInstance(resourceType.javaClass)
    }
}
