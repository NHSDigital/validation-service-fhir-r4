package uk.nhs.nhsdigital.fhirvalidator.interceptor

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import ca.uhn.fhir.rest.api.Constants
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails
import org.apache.commons.lang3.StringUtils
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import uk.nhs.nhsdigital.fhirvalidator.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems
import java.io.IOException
import java.util.*
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Interceptor
class AWSAuditEventLoggingInterceptor(
    private val ctx: FhirContext,
    private val fhirServerProperties: FHIRServerProperties
)
 {

    //  IGenericClient awsClient;
    //   AmazonSQS sqs;




    private val log = LoggerFactory.getLogger("FHIRAudit")


    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
    fun processingCompletedNormally(theRequestDetails: ServletRequestDetails) {
        var fhirResource: String? = null
        var patientId: String? = null
        val fhirResourceName = theRequestDetails.requestPath
        if (theRequestDetails.parameters.size > 0) {
            if (theRequestDetails.parameters["patient"] != null && theRequestDetails.parameters["patient"] != null && theRequestDetails.parameters["patient"]!!.size > 0) patientId =
                theRequestDetails.parameters["patient"]!![0]
        }
        var contentType = theRequestDetails.getHeader("Content-Type")
        if (StringUtils.isNotBlank(contentType)) {
            val colonIndex = contentType.indexOf(59.toChar())
            if (colonIndex != -1) {
                contentType = contentType.substring(0, colonIndex)
            }
            contentType = contentType.trim { it <= ' ' }
            val encoding = EncodingEnum.forContentType(contentType)
            if (encoding != null) {
                val requestContents = theRequestDetails.loadRequestContents()
                fhirResource = String(requestContents, Constants.CHARSET_UTF8)
                if (!fhirResource.isEmpty()) {
                    try {
                        var baseResource : IBaseResource? = null
                        try {
                            baseResource = ctx.newJsonParser().parseResource(fhirResource)
                        } catch (ex : Exception) {
                            baseResource = ctx.newXmlParser().parseResource(fhirResource)
                        }
                        if (baseResource is QuestionnaireResponse) {
                            patientId = baseResource.subject.reference
                        }
                    } finally {
                    }
                }
            }
        }
        val auditEvent = createAudit(theRequestDetails.servletRequest, fhirResourceName, patientId, fhirResource)
        auditEvent.outcome = AuditEvent.AuditEventOutcome._0
        writeAWS(auditEvent)
    }


    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
    @Throws(ServletException::class, IOException::class)
    fun handleException(
        theRequestDetails: RequestDetails,
        theException: BaseServerResponseException,
        myRequest: HttpServletRequest,
        theServletResponse: HttpServletResponse?
    ): Boolean {
        var patientId: String? = null
        if (theRequestDetails is ServletRequestDetails) {
            val servletRequestDetails = theRequestDetails
            val fhirResourceName = servletRequestDetails.requestPath
            var fhirResource: String? = null
            if (servletRequestDetails.parameters.size > 0) {
                if (servletRequestDetails.parameters["patient"] != null && servletRequestDetails.parameters["patient"]!!.size > 0) patientId =
                    theRequestDetails.getParameters()["patient"]!![0]
            }
            var contentType = myRequest.contentType
            if (StringUtils.isNotBlank(contentType)) {
                val colonIndex = contentType.indexOf(59.toChar())
                if (colonIndex != -1) {
                    contentType = contentType.substring(0, colonIndex)
                }
                contentType = contentType.trim { it <= ' ' }
                val encoding = EncodingEnum.forContentType(contentType)
                if (encoding != null) {
                    val requestContents = theRequestDetails.loadRequestContents()
                    fhirResource = String(requestContents, Constants.CHARSET_UTF8)
                    if (!fhirResource.isEmpty()) {
                        try {
                            var baseResource : IBaseResource? = null
                            try {
                                baseResource = ctx.newJsonParser().parseResource(fhirResource)
                            } catch (ex : Exception) {
                                baseResource = ctx.newXmlParser().parseResource(fhirResource)
                            }
                            if (baseResource is QuestionnaireResponse) {
                                patientId = baseResource.subject.reference
                            }
                        } catch (finalEx : Exception) {

                        }
                    }
                }
            }
            val auditEvent =
                createAudit(servletRequestDetails.servletRequest, fhirResourceName, patientId, fhirResource)
            addAWSOutComeException(auditEvent, theException)
            writeAWS(auditEvent)
        }
        return true
    }


    fun createAudit(
        httpRequest: HttpServletRequest,
        fhirResourceName: String?,
        patientId: String?,
        resource: String?
    ): AuditEvent {
        val auditEvent = AuditEvent()
        auditEvent.recorded = Date()
        when (httpRequest.method) {
            "GET" -> auditEvent.action = AuditEvent.AuditEventAction.R
            "POST" -> auditEvent.action = AuditEvent.AuditEventAction.C
            "PUT" -> auditEvent.action = AuditEvent.AuditEventAction.U
            "PATCH" -> auditEvent.action = AuditEvent.AuditEventAction.U
            "DEL", "DELETE" -> auditEvent.action = AuditEvent.AuditEventAction.D
        }


        // Entity
        val entityComponent = auditEvent.addEntity()
        var path = httpRequest.scheme + "://" + httpRequest.serverName + httpRequest.pathInfo
        if (path.contains("$")) auditEvent.action = AuditEvent.AuditEventAction.E
        if (httpRequest.queryString != null) path += "?" + httpRequest.queryString
        entityComponent.addDetail().setType("query").value = StringType(path)
        if (httpRequest.method == "GET") {
            auditEvent.type = Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("access")
        } else {
            auditEvent.type = Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("transmit")
           // DISABLED 3/Oct/2022 Don't put resources in audit  entityComponent.addDetail().setType("resource").value = StringType(resource)
        }
        entityComponent.type = Coding().setSystem(FhirSystems.FHIR_RESOURCE_TYPE).setCode(fhirResourceName)


        // Source
        // When identity is provided correct this
        if (httpRequest.getHeader("ODS_CODE") != null) {
            auditEvent.source.site = httpRequest.getHeader("ODS_CODE")
        }
        auditEvent.source.observer = Reference()
            .setIdentifier(Identifier().setValue(httpRequest.serverName))
            .setDisplay(fhirServerProperties.server.name + " " + fhirServerProperties.server.version + " " + fhirServerProperties.server.baseUrl)
            .setType("Device")


        // Agent Application
        val agentComponent = auditEvent.addAgent()
        agentComponent.requestor = true
        agentComponent.type = CodeableConcept(Coding().setSystem(FhirSystems.DICOM_AUDIT_ROLES).setCode("110150"))

        /// Agent Patient about
        if (patientId != null) {
            val patient = auditEvent.addAgent()
            patient.requestor = false
            patient.type =
                CodeableConcept(Coding().setSystem(FhirSystems.V3_ROLE_CLASS).setCode("PAT"))
            if (patientId.startsWith("Patient/")) {
                patient.who = Reference().setReference(patientId).setType("Patient")
            } else {
                patient.who =
                    Reference().setType("Patient")
                        .setIdentifier(Identifier().setSystem(FhirSystems.EMIS_PATIENT_IDENTIFIER).setValue(patientId))
            }
        }
        var ipAddress = httpRequest.getHeader("X-FORWARDED-FOR")
        if (ipAddress == null) {
            ipAddress = httpRequest.remoteAddr
        }
        if (ipAddress != null) agentComponent.network.address = ipAddress

        // TODO refactor to use SQS
        return auditEvent
    }

    fun writeAWS(event: AuditEvent) {
        val audit = ctx.newJsonParser().encodeResourceToString(event)
        if (event.hasOutcome() && event.outcome != AuditEvent.AuditEventOutcome._0) {
            log.error(audit)
        } else {
            log.info(audit)
        }
        /*
        String queueName = MessageProperties.getAwsQueueName();
        GetQueueUrlResult queueUrl= sqs.getQueueUrl(queueName);
        SendMessageRequest send_msg_request = new SendMessageRequest()
                .withQueueUrl(queueUrl.getQueueUrl())
                .withMessageBody(audit)
                .withDelaySeconds(5);
        sqs.sendMessage(send_msg_request);

         */
    }

    fun addAWSOutComeException(auditEvent: AuditEvent, exception: Exception) {
        if (exception.message != null) {
            auditEvent.outcomeDesc = exception.message
        }
        auditEvent.outcome = AuditEvent.AuditEventOutcome._8
    }
}
