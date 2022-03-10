package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.Operation
import io.swagger.v3.core.util.Json
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CapabilityStatement
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.OpenAPIParser
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@Component
class OpenAPIProvider(private val fhirContext: FhirContext,
                      private val oasParser : OpenAPIParser
) {
    @Operation(name = "openapi", idempotent = true,manualResponse=true, manualRequest=true)
    fun convertOpenAPI(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse
    ) {

        var input = IOUtils.toString(servletRequest.getReader());
        var inputResource : IBaseResource
        servletResponse.setContentType("application/json")
        servletResponse.setCharacterEncoding("UTF-8")
        try {
            inputResource = fhirContext.newJsonParser().parseResource(input)
        } catch (ex : Exception) {
            inputResource = fhirContext.newXmlParser().parseResource(input)
        }
        if (inputResource is CapabilityStatement) {
            val cs : CapabilityStatement = inputResource

            servletResponse.writer.write(Json.pretty(oasParser.generateOpenApi(cs)))
            servletResponse.writer.flush()
            return
        }
        servletResponse.writer.write("{}")
        servletResponse.writer.flush()
        return
    }


}
