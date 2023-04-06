package uk.nhs.nhsdigital.fhirvalidator.validationSupport

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import org.hl7.fhir.instance.model.api.IBaseResource

class UnsupportedCodeSystemWarningValidationSupport(
    private val fhirContext: FhirContext
) : IValidationSupport {
    override fun getFhirContext(): FhirContext {
        return fhirContext
    }

    override fun isCodeSystemSupported(context: ValidationSupportContext?, system: String?): Boolean {
        return true
    }

    override fun isValueSetSupported(context: ValidationSupportContext?, url: String?): Boolean {
        return true
    }

    override fun validateCode(
        context: ValidationSupportContext,
        options: ConceptValidationOptions,
        system: String?,
        code: String?,
        display: String?,
        url: String?
    ): IValidationSupport.CodeValidationResult {
        return IValidationSupport.CodeValidationResult()
            .setSeverity(IValidationSupport.IssueSeverity.WARNING)
            .setMessage("Unsupported code system $system")
    }

    override fun validateCodeInValueSet(
        context: ValidationSupportContext?,
        options: ConceptValidationOptions?,
        system: String?,
        code: String?,
        display: String?,
        valueSet: IBaseResource
    ): IValidationSupport.CodeValidationResult {
        return IValidationSupport.CodeValidationResult()
            .setSeverity(IValidationSupport.IssueSeverity.WARNING)
            .setMessage("Unsupported code system $system")
    }

    override fun lookupCode(
        context: ValidationSupportContext?,
        system: String?,
        code: String?
    ): IValidationSupport.LookupCodeResult {
        return IValidationSupport.LookupCodeResult()
            .setSearchedForSystem(system)
            .setSearchedForCode(code)
            .setFound(false)
    }
}
