package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.provider.ValidateProvider
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Patient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class ValidateProviderTest {
    @Mock
    lateinit var mockFhirContext: FhirContext

    @Mock
    lateinit var mockValidator: FhirValidator

    @Mock
    lateinit var mockMessageDefinitionApplier: MessageDefinitionApplier

    @Mock
    lateinit var mockCapabilityStatementApplier: CapabilityStatementApplier

    @InjectMocks
    lateinit var testValidateProvider: ValidateProvider

    @Test
    fun getResourcesToValidate_returns_emptyList_when_passed_null() {
        assertEquals(emptyList<IBaseResource>(), testValidateProvider.getResourcesToValidate(null))
    }

    @Test
    fun getResourcesToValidate_returns_list_of_entries_when_passed_searchset() {
        val patient = Patient()
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.SEARCHSET
        val bundleEntry = bundle.addEntry()
        bundleEntry.resource = patient
        assertEquals(listOf(patient), testValidateProvider.getResourcesToValidate(bundle))
    }

    @Test
    fun getResourcesToValidate_returns_a_bundle_in_a_list_when_passed_other_bundle_type() {
        val patient = Patient()
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.COLLECTION
        val bundleEntry = bundle.addEntry()
        bundleEntry.resource = patient
        assertEquals(listOf(bundle), testValidateProvider.getResourcesToValidate(bundle))
    }

    @Test
    fun getResourcesToValidate_returns_a_patient_in_a_list_when_passed_a_patient() {
        val patient = Patient()
        assertEquals(listOf(patient), testValidateProvider.getResourcesToValidate(patient))
    }

    @Test
    fun validateResource_returns_messageDefinitionErrors() {
        val patient = Patient()
        val operationOutcome = OperationOutcome()
        given(mockMessageDefinitionApplier.applyMessageDefinition(patient)).willReturn(operationOutcome)

        assertEquals(operationOutcome, testValidateProvider.validateResource(patient, null))
    }
}

