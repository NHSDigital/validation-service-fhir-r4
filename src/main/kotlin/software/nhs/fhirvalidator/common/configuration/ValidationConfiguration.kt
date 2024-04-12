package software.nhs.fhirvalidator.common.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.validation.FhirValidator
import software.nhs.fhirvalidator.common.service.ImplementationGuideParser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ValidationConfiguration(private val implementationGuideParser: ImplementationGuideParser) {
    private val logger = KotlinLogging.logger {} 

    @Bean
    fun validator(fhirContext: FhirContext, instanceValidator: FhirInstanceValidator): FhirValidator {
        return fhirContext.newValidator().registerValidatorModule(instanceValidator)
    }

    @Bean
    fun instanceValidator(supportChain: ValidationSupportChain): FhirInstanceValidator {
        return FhirInstanceValidator(supportChain)
    }

    @Bean("SupportChain")
    fun validationSupportChain(
        fhirContext: FhirContext,
        terminologyValidationSupport: InMemoryTerminologyServerValidationSupport,
        npmPackages: List<NpmPackage>
    ): ValidationSupportChain {
        val supportChain = ValidationSupportChain(
            DefaultProfileValidationSupport(fhirContext),
            CommonCodeSystemsTerminologyService(fhirContext),
            terminologyValidationSupport,
            SnapshotGeneratingValidationSupport(fhirContext)
        )
        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport)
            .forEach(supportChain::addValidationSupport)
        generateSnapshots(supportChain)
        supportChain.fetchCodeSystem("http://snomed.info/sct")
        return supportChain
    }

    @Bean
    fun terminologyValidationSupport(fhirContext: FhirContext): InMemoryTerminologyServerValidationSupport {
        return object : InMemoryTerminologyServerValidationSupport(fhirContext) {
            override fun validateCodeInValueSet(
                theValidationSupportContext: ValidationSupportContext?,
                theOptions: ConceptValidationOptions?,
                theCodeSystem: String?,
                theCode: String?,
                theDisplay: String?,
                theValueSet: IBaseResource
            ): IValidationSupport.CodeValidationResult? {
                val valueSetUrl = CommonCodeSystemsTerminologyService.getValueSetUrl(fhirContext, theValueSet)

                if (valueSetUrl == "https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"
                    || valueSetUrl == "https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationDispense-Code"
                    || valueSetUrl == "https://fhir.hl7.org.uk/ValueSet/UKCore-MedicationCode") {
                    return IValidationSupport.CodeValidationResult()
                        .setSeverity(IValidationSupport.IssueSeverity.WARNING)
                        .setMessage("Unable to validate medication codes")
                }

                return super.validateCodeInValueSet(
                    theValidationSupportContext,
                    theOptions,
                    theCodeSystem,
                    theCode,
                    theDisplay,
                    theValueSet
                )
            }
        }
    }

    fun generateSnapshots(supportChain: IValidationSupport) {
        val structureDefinitions = supportChain.fetchAllStructureDefinitions<StructureDefinition>() ?: return
        val context = ValidationSupportContext(supportChain)

        structureDefinitions
            .filter { shouldGenerateSnapshot(it) }
            .parallelStream()
            .forEach {
                try {
                    circularReferenceCheck(it,supportChain)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to generate snapshot for $it" }
                }
            }
        structureDefinitions
            .filter { shouldGenerateSnapshot(it) }
            .forEach {
                try {
                    supportChain.generateSnapshot(context, it, it.url, "https://fhir.nhs.uk/R4", it.name)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to generate snapshot for $it" }
                }
            }
    }

    private fun circularReferenceCheck(structureDefinition: StructureDefinition, supportChain: IValidationSupport): StructureDefinition {
        if (structureDefinition.hasSnapshot()) logger.error { structureDefinition.url + " has snapshot!!" }
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
                it.id.contains("Encounter.reasonReference") ||
                it.id.contains("Encounter.appointment")
            ) && it.hasType()) {
                logger.warn { structureDefinition.url + " has circular references ("+ it.id + ")" }
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
