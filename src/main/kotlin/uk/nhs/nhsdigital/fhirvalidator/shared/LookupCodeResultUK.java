package uk.nhs.nhsdigital.fhirvalidator.shared;

import ca.uhn.fhir.context.support.IValidationSupport;
import org.hl7.fhir.r4.model.Parameters;

public class LookupCodeResultUK extends IValidationSupport.LookupCodeResult {
    private Parameters originalParameters;

    public Parameters getOriginalParameters() {
        return originalParameters;
    }

    public void setOriginalParameters(Parameters originalParameters) {
        this.originalParameters = originalParameters;
    }
}
