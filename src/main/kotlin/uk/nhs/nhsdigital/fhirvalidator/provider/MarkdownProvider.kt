package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.OperationParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.validation.FhirValidator
import mu.KLogging
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.CapabilityStatementApplier
import uk.nhs.nhsdigital.fhirvalidator.service.MessageDefinitionApplier
import uk.nhs.nhsdigital.fhirvalidator.service.OpenAPIParser
import uk.nhs.nhsdigital.fhirvalidator.service.VerifyOAS
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class MarkdownProvider (
                        private val oasParser : OpenAPIParser
) {
    companion object : KLogging()

    @Operation(name = "\$markdown", idempotent = true,manualResponse=true, manualRequest=true)
    @Throws(Exception::class)
    fun createProfileMarkdown(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        @OperationParam(name="url") profile: StringParam?
    ) {
        servletResponse.setContentType("text/markdown")
        servletResponse.setCharacterEncoding("UTF-8")
        if (profile != null) {
            servletResponse.writer.write(oasParser.generateMarkdown(java.net.URLDecoder.decode(profile.value, StandardCharsets.UTF_8.name())))
        }
        servletResponse.writer.flush()
        return
    }

}
