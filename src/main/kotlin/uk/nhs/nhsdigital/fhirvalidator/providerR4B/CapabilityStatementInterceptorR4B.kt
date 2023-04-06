package uk.nhs.nhsdigital.fhirvalidator.providerR4B

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import ca.uhn.fhir.rest.api.server.RequestDetails
import mu.KLogging
import org.hl7.fhir.instance.model.api.IBaseConformance
import org.hl7.fhir.r4b.model.*

@Interceptor
class CapabilityStatementInterceptorR4B() {

    companion object : KLogging()
    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    fun customize(theCapabilityStatement: IBaseConformance, requestDetails: RequestDetails) {

        if (requestDetails.parameters != null && requestDetails.parameters.get("mode") != null) {
            logger.info("mode="+requestDetails.parameters.get("mode"))
        }
        // Cast to the appropriate version
        val cs: CapabilityStatement = theCapabilityStatement as CapabilityStatement

        logger.info(requestDetails.requestId)

    }


}
