package uk.nhs.nhsdigital.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.OperationParam
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.TokenParam
import uk.nhs.nhsdigital.fhirvalidator.service.VerifyOAS
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import mu.KLogging
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r5.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
class TerminologyServerFacade(
    @Qualifier("R5") private val fhirContext: FhirContext
) {
    companion object : KLogging()

    @GetMapping("/Terminology/ValueSet/\$validate-code", produces = ["application/json", "application/fhir+json"])
    fun validateCode(@RequestParam(name = "url") url: String?,
                     @RequestParam(name = "context") context: String?,
                     @ResourceParam valueSet: ValueSet?,
                     @RequestParam(name = "valueSetVersion") valueSetVersion: String?,
                     @RequestParam(name = "code") code: String?,
                     @RequestParam(name = "system") system: String?,
                     @RequestParam(name = "systemVersion") systemVersion: String?,
                     @RequestParam(name = "display") display: String?,
                     @RequestParam(name = "coding") coding: TokenParam?,
                     @RequestParam(name = "codeableConcept") codeableConcept: CodeableConcept?,
                     @RequestParam(name = "date") date: DateParam?,
                     @RequestParam(name = "abstract") abstract: BooleanType?,
                     @RequestParam(name = "displayLanguage") displayLanguage: CodeType?) :String? {
        return  fhirContext.newJsonParser().encodeResourceToString(org.hl7.fhir.r5.model.OperationOutcome())
    }

    @GetMapping("/Terminology/metadata", produces = ["application/json", "application/fhir+json"])
    fun validate(

        @RequestParam(required = false) mode: String?,
        @RequestParam(required = false) _summmary: String?
    ): String? {
        if (mode != null && mode.equals("terminology")) {

            val tc = TerminologyCapabilities()
            tc.kind = Enumerations.CapabilityStatementKind.INSTANCE
            tc.status = Enumerations.PublicationStatus.DRAFT
            tc.date = Date()
            tc.codeSystem.add(TerminologyCapabilities.TerminologyCapabilitiesCodeSystemComponent().setUri("http://snomed.info/sct"))
            tc.codeSystem.add(TerminologyCapabilities.TerminologyCapabilitiesCodeSystemComponent().setUri("http://snomed.info/sct"))
            tc.validateCode.translations=false
            tc.expansion.hierarchical=false
            tc.expansion.incomplete=false
            return  fhirContext.newJsonParser().encodeResourceToString(tc)


        }
        val cs = CapabilityStatement()
        cs.fhirVersion = Enumerations.FHIRVersion._4_0_1
        cs.status= Enumerations.PublicationStatus.DRAFT
        cs.date = Date()
        cs.kind = Enumerations.CapabilityStatementKind.INSTANCE
        val codeType = CodeType()
        codeType.setValue("application/fhir+json")
        cs.format.add(codeType);

        var rest = cs.restFirstRep
        rest.mode = Enumerations.RestfulCapabilityMode.SERVER
        rest.resource.add(CapabilityStatement.CapabilityStatementRestResourceComponent().setType("ValueSet")
            .addOperation(CapabilityStatement.CapabilityStatementRestResourceOperationComponent().setName("validate-code")))

        rest.resource.add(CapabilityStatement.CapabilityStatementRestResourceComponent().setType("CodeSystem")
            .addOperation(CapabilityStatement.CapabilityStatementRestResourceOperationComponent().setName("lookup")))
        return  fhirContext.newJsonParser().encodeResourceToString(cs)
    }

}
