package software.nhs.fhirvalidator.common.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import software.nhs.fhirvalidator.common.service.CapabilityStatementApplier
import software.nhs.fhirvalidator.common.service.MessageDefinitionApplier
import software.nhs.fhirvalidator.common.service.ImplementationGuideParser
import software.nhs.fhirvalidator.common.controller.ValidateController
import software.nhs.fhirvalidator.common.configuration.ValidationConfiguration
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.utilities.npm.NpmPackage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class ValidateControllerTest {
    @Mock
    var mockFhirContext: FhirContext = FhirContext.forR4()

    @Mock
    var mockNpmPackages: List<NpmPackage> = emptyList()

    @Mock
    lateinit var mockImplementationGuideParser: ImplementationGuideParser

    @Mock
    lateinit var mockValidationConfiguration: ValidationConfiguration

    @Mock
    lateinit var mockMessageDefinitionApplier: MessageDefinitionApplier

    @Mock
    lateinit var mockCapabilityStatementApplier: CapabilityStatementApplier

    @InjectMocks
    var testValidateController: ValidateController = ValidateController(mockFhirContext, mockNpmPackages)

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
