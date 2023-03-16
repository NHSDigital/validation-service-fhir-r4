package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.parser.LenientErrorHandler
import uk.nhs.nhsdigital.fhirvalidator.shared.PrePopulatedValidationSupport
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.nhs.nhsdigital.fhirvalidator.configuration.ValidationConfiguration
import java.io.InputStream

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
        val jsonParser = fhirContext.newJsonParser().setParserErrorHandler(LenientErrorHandler())
        val list = npmPackage.list(folderName).map {
            //println(cnt.toString() + " " + it)
            //cnt++
            npmPackage.load(folderName, it)
        }
        ValidationConfiguration.logger.info("Package {} - {} Folder {}",npmPackage.name(),npmPackage.version(),folderName)
        return list
            .map {

                parseResource(jsonParser,it)
            }
    }
    fun parseResource(jsonParser: IParser, it : InputStream?): IBaseResource {
        try {
            return jsonParser.parseResource(it)
        } catch (ex: Exception) {
            ValidationConfiguration.logger.error(ex.message)
        }
        // Should not get here
        return Parameters()
    }

}
