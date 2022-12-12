package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import uk.nhs.nhsdigital.fhirvalidator.shared.PrePopulatedValidationSupport
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(@Qualifier("R4") private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport =
            PrePopulatedValidationSupport(fhirContext)
        getResourcesFromPackage(npmPackage).forEach(prePopulatedSupport::addResource)
        return prePopulatedSupport
    }

    fun getResourcesFromPackage(npmPackage: NpmPackage): List<IBaseResource> {
        return getResourcesFromFolder(npmPackage, "package")
            .plus(getResourcesFromFolder(npmPackage, "examples"))
    }

    fun getResourcesFromFolder(npmPackage: NpmPackage, folderName: String): List<IBaseResource> {
        val jsonParser = fhirContext.newJsonParser()
        var cnt : Int = 0
        val list = npmPackage.list(folderName).map {
            //println(cnt.toString() + " " + it)
            //cnt++
            npmPackage.load(folderName, it)
        }
        cnt = 0
        return list
            .map {
              //  println(cnt)
              //  cnt++
                jsonParser.parseResource(it)
            }
    }

}
