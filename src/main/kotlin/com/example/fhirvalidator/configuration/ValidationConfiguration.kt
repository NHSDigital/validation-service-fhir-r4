package com.example.fhirvalidator.configuration

import com.example.fhirvalidator.validationSupport.UnsupportedCodeSystemWarningValidationSupport
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.ImplementationGuideParser
import com.example.fhirvalidator.shared.RemoteTerminologyServiceValidationSupport
import com.example.fhirvalidator.util.AccessTokenInterceptor
import com.example.fhirvalidator.validationSupport.SwitchedTerminologyServiceValidationSupport
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
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
        return fhirContext.newValidator().registerValidatorModule(instanceValidator)
    }

    @Bean
    fun instanceValidator(supportChain: ValidationSupportChain): FhirInstanceValidator {
        return FhirInstanceValidator(CachingValidationSupport(supportChain))
    }

    @Bean("SupportChain")
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

        //Initialise now instead of when the first message arrives
        generateSnapshots(supportChain)
        supportChain.fetchCodeSystem("http://snomed.info/sct")

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
            Predicate { it.startsWith("http://snomed.info/sct") || it.startsWith("https://dmd.nhs.uk") }
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
                    circularReferenceCheck(it,supportChain)
                } catch (e: Exception) {
                    logger.error("Failed to generate snapshot for $it", e)
                }
            }
        /*
        val ukCoreObservation : StructureDefinition = supportChain.fetchStructureDefinition("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Observation") as StructureDefinition
        supportChain.generateSnapshot(context, ukCoreObservation, ukCoreObservation.url,"https://fhir.nhs.uk/R4", ukCoreObservation.name )
        */
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

    private fun circularReferenceCheck(structureDefinition: StructureDefinition, supportChain: IValidationSupport): StructureDefinition {
        if (structureDefinition.hasSnapshot()) logger.error(structureDefinition.url + " has snapshot!!")
        structureDefinition.differential.element.forEach{
            //   ||
            if ((
                        it.id.endsWith(".partOf") ||
                        it.id.endsWith(".basedOn") ||
                        it.id.endsWith(".replaces") ||
                        it.id.contains("Condition.stage.assessment") ||
                        it.id.contains("Observation.derivedFrom") ||
                                it.id.contains("Observation.hasMember") ||
                                it.id.contains("CareTeam.encounter") ||
                        it.id.contains("CareTeam.reasonReference") ||
                        it.id.contains("ServiceRequest.encounter") ||
                        it.id.contains("ServiceRequest.reasonReference") ||
                        it.id.contains("EpisodeOfCare.diagnosis.condition") ||
                        it.id.contains("Encounter.diagnosis.condition") ||
                        it.id.contains("Encounter.reasonReference")
                                )
                && it.hasType()) {
                logger.warn(structureDefinition.url + " has circular references ("+ it.id + ")")
                it.type.forEach{
                    if (it.hasTargetProfile())
                        it.targetProfile.forEach {
                            it.value = getBase(it.value, supportChain);
                        }
                }
            }
        }
        return structureDefinition
    }

    private fun getBase(profile : String,supportChain: IValidationSupport): String? {
        val structureDefinition : StructureDefinition=
            supportChain.fetchStructureDefinition(profile) as StructureDefinition;
        if (structureDefinition.hasBaseDefinition()) {
            var baseProfile = structureDefinition.baseDefinition
            if (baseProfile.contains(".uk")) baseProfile = getBase(baseProfile, supportChain)
            return baseProfile
        }
        return null;
    }
    private fun shouldGenerateSnapshot(structureDefinition: StructureDefinition): Boolean {
        return !structureDefinition.hasSnapshot() && structureDefinition.derivation == StructureDefinition.TypeDerivationRule.CONSTRAINT
    }
}
