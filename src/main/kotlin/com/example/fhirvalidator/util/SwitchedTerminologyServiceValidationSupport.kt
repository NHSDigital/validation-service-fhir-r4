package com.example.fhirvalidator.util

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.*
import org.hl7.fhir.instance.model.api.IBaseResource
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
        context: ValidationSupportContext?,
        options: ConceptValidationOptions?,
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
        if (system != null && codeSystemPredicate.test(system)) {
            return override.lookupCode(context, system, code)
        }
        return default.lookupCode(context, system, code)
    }

    override fun invalidateCaches() {
        default.invalidateCaches()
        override.invalidateCaches()
    }
}
