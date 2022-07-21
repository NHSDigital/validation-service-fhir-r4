package uk.nhs.nhsdigital.fhirvalidator.shared;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.*;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IOperationUnnamed;
import ca.uhn.fhir.util.BundleUtil;
import ca.uhn.fhir.util.ParametersUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.common.hapi.validation.support.BaseValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RemoteTerminologyServiceValidationSupport extends BaseValidationSupport implements IValidationSupport {
    private String myBaseUrl;
    private List<Object> myClientInterceptors = new ArrayList();

    public RemoteTerminologyServiceValidationSupport(FhirContext theFhirContext) {

        super(theFhirContext);

        theFhirContext.getRestfulClientFactory().setConnectTimeout(30000); // Oh yes
        System.out.println(theFhirContext.getRestfulClientFactory().getConnectTimeout());
    }

    @Nullable
    @Override
    public ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext, @Nullable ValueSetExpansionOptions theExpansionOptions, @NotNull IBaseResource theValueSetToExpand) {

        IGenericClient client = this.provideClient();
        IBaseParameters input = ParametersUtil.newInstance(this.getFhirContext());
        ParametersUtil.addParameterToParameters(this.getFhirContext(), input, "valueSet", theValueSetToExpand);

        IBaseParameters output = client
                .operation()
                .onType("ValueSet")
                .named("expand")
                .withParameters(input)
                .execute();
        if (output instanceof Parameters) {
            Parameters parameters = (Parameters) output;
            if (parameters.getParameter().size()>0) {
                Resource resource = parameters.getParameter().get(0).getResource();
                if (resource instanceof ValueSet) {
                    ValueSet valueSet = (ValueSet) resource;
                    return new ValueSetExpansionOutcome(resource);
                }
            }
        }
        return super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand);
    }

    public CodeValidationResult validateCode(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {
        // KGM this change for a ValueSet from validator to be used (and not use the one on the ontology server
        if (theValueSetUrl != null) return this.invokeRemoteValidateCode(theCodeSystem, theCode, theDisplay, null, theValidationSupportContext.getRootValidationSupport().fetchValueSet(theValueSetUrl));
        return this.invokeRemoteValidateCode(theCodeSystem, theCode, theDisplay, theValueSetUrl, (IBaseResource)null);
    }

    public CodeValidationResult validateCodeInValueSet(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, @Nonnull IBaseResource theValueSet) {
        if (theOptions != null && theOptions.isInferSystem()) {
            return null;
        } else {
            IBaseResource valueSet = theValueSet;

            String valueSetUrl = DefaultProfileValidationSupport.getConformanceResourceUrl(this.myCtx, theValueSet);
            // KGM this next section
            if (valueSet == null && StringUtils.isNotBlank(valueSetUrl)) valueSet = theValidationSupportContext.getRootValidationSupport().fetchValueSet(valueSetUrl);
            if (valueSet != null)
                valueSetUrl = null;
            // KGM also this line
            return this.invokeRemoteValidateCode(theCodeSystem, theCode, theDisplay, valueSetUrl, valueSet);
        }
    }

    public IBaseResource fetchCodeSystem(String theSystem) {
        IGenericClient client = this.provideClient();
        Class<? extends IBaseBundle> bundleType = this.myCtx.getResourceDefinition("Bundle").getImplementingClass(IBaseBundle.class);
        IBaseBundle results = (IBaseBundle)client.search().forResource("CodeSystem").where(CodeSystem.URL.matches().value(theSystem)).returnBundle(bundleType).execute();
        List<IBaseResource> resultsList = BundleUtil.toListOfResources(this.myCtx, results);
        return resultsList.size() > 0 ? (IBaseResource)resultsList.get(0) : null;
    }

    public IBaseResource fetchValueSet(String theValueSetUrl) {
        IGenericClient client = this.provideClient();
        Class<? extends IBaseBundle> bundleType = this.myCtx.getResourceDefinition("Bundle").getImplementingClass(IBaseBundle.class);
        IBaseBundle results = (IBaseBundle)client.search().forResource("ValueSet").where(CodeSystem.URL.matches().value(theValueSetUrl)).returnBundle(bundleType).execute();
        List<IBaseResource> resultsList = BundleUtil.toListOfResources(this.myCtx, results);
        return resultsList.size() > 0 ? (IBaseResource)resultsList.get(0) : null;
    }

    public boolean isCodeSystemSupported(ValidationSupportContext theValidationSupportContext, String theSystem) {
        return this.fetchCodeSystem(theSystem) != null;
    }

    public boolean isValueSetSupported(ValidationSupportContext theValidationSupportContext, String theValueSetUrl) {
        return this.fetchValueSet(theValueSetUrl) != null;
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
                // KGM changed next line to make ensure url parameter isn't used if a valueSet is present
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

    public void setBaseUrl(String theBaseUrl) {
        Validate.notBlank(theBaseUrl, "theBaseUrl must be provided", new Object[0]);
        this.myBaseUrl = theBaseUrl;
    }

    public void addClientInterceptor(@Nonnull Object theClientInterceptor) {
        Validate.notNull(theClientInterceptor, "theClientInterceptor must not be null", new Object[0]);
        this.myClientInterceptors.add(theClientInterceptor);
    }
}
