package uk.nhs.nhsdigital.fhirvalidator.interceptor;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.nhs.nhsdigital.fhirvalidator.configuration.FHIRServerProperties;
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

@Interceptor
public class AWSAuditEventLoggingInterceptor {


  //  IGenericClient awsClient;

 //   AmazonSQS sqs;

    FhirContext ctx;

    FHIRServerProperties fhirServerProperties;

    private Logger log = LoggerFactory.getLogger("FHIRAudit");

    public AWSAuditEventLoggingInterceptor(//IGenericClient _client,
                                          // AmazonSQS _sqs,
                                           FhirContext _ctx,
    FHIRServerProperties _fhirServerProperties) {

        this.ctx = _ctx;
        this.fhirServerProperties = _fhirServerProperties;
        //this.sqs = _sqs;
    }

    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
    public void processingCompletedNormally(ServletRequestDetails theRequestDetails)  {

        String fhirResource = null;
        String patientId = null;
        String fhirResourceName = theRequestDetails.getRequestPath();

        if (theRequestDetails.getParameters().size()>0) {
            if (theRequestDetails.getParameters().get("patient") != null && theRequestDetails.getParameters().get("patient")!=null && theRequestDetails.getParameters().get("patient").length > 0) patientId = theRequestDetails.getParameters().get("patient")[0];
        }
        String contentType = theRequestDetails.getHeader("Content-Type");
        if (StringUtils.isNotBlank(contentType)) {
            int colonIndex = contentType.indexOf(59);
            if (colonIndex != -1) {
                contentType = contentType.substring(0, colonIndex);
            }

            contentType = contentType.trim();
            EncodingEnum encoding = EncodingEnum.forContentType(contentType);
            if (encoding != null) {
                byte[] requestContents = theRequestDetails.loadRequestContents();
                fhirResource = new String(requestContents, Constants.CHARSET_UTF8);
                if (fhirResource != null && !fhirResource.isEmpty()) {
                    try {
                        IBaseResource baseResource = ctx.newJsonParser().parseResource(fhirResource);
                        if (baseResource instanceof QuestionnaireResponse) {
                            QuestionnaireResponse form = (QuestionnaireResponse) baseResource;
                            patientId = form.getSubject().getReference();
                        }
                    } finally {

                    }
                }
            }
        }

        AuditEvent auditEvent = createAudit(theRequestDetails.getServletRequest(),fhirResourceName, patientId,fhirResource);
        auditEvent.setOutcome(AuditEvent.AuditEventOutcome._0);
        writeAWS(auditEvent);
    }



    @Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
    public boolean handleException(RequestDetails theRequestDetails, BaseServerResponseException theException, HttpServletRequest myRequest, HttpServletResponse theServletResponse) throws ServletException, IOException {


        String patientId = null;
        if (theRequestDetails instanceof ServletRequestDetails) {
            ServletRequestDetails servletRequestDetails = (ServletRequestDetails) theRequestDetails;
            String fhirResourceName = servletRequestDetails.getRequestPath();
            String fhirResource = null;
            if (servletRequestDetails.getParameters().size()>0) {
                if (servletRequestDetails.getParameters().get("patient") != null && servletRequestDetails.getParameters().get("patient").length > 0) patientId = theRequestDetails.getParameters().get("patient")[0];
            }

                String contentType = myRequest.getContentType();
                if (StringUtils.isNotBlank(contentType)) {
                    int colonIndex = contentType.indexOf(59);
                    if (colonIndex != -1) {
                        contentType = contentType.substring(0, colonIndex);
                    }

                    contentType = contentType.trim();
                    EncodingEnum encoding = EncodingEnum.forContentType(contentType);
                    if (encoding != null) {
                        byte[] requestContents = theRequestDetails.loadRequestContents();
                        fhirResource = new String(requestContents, Constants.CHARSET_UTF8);
                        if (fhirResource != null && !fhirResource.isEmpty()) {
                            try {
                                IBaseResource baseResource = ctx.newJsonParser().parseResource(fhirResource);
                                if (baseResource instanceof QuestionnaireResponse) {
                                    QuestionnaireResponse form = (QuestionnaireResponse) baseResource;
                                    patientId = form.getSubject().getReference();
                                }
                            } finally {

                            }
                        }
                    }
                }

            AuditEvent auditEvent = createAudit(servletRequestDetails.getServletRequest(),fhirResourceName, patientId,fhirResource);
            addAWSOutComeException(auditEvent,theException);
            writeAWS(auditEvent);
        }

        return true;
    }



    public AuditEvent createAudit(HttpServletRequest httpRequest, String fhirResourceName, String patientId, String resource) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setRecorded(new Date());

        switch (httpRequest.getMethod()) {
            case "GET":
                auditEvent.setAction(AuditEvent.AuditEventAction.R);
                break;
            case "POST":
                auditEvent.setAction(AuditEvent.AuditEventAction.C);
                break;
            case "PUT":
                auditEvent.setAction(AuditEvent.AuditEventAction.U);
                break;
            case "PATCH":
                auditEvent.setAction(AuditEvent.AuditEventAction.U);
                break;
            case "DEL":
            case "DELETE":
                auditEvent.setAction(AuditEvent.AuditEventAction.D);
                break;
        }


        // Entity
        AuditEvent.AuditEventEntityComponent entityComponent = auditEvent.addEntity();

        String path = httpRequest.getScheme() + "://" + httpRequest.getServerName() + httpRequest.getPathInfo();
        if (path.contains("$")) auditEvent.setAction(AuditEvent.AuditEventAction.E);
        if (httpRequest.getQueryString() != null) path += "?"+httpRequest.getQueryString();
        entityComponent.addDetail().setType("query").setValue(new StringType(path));

        if (httpRequest.getMethod().equals("GET")) {
            auditEvent.setType(new Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("access"));
        } else {
            auditEvent.setType(new Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("transmit"));
            entityComponent.addDetail().setType("resource").setValue(new StringType(resource));
        }
        entityComponent.setType(new Coding().setSystem(FhirSystems.FHIR_RESOURCE_TYPE).setCode(fhirResourceName));



        // Source
        // When identity is provided correct this
        if (httpRequest.getHeader("ODS_CODE") != null) {
            auditEvent.getSource().setSite(httpRequest.getHeader("ODS_CODE"));
        }
        auditEvent.getSource().setObserver(new Reference()
                        .setIdentifier(new Identifier().setValue(httpRequest.getServerName()))
                        .setDisplay(this.fhirServerProperties.getServer().getName() + " " + this.fhirServerProperties.getServer().getVersion() + " " + this.fhirServerProperties.getServer().getBaseUrl())
                        .setType("Device"));


        // Agent Application
        AuditEvent.AuditEventAgentComponent agentComponent = auditEvent.addAgent();
        agentComponent.setRequestor(true);
        agentComponent.setType(new CodeableConcept(new Coding().setSystem(FhirSystems.DICOM_AUDIT_ROLES).setCode("110150")));

        /// Agent Patient about
        if (patientId != null) {
            AuditEvent.AuditEventAgentComponent patient = auditEvent.addAgent();
            patient.setRequestor(false);
            patient.setType(new CodeableConcept(new Coding().setSystem(FhirSystems.V3_ROLE_CLASS).setCode("PAT")));
            if (patientId.startsWith("Patient/")) {
                patient.setWho(new Reference().setReference(patientId).setType("Patient"));
            } else {
                patient.setWho(new Reference().setType("Patient").setIdentifier(new Identifier().setSystem(FhirSystems.EMIS_PATIENT_IDENTIFIER).setValue(patientId)));
            }
        }

        String ipAddress = httpRequest.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = httpRequest.getRemoteAddr();
        }
        if (ipAddress != null) agentComponent.getNetwork().setAddress(ipAddress);

        // TODO refactor to use SQS


        return auditEvent;
    }

    public void writeAWS(AuditEvent event) {
        String audit = ctx.newJsonParser().encodeResourceToString(event);
        if (event.hasOutcome() && !event.getOutcome().equals(AuditEvent.AuditEventOutcome._0)) {
            log.error(audit);
        } else {
            log.info(audit);
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

    public void addAWSOutComeException(AuditEvent auditEvent, Exception exception) {
        if (exception.getMessage()!=null) {
            auditEvent.setOutcomeDesc(exception.getMessage());
        }
        auditEvent.setOutcome(AuditEvent.AuditEventOutcome._8);

    }
}
