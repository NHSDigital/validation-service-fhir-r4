package uk.nhs.nhsdigital.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.annotations.Hidden
import uk.nhs.nhsdigital.fhirvalidator.service.VerifyOAS
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import mu.KLogging
import org.hl7.fhir.r4.model.OperationOutcome
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import uk.nhs.nhsdigital.fhirvalidator.service.OpenAPIParser
import java.util.*


@RestController
@Hidden
class VerifyController(
    @Qualifier("R4") private val fhirContext: FhirContext,
    private val verifyOAS:VerifyOAS,
    private val oasParser : OpenAPIParser

) {
    companion object : KLogging()


    @PostMapping("convertOAS",produces = ["application/json"])
    fun convert(
        @RequestBody input: Optional<String>,
        @RequestParam(required = false) url: String?
    ): String {
        var openAPI : OpenAPI? = null
        openAPI = OpenAPIV3Parser().readContents(input.get()).openAPI
        return Json.pretty(openAPI)
    }
    @PostMapping("/\$verifyOAS", produces = ["application/json", "application/x-yaml"])
    fun validate(
        @RequestBody input: Optional<String>,
        @RequestParam(required = false) url: String?
    ): String {
        var openAPI : OpenAPI? = null
        if (url != null) {
            val parseOptions = ParseOptions()
            parseOptions.isResolve = true // implicit
          //  parseOptions.isResolveFully = true
            openAPI = OpenAPIV3Parser().readLocation(url,null,parseOptions).openAPI
        }
        else {
            if (input.isPresent) {
                openAPI = OpenAPIV3Parser().readContents(input.get()).openAPI
            } else {
                return  fhirContext.newJsonParser().encodeResourceToString(OperationOutcome()
                    .addIssue(OperationOutcome.OperationOutcomeIssueComponent()
                    .setSeverity(OperationOutcome.IssueSeverity.FATAL)
                        .setDiagnostics("If url is not provided, the OAS must be present in the payload")))
            }
        }

        if (openAPI !=null) {
            val results = verifyOAS.validate(openAPI)
            return  fhirContext.newJsonParser().encodeResourceToString(createOperationOutcome(results))
        }

        return  fhirContext.newJsonParser().encodeResourceToString(OperationOutcome().addIssue(OperationOutcome.OperationOutcomeIssueComponent()
            .setSeverity(OperationOutcome.IssueSeverity.FATAL).setDiagnostics("Unable to process OAS")))
    }

}
