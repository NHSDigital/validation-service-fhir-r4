package com.example.fhirvalidator.shared;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IOperationUnnamed;
import ca.uhn.fhir.util.ParametersUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ValueSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HybridTerminologyValidationSupport extends InMemoryTerminologyServerValidationSupport {
    private final FhirContext myCtx;
    private String myBaseUrl;
    private List<Object> myClientInterceptors = new ArrayList();
    private static final Logger log = LoggerFactory.getLogger(HybridTerminologyValidationSupport.class);

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
    }

    public void addClientInterceptor(@Nonnull Object theClientInterceptor) {
        Validate.notNull(theClientInterceptor, "theClientInterceptor must not be null", new Object[0]);
        log.info("Authentication Interceptor added to NHS Digital Terminology Validation Support");
        this.myClientInterceptors.add(theClientInterceptor);
    }

    public HybridTerminologyValidationSupport(FhirContext theCtx) {
        super(theCtx);
        log.info("NHS Digital Terminology Validation Support active");
        Validate.notNull(theCtx, "theCtx must not be null", new Object[0]);
        this.myCtx = theCtx;
    }

    public CodeValidationResult validateCodeRemote(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {
        return this.invokeRemoteValidateCode(theCodeSystem, theCode, theDisplay, theValueSetUrl, (IBaseResource)null);
    }

    public CodeValidationResult validateCodeInValueSetRemote(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, @Nonnull IBaseResource theValueSet) {
        if (theOptions != null && theOptions.isInferSystem()) {
            return null;
        } else {
            IBaseResource valueSet = theValueSet;
            String valueSetUrl = DefaultProfileValidationSupport.getConformanceResourceUrl(this.myCtx, theValueSet);
            if (StringUtils.isNotBlank(valueSetUrl)) {
                valueSet = null;
            } else {
                valueSetUrl = null;
            }

            return this.invokeRemoteValidateCode(theCodeSystem, theCode, theDisplay, valueSetUrl, valueSet);
        }
    }

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
                if (StringUtils.isNotBlank(theValueSetUrl)) {
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

            IBaseParameters output = (IBaseParameters)((IOperationUnnamed)client.operation().onType(resourceType)).named("validate-code").withParameters(input).execute();
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

    @Override
    public ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext, ValueSetExpansionOptions theExpansionOptions, @NotNull IBaseResource theValueSetToExpand) {
        if (theValueSetToExpand instanceof ValueSet) {
            ValueSet valueSet = (ValueSet) theValueSetToExpand;
        }
        ValueSetExpansionOutcome valueSetExpansionOutcome = super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand);

        return valueSetExpansionOutcome;
    }

    @Override
    public CodeValidationResult validateCode(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {

        if (theCodeSystem.equals("http://snomed.info/sct")) return this.validateCodeInValueSetRemote(theValidationSupportContext,
                theOptions,theCodeSystem,
                theCode,
                theDisplay,
                theValidationSupportContext.getRootValidationSupport().fetchValueSet(theValueSetUrl));


        if (theValueSetUrl != null && theValueSetUrl.equals("http://hl7.org/fhir/ValueSet/units-of-time")) {
            return validateCodeInValueSet(theValidationSupportContext,theOptions,"http://unitsofmeasure.org",theCode,theDisplay,theValidationSupportContext.getRootValidationSupport().fetchValueSet("http://hl7.org/fhir/ValueSet/units-of-time"));
        }
        CodeValidationResult codeValidationResult = super.validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode, theDisplay, theValueSetUrl);
        return codeValidationResult;
    }



    @Override
    public LookupCodeResult lookupCode(ValidationSupportContext theValidationSupportContext, String theSystem, String theCode) {
       // Consider extending to cover SNOMED from Onto Server
        return super.lookupCode(theValidationSupportContext, theSystem, theCode);
    }

    @Override
    public CodeValidationResult validateCodeInValueSet(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystemUrlAndVersion, String theCode, String theDisplay, @NotNull IBaseResource theValueSet) {

        if (theCodeSystemUrlAndVersion != null && theCodeSystemUrlAndVersion.equals("http://snomed.info/sct")) return validateCodeInValueSetRemote(theValidationSupportContext,theOptions,theCodeSystemUrlAndVersion, theCode, theDisplay, theValueSet);

        CodeValidationResult codeValidationResult = super.validateCodeInValueSet(theValidationSupportContext, theOptions, theCodeSystemUrlAndVersion, theCode, theDisplay, theValueSet);
        if (theValueSet instanceof ValueSet) {
            ValueSet valueSet = (ValueSet) theValueSet;
            if (valueSet.hasUrl())  {
                if (codeValidationResult != null) log.debug("validateCode {} {} {}",theCodeSystemUrlAndVersion, theCode, valueSet.getUrl(),codeValidationResult.getMessage());
                else log.debug("validateCode {} {} {}",theCodeSystemUrlAndVersion, theCode, valueSet.getUrl());
            }
        }
        return codeValidationResult;
    }
}
