package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationResult
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.OperationOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class ValidateControllerTest {
    @Mock
    lateinit var mockFhirContext: FhirContext

    @Mock
    lateinit var mockValidator: FhirValidator

    @Mock
    lateinit var mockInputParser: IParser

    @Mock
    lateinit var mockOutputParser: IParser

    @Mock
    lateinit var mockValidationResult: ValidationResult

    @Mock
    lateinit var mockMessageDefinitionApplier: MessageDefinitionApplier

    @Mock
    lateinit var mockCapabilityStatementApplier: CapabilityStatementApplier

    @InjectMocks
    lateinit var testValidateController: ValidateController

    @Test
    fun parseAndValidateResource_returns_combined_operation_outcome_when_validation_succeeds() {
        val patient = Patient()
        val validatorIssue = OperationOutcome.OperationOutcomeIssueComponent().apply {
            diagnostics = "validator issue"
        }
        val validatorOutcome = OperationOutcome().apply { addIssue(validatorIssue) }
        val payload = """{"resourceType":"Patient"}"""

        Mockito.`when`(mockFhirContext.newJsonParser()).thenReturn(mockInputParser)
        Mockito.`when`(mockInputParser.parseResource(payload)).thenReturn(patient)
        Mockito.`when`(mockMessageDefinitionApplier.applyMessageDefinition(patient)).thenReturn(null)
        Mockito.`when`(mockValidator.validateWithResult(patient)).thenReturn(mockValidationResult)
        Mockito.`when`(mockValidationResult.toOperationOutcome()).thenReturn(validatorOutcome)

        val result = testValidateController.parseAndValidateResource(payload, "request-id")

        assertEquals(1, result.issue.size)
        assertEquals("validator issue", result.issue[0].diagnostics)
        Mockito.verify(mockCapabilityStatementApplier).applyCapabilityStatementProfiles(patient)
        Mockito.verify(mockValidator).validateWithResult(patient)
    }

    @Test
    fun parseAndValidateResource_returns_operation_outcome_when_input_invalid() {
        val payload = "not-json"
        Mockito.`when`(mockFhirContext.newJsonParser()).thenReturn(mockInputParser)
        Mockito.`when`(mockInputParser.parseResource(payload)).thenThrow(DataFormatException("bad payload"))

        val result = testValidateController.parseAndValidateResource(payload, "request-id")

        assertEquals(1, result.issue.size)
        assertEquals("bad payload", result.issue[0].diagnostics)
        Mockito.verifyNoInteractions(mockValidator, mockMessageDefinitionApplier, mockCapabilityStatementApplier)
    }

    @Test
    fun validate_returns_encoded_operation_outcome_string() {
        val patient = Patient()
        val validatorIssue = OperationOutcome.OperationOutcomeIssueComponent().apply {
            diagnostics = "validator issue"
        }
        val validatorOutcome = OperationOutcome().apply { addIssue(validatorIssue) }
        val payload = """{"resourceType":"Patient"}"""
        val encodedOperationOutcome = """{"resourceType":"OperationOutcome"}"""

        Mockito.`when`(mockFhirContext.newJsonParser()).thenReturn(mockInputParser, mockOutputParser)
        Mockito.`when`(mockInputParser.parseResource(payload)).thenReturn(patient)
        Mockito.`when`(mockMessageDefinitionApplier.applyMessageDefinition(patient)).thenReturn(null)
        Mockito.`when`(mockValidator.validateWithResult(patient)).thenReturn(mockValidationResult)
        Mockito.`when`(mockValidationResult.toOperationOutcome()).thenReturn(validatorOutcome)
        Mockito.`when`(mockOutputParser.encodeResourceToString(Mockito.any(IBaseResource::class.java))).thenReturn(encodedOperationOutcome)

        val response = testValidateController.validate(payload, null)

        assertEquals(encodedOperationOutcome, response)
    }

    @Test
    fun getResourcesToValidate_returns_inner_bundles_when_passed_searchset_containing_bundles() {
        val childBundle = Bundle()
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.SEARCHSET
        val bundleEntry = bundle.addEntry()
        bundleEntry.resource = childBundle
        assertEquals(listOf(childBundle), testValidateController.getResourcesToValidate(bundle))
    }

    @Test
    fun getResourcesToValidate_returns_bundle_when_passed_searchset_containing_other_resource_type() {
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.SEARCHSET
        val bundleEntry = bundle.addEntry()
        bundleEntry.resource = Patient()
        assertEquals(listOf(bundle), testValidateController.getResourcesToValidate(bundle))
    }

    @Test
    fun getResourcesToValidate_returns_bundle_when_passed_other_bundle_type() {
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.COLLECTION
        val bundleEntry = bundle.addEntry()
        bundleEntry.resource = Patient()
        assertEquals(listOf(bundle), testValidateController.getResourcesToValidate(bundle))
    }

    @Test
    fun getResourcesToValidate_returns_resource_when_passed_other_resource_type() {
        val patient = Patient()
        assertEquals(listOf(patient), testValidateController.getResourcesToValidate(patient))
    }
}
