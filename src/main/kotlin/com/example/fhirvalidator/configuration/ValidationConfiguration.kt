package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.model.ValidationConfig
import com.example.fhirvalidator.service.ImplementationGuideParser
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class ValidationConfiguration(private val implementationGuideParser: ImplementationGuideParser) {
    companion object : KLogging()


    @Bean
    fun validator(fhirContext: FhirContext, instanceValidator: FhirInstanceValidator): FhirValidator {
        return fhirContext.newValidator().registerValidatorModule(instanceValidator)
    }

    @Bean
    fun instanceValidator(supportChain: ValidationSupportChain): FhirInstanceValidator {
        return FhirInstanceValidator(CachingValidationSupport(supportChain))
    }

    @Bean
    fun validationSupportChain(
        fhirContext: FhirContext,
        terminologyValidationSupport: InMemoryTerminologyServerValidationSupport,
        npmPackages: List<NpmPackage>,
        validationConfig : ValidationConfig
    ): ValidationSupportChain {
        val supportChain = ValidationSupportChain(
            DefaultProfileValidationSupport(fhirContext),
            CommonCodeSystemsTerminologyService(fhirContext))

        if (validationConfig.useRemoteTerminology && !validationConfig.terminologyServer.isEmpty()) {
            // May need to review this. Hopefully this captures the common use cases.
            supportChain.addValidationSupport(InMemoryTerminologyServerValidationSupport(fhirContext))
            val remoteTerminologyServer = RemoteTerminologyServiceValidationSupport(fhirContext)
            remoteTerminologyServer.setBaseUrl(validationConfig.terminologyServer)
            remoteTerminologyServer.addClientInterceptor(BearerTokenAuthInterceptor(validationConfig.accessToken));
            supportChain.addValidationSupport(remoteTerminologyServer)
        } else {
            supportChain.addValidationSupport(terminologyValidationSupport)
        }
        supportChain.addValidationSupport(SnapshotGeneratingValidationSupport(fhirContext))
        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport)
            .forEach(supportChain::addValidationSupport)
        generateSnapshots(supportChain)

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
                val valueSetUrl = CommonCodeSystemsTerminologyService.getValueSetUrl(theValueSet)

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
}
