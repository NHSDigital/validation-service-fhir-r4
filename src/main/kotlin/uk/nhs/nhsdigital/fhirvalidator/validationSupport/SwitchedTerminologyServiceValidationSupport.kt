package uk.nhs.nhsdigital.fhirvalidator.validationSupport

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import org.hl7.fhir.instance.model.api.IBaseResource
import uk.nhs.nhsdigital.fhirvalidator.controller.VerifyController
import java.util.function.Predicate

class SwitchedTerminologyServiceValidationSupport(
    private val fhirContext: FhirContext,
    private val default: IValidationSupport,
    private val override: IValidationSupport,
    private val codeSystemPredicate: Predicate<String>
) : IValidationSupport {
    override fun getFhirContext(): FhirContext {
        return fhirContext
    }

    override fun isCodeSystemSupported(context: ValidationSupportContext?, system: String?): Boolean {
        return default.isCodeSystemSupported(context, system) || override.isCodeSystemSupported(context, system)
    }

    override fun isValueSetSupported(context: ValidationSupportContext?, url: String?): Boolean {
        return default.isValueSetSupported(context, url) || override.isValueSetSupported(context, url)
    }

    override fun validateCode(
        context: ValidationSupportContext,
        options: ConceptValidationOptions,
        system: String?,
        code: String?,
        display: String?,
        url: String?
    ): IValidationSupport.CodeValidationResult? {
        if (system != null && codeSystemPredicate.test(system)) {
            return override.validateCode(context, options, system, code, display, url)
        }
        return default.validateCode(context, options, system, code, display, url)
    }

    override fun validateCodeInValueSet(
        context: ValidationSupportContext?,
        options: ConceptValidationOptions?,
        system: String?,
        code: String?,
        display: String?,
        valueSet: IBaseResource
    ): IValidationSupport.CodeValidationResult? {
        if (system != null && codeSystemPredicate.test(system)) {
            return override.validateCodeInValueSet(context, options, system, code, display, valueSet)
        }
        return default.validateCodeInValueSet(context, options, system, code, display, valueSet)
    }

    override fun lookupCode(
        context: ValidationSupportContext?,
        system: String?,
        code: String?
    ): IValidationSupport.LookupCodeResult? {
        return this.lookupCode(context, system, code, null)
    }

    override fun lookupCode(
        context: ValidationSupportContext?,
        system: String?,
        code: String?,
        theDisplayLanguage: String?
    ): IValidationSupport.LookupCodeResult? {
        if (system != null && codeSystemPredicate.test(system)) {
            return override.lookupCode(context, system, code, theDisplayLanguage)
        }
        return default.lookupCode(context, system, code, theDisplayLanguage)
    }

    override fun expandValueSet(
        theValidationSupportContext: ValidationSupportContext?,
        theExpansionOptions: ValueSetExpansionOptions?,
        theValueSetToExpand: IBaseResource
    ): IValidationSupport.ValueSetExpansionOutcome? {
        VerifyController.logger.info("Switched validation expansion called")
        val outcome = default.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand)
        if (outcome != null && outcome.error == null) return outcome
        return override.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand)
    }

    override fun invalidateCaches() {
        default.invalidateCaches()
        override.invalidateCaches()
    }
}
