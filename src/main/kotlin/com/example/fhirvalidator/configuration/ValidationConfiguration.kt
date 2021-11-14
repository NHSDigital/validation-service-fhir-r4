package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.model.ValidationConfig
import com.example.fhirvalidator.service.ImplementationGuideParser
import com.example.fhirvalidator.shared.AuthorisationClient
import com.example.fhirvalidator.shared.HybridTerminologyValidationSupport
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class ValidationConfiguration(private val implementationGuideParser: ImplementationGuideParser) {
    companion object : KLogging()


    @Bean
    fun validator(fhirContext: FhirContext, instanceValidator: FhirInstanceValidator): FhirValidator {
        val validator = fhirContext.newValidator().registerValidatorModule(instanceValidator)
        logger.info("Initialising Validator")
        validator.validateWithResult(getInitialisationExampleResource())
        return validator
    }

    @Bean
    fun instanceValidator(supportChain: ValidationSupportChain): FhirInstanceValidator {
        return FhirInstanceValidator(CachingValidationSupport(supportChain))
    }

    @Bean
    fun validationSupportChain(
        fhirContext: FhirContext,
        hybridTerminologyValidationSupport: HybridTerminologyValidationSupport,
        npmPackages: List<NpmPackage>
    ): ValidationSupportChain {
        val supportChain = ValidationSupportChain(
            DefaultProfileValidationSupport(fhirContext),
            CommonCodeSystemsTerminologyService(fhirContext),
            hybridTerminologyValidationSupport,
            SnapshotGeneratingValidationSupport(fhirContext)

        )
        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport)
            .forEach(supportChain::addValidationSupport)

        generateSnapshots(supportChain)

        return supportChain

    }

    @Bean
    fun terminologyValidationSupport(fhirContext: FhirContext, validationConfig: ValidationConfig): HybridTerminologyValidationSupport {

        val hybridTerminologyValidationSupport =
            HybridTerminologyValidationSupport(fhirContext)
        hybridTerminologyValidationSupport.addRemoteCodeSystem("http://snomed.info/sct")
        hybridTerminologyValidationSupport.addRemoteCodeSystem("https://dmd.nhs.uk")
        hybridTerminologyValidationSupport.addRemoteCodeSystem("https://dmd.nhs.uk/") // This is an error, trailing slash should be removed

        if (validationConfig.useRemoteTerminology && !validationConfig.terminologyServer.isEmpty()) {
            hybridTerminologyValidationSupport.setBaseUrl(validationConfig.terminologyServer)
            if (!validationConfig.clientId.isEmpty() && !validationConfig.clientSecret.isEmpty()) {
                hybridTerminologyValidationSupport.addClientInterceptor(
                    AuthorisationClient(
                        validationConfig.clientId,
                        validationConfig.clientSecret
                    )
                );
            }
        }
        return hybridTerminologyValidationSupport;
    }

    fun generateSnapshots(supportChain: IValidationSupport) {
        supportChain.fetchAllStructureDefinitions<StructureDefinition>()
            ?.filter { shouldGenerateSnapshot(it) }
            ?.partition { it.baseDefinition.startsWith("http://hl7.org/fhir/") }
            ?.toList()
            ?.flatten()
            ?.forEach {
                try {
                    supportChain.generateSnapshot(
                        ValidationSupportContext(supportChain),
                        it,
                        it.url,
                        "https://fhir.nhs.uk/R4",
                        it.name
                    )
                } catch (e: Exception) {
                    logger.error("Failed to generate snapshot for $it", e)
                }
            }
    }

    private fun shouldGenerateSnapshot(structureDefinition: StructureDefinition): Boolean {
        return !structureDefinition.hasSnapshot() && structureDefinition.derivation == StructureDefinition.TypeDerivationRule.CONSTRAINT
    }

    private fun getInitialisationExampleResource() : MedicationRequest {
        // Basic resource to force validator to initialise (and not the first calls)
        var medicationRequest = MedicationRequest();
        medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE)
        medicationRequest.setMedication(CodeableConcept().addCoding(Coding().setSystem("http://snomed.info/sct").setCode("15517911000001104")))
        medicationRequest.setCourseOfTherapyType(CodeableConcept().addCoding(Coding().setSystem("https://fhir.nhs.uk/CodeSystem/medicationrequest-course-of-therapy").setCode("continuous-repeat-dispensing")))
        return medicationRequest
    }
}
