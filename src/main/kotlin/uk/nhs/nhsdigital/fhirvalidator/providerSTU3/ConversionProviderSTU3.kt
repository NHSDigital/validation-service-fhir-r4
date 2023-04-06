package uk.nhs.nhsdigital.fhirvalidator.providerSTU3

import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Validate
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationOptions
import mu.KLogging
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_30_40
import org.hl7.fhir.convertors.conv30_40.VersionConvertor_30_40
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.dstu3.model.Bundle
import org.hl7.fhir.dstu3.model.OperationOutcome
import org.hl7.fhir.dstu3.model.Resource
import org.hl7.fhir.dstu3.model.ResourceType
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.util.createSTU3OperationOutcome
import javax.servlet.http.HttpServletRequest

@Component
class ConversionProviderSTU3 () {
    companion object : KLogging()

    @Operation(name = "\$convert", idempotent = true)
    @Throws(Exception::class)
    fun convertJson(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        return resource
    }

    @Operation(name = "\$convertR4", idempotent = true)
    @Throws(java.lang.Exception::class)
    fun convertR4(
        @ResourceParam resource: IBaseResource?
    ): IBaseResource? {
        val convertor = VersionConvertor_30_40(BaseAdvisor_30_40())
        val resourceR3 = resource as Resource
        return convertor.convertResource(resourceR3)
    }

}
