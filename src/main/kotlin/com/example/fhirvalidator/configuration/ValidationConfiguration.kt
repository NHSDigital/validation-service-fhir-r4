package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.rest.client.api.IClientInterceptor
import ca.uhn.fhir.rest.client.api.IHttpRequest
import ca.uhn.fhir.rest.client.api.IHttpResponse
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.ImplementationGuideParser
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.MedicationRequest
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import java.util.*


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
        optionalRemoteTerminologySupport: Optional<RemoteTerminologyServiceValidationSupport>,
        npmPackages: List<NpmPackage>
    ): ValidationSupportChain {
        val supportChain = ValidationSupportChain(
            DefaultProfileValidationSupport(fhirContext),
            SnapshotGeneratingValidationSupport(fhirContext),
            CommonCodeSystemsTerminologyService(fhirContext),
            InMemoryTerminologyServerValidationSupport(fhirContext)
        )

        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport)
            .forEach(supportChain::addValidationSupport)

        if (optionalRemoteTerminologySupport.isPresent) {
            val remoteTerminologySupport = optionalRemoteTerminologySupport.get()
            //val cachingRemoteTerminologySupport = CachingValidationSupport(remoteTerminologySupport)
            supportChain.addValidationSupport(remoteTerminologySupport)
        }

        generateSnapshots(supportChain)

        return supportChain

    }

    @Bean
    @ConditionalOnProperty("terminology.url")
    fun remoteTerminologyServiceValidationSupport(
        fhirContext: FhirContext,
        optionalAuthorizedClient: Optional<OAuth2AuthorizedClient>
    ): RemoteTerminologyServiceValidationSupport {
        logger.info("Using remote terminology server at ${terminologyValidationProperties.url}")
        val validationSupport = RemoteTerminologyServiceValidationSupport(fhirContext)
        validationSupport.setBaseUrl(terminologyValidationProperties.url)

        if (optionalAuthorizedClient.isPresent) {
            val authorizedClient = optionalAuthorizedClient.get()
            validationSupport.addClientInterceptor(
                object : IClientInterceptor {
                    override fun interceptRequest(request: IHttpRequest?) {
                        logger.info { "Intercepted request to ${request?.uri}" }
                        val accessToken = authorizedClient.accessToken.tokenValue
                        request?.addHeader("Authorization", "Bearer $accessToken")
                    }
                    override fun interceptResponse(theResponse: IHttpResponse?) {}
                }
            )
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
