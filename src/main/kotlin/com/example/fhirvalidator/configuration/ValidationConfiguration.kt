package com.example.fhirvalidator.configuration

import UnsupportedCodeSystemWarningValidationSupport
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.ImplementationGuideParser
import com.example.fhirvalidator.util.AccessTokenInterceptor
import com.example.fhirvalidator.util.SwitchedTerminologyServiceValidationSupport
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.MedicationRequest
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import java.util.*
import java.util.function.Predicate


@Configuration
class ValidationConfiguration(
    private val implementationGuideParser: ImplementationGuideParser,
    private val terminologyValidationProperties: TerminologyValidationProperties
) {
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
        switchedTerminologyServiceValidationSupport: SwitchedTerminologyServiceValidationSupport,
        npmPackages: List<NpmPackage>
    ): ValidationSupportChain {
        val supportChain = ValidationSupportChain(
            DefaultProfileValidationSupport(fhirContext),
            SnapshotGeneratingValidationSupport(fhirContext),
            CommonCodeSystemsTerminologyService(fhirContext),
            switchedTerminologyServiceValidationSupport
        )

        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport)
            .forEach(supportChain::addValidationSupport)

        generateSnapshots(supportChain)

        return supportChain
    }

    @Bean
    fun switchedTerminologyServiceValidationSupport(
        fhirContext: FhirContext,
        optionalRemoteTerminologySupport: Optional<RemoteTerminologyServiceValidationSupport>
    ): SwitchedTerminologyServiceValidationSupport {
        val snomedValidationSupport = if (optionalRemoteTerminologySupport.isPresent) {
            CachingValidationSupport(optionalRemoteTerminologySupport.get())
        } else {
            UnsupportedCodeSystemWarningValidationSupport(fhirContext)
        }

        return SwitchedTerminologyServiceValidationSupport(
            fhirContext,
            InMemoryTerminologyServerValidationSupport(fhirContext),
            snomedValidationSupport,
            Predicate { it.startsWith("http://snomed.info/sct") }
        )
    }

    @Bean
    @ConditionalOnProperty("terminology.url")
    fun remoteTerminologyServiceValidationSupport(
        fhirContext: FhirContext,
        optionalAuthorizedClientManager: Optional<OAuth2AuthorizedClientManager>
    ): RemoteTerminologyServiceValidationSupport {
        logger.info("Using remote terminology server at ${terminologyValidationProperties.url}")
        val validationSupport = RemoteTerminologyServiceValidationSupport(fhirContext)
        validationSupport.setBaseUrl(terminologyValidationProperties.url)

        if (optionalAuthorizedClientManager.isPresent) {
            val authorizedClientManager = optionalAuthorizedClientManager.get()
            val accessTokenInterceptor = AccessTokenInterceptor(authorizedClientManager)
            validationSupport.addClientInterceptor(accessTokenInterceptor)
        }

        return validationSupport
    }

    fun generateSnapshots(supportChain: IValidationSupport) {
        val structureDefinitions = supportChain.fetchAllStructureDefinitions<StructureDefinition>() ?: return
        val context = ValidationSupportContext(supportChain)
        structureDefinitions
            .filter { shouldGenerateSnapshot(it) }
            .forEach {
                try {
                    supportChain.generateSnapshot(context, it, it.url, "https://fhir.nhs.uk/R4", it.name)
                } catch (e: Exception) {
                    logger.error("Failed to generate snapshot for $it", e)
                }
            }
    }

    private fun shouldGenerateSnapshot(structureDefinition: StructureDefinition): Boolean {
        return !structureDefinition.hasSnapshot() && structureDefinition.derivation == StructureDefinition.TypeDerivationRule.CONSTRAINT
    }

    private fun getInitialisationExampleResource(): MedicationRequest {
        // Basic resource to force validator to initialise (and not the first calls)
        val medicationRequest = MedicationRequest()
        medicationRequest.status = MedicationRequest.MedicationRequestStatus.ACTIVE
        medicationRequest.medication = CodeableConcept().addCoding(
            Coding().setSystem("http://snomed.info/sct").setCode("15517911000001104")
        )
        medicationRequest.courseOfTherapyType = CodeableConcept().addCoding(
            Coding().setSystem("https://fhir.nhs.uk/CodeSystem/medicationrequest-course-of-therapy")
                .setCode("continuous-repeat-dispensing")
        )
        return medicationRequest
    }
}
