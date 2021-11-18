package com.example.fhirvalidator.shared;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IOperationUnnamed;
import ca.uhn.fhir.util.ParametersUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RemoteTerminoloyValidationSupport extends RemoteTerminologyServiceValidationSupport {
    private final FhirContext myCtx;
    private String myBaseUrl;
    private List<Object> myClientInterceptors = new ArrayList();

    public RemoteTerminoloyValidationSupport(FhirContext theCtx) {
        super(theCtx);

        Validate.notNull(theCtx, "theCtx must not be null", new Object[0]);
        this.myCtx = theCtx;
    }

    @Override
    public CodeValidationResult validateCode(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {

        if (theValueSetUrl != null) return super.validateCodeInValueSet(theValidationSupportContext,
                theOptions,theCodeSystem,
                theCode,
                theDisplay,
                theValidationSupportContext.getRootValidationSupport().fetchValueSet(theValueSetUrl));

        CodeValidationResult codeValidationResult = super.validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode, theDisplay, theValueSetUrl);
        return codeValidationResult;
    }

    @Override
    protected CodeValidationResult invokeRemoteValidateCode(String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl, IBaseResource theValueSet) {

        if (StringUtils.isBlank(theCode)) {
            return null;
        } else {
            IGenericClient client = this.provideClient();
            IBaseParameters input = ParametersUtil.newInstance(this.getFhirContext());
            String resourceType = "ValueSet";
            if (theValueSet == null && theValueSetUrl == null) {
                resourceType = "CodeSystem";
                ParametersUtil.addParameterToParametersUri(this.getFhirContext(), input, "url", theCodeSystem);
                ParametersUtil.addParameterToParametersString(this.getFhirContext(), input, "code", theCode);
                if (StringUtils.isNotBlank(theDisplay)) {
                    ParametersUtil.addParameterToParametersString(this.getFhirContext(), input, "display", theDisplay);
                }
            } else {
                if (StringUtils.isNotBlank(theValueSetUrl) && theValueSet == null) {
                    ParametersUtil.addParameterToParametersUri(this.getFhirContext(), input, "url", theValueSetUrl);
                }

                ParametersUtil.addParameterToParametersString(this.getFhirContext(), input, "code", theCode);
                if (StringUtils.isNotBlank(theCodeSystem)) {
                    ParametersUtil.addParameterToParametersUri(this.getFhirContext(), input, "system", theCodeSystem);
                }

                if (StringUtils.isNotBlank(theDisplay)) {
                    ParametersUtil.addParameterToParametersString(this.getFhirContext(), input, "display", theDisplay);
                }

                if (theValueSet != null) {
                    ParametersUtil.addParameterToParameters(this.getFhirContext(), input, "valueSet", theValueSet);
                }
            }
            IBaseParameters output = (IBaseParameters) ((IOperationUnnamed) client.operation().onType(resourceType)).named("validate-code").withParameters(input).execute();
            List<String> resultValues = ParametersUtil.getNamedParameterValuesAsString(this.getFhirContext(), output, "result");
            if (resultValues.size() >= 1 && !StringUtils.isBlank((CharSequence)resultValues.get(0))) {
                Validate.isTrue(resultValues.size() == 1, "Response contained %d 'result' values", (long)resultValues.size());
                boolean success = "true".equalsIgnoreCase((String)resultValues.get(0));
                CodeValidationResult retVal = new CodeValidationResult();
                List displayValues;
                if (success) {
                    retVal.setCode(theCode);
                    displayValues = ParametersUtil.getNamedParameterValuesAsString(this.getFhirContext(), output, "display");
                    if (displayValues.size() > 0) {
                        retVal.setDisplay((String)displayValues.get(0));
                    }
                } else {
                    retVal.setSeverity(IssueSeverity.ERROR);
                    displayValues = ParametersUtil.getNamedParameterValuesAsString(this.getFhirContext(), output, "message");
                    if (displayValues.size() > 0) {
                        retVal.setMessage((String)displayValues.get(0));
                    }
                }

                return retVal;
            } else {
                return null;
            }
        }
    }

    private IGenericClient provideClient() {
        IGenericClient retVal = this.myCtx.newRestfulGenericClient(this.myBaseUrl);
        Iterator var2 = this.myClientInterceptors.iterator();

        while(var2.hasNext()) {
            Object next = var2.next();
            retVal.registerInterceptor(next);
        }

        return retVal;
    }
    public void setBaseUrl(String theBaseUrl) {
        Validate.notBlank(theBaseUrl, "theBaseUrl must be provided", new Object[0]);
        this.myBaseUrl = theBaseUrl;
        super.setBaseUrl(theBaseUrl);
    }

    public void addClientInterceptor(@Nonnull Object theClientInterceptor) {
        Validate.notNull(theClientInterceptor, "theClientInterceptor must not be null", new Object[0]);
        this.myClientInterceptors.add(theClientInterceptor);
        super.addClientInterceptor(theClientInterceptor);
    }

}
