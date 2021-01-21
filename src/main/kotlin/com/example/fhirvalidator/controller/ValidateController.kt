package com.example.fhirvalidator.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.CapabilityStatementApplier
import com.example.fhirvalidator.service.MessageDefinitionApplier
import com.example.fhirvalidator.util.createOperationOutcome
import com.github.benmanes.caffeine.cache.LoadingCache
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.common.hapi.validation.validator.VersionSpecificWorkerContextWrapper
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class ValidateController(
        private val fhirContext: FhirContext,
        private val validator: FhirValidator,
        //private val instanceValidator: FhirInstanceValidator,
        private val messageDefinitionApplier: MessageDefinitionApplier,
        private val capabilityStatementApplier: CapabilityStatementApplier
) {
    companion object : KLogging()

    @PostMapping("/\$validate", produces = ["application/json", "application/fhir+json"])
    fun validate(
        @RequestBody input: String,
        @RequestHeader("x-request-id", required = false) requestId: String?
    ): String {
        requestId?.let { logger.info("started processing message $it") }
        val result = parseAndValidateResource(input)
        requestId?.let { logger.info("finished processing message $it") }
        return fhirContext.newJsonParser().encodeResourceToString(result)
    }

//    fun logCacheContents() {
//        //Horrible reflection stuff
//        val workerContextField = FhirInstanceValidator::class.java.getDeclaredField("myWrappedWorkerContext")
//        workerContextField.trySetAccessible()
//        val resourceCacheField = VersionSpecificWorkerContextWrapper::class.java.getDeclaredField("myFetchResourceCache")
//        resourceCacheField.trySetAccessible()
//        val resourceKeyClass = VersionSpecificWorkerContextWrapper::class.java.declaredClasses.first { it.simpleName == "ResourceKey" }
//        val resourceNameField = resourceKeyClass.getDeclaredField("myResourceName")
//        resourceNameField.trySetAccessible()
//        val resourceUriField = resourceKeyClass.getDeclaredField("myUri")
//        resourceUriField.trySetAccessible()
//
//        val workerContext = workerContextField.get(instanceValidator) as VersionSpecificWorkerContextWrapper?
//        val resourceCache = workerContext?.let { resourceCacheField.get(it) as LoadingCache<*, *>? }
//        resourceCache?.let {
//            logger.info("Cache contents:")
//            it.asMap().keys.forEach { logger.info { "${resourceNameField.get(it)} - ${resourceUriField.get(it)}" } }
//        }
//    }
//
//    val thing = Timer("cache contents logger").scheduleAtFixedRate(object: TimerTask() {
//        override fun run() {
//            logCacheContents()
//        }
//    }, 1000, 30000)

    private fun parseAndValidateResource(input: String): IBaseOperationOutcome {
        return try {
            val inputResource = fhirContext.newJsonParser().parseResource(input)
            val messageDefinitionErrors = messageDefinitionApplier.applyMessageDefinition(inputResource)
            capabilityStatementApplier.applyCapabilityStatementProfiles(inputResource)
            messageDefinitionErrors ?: validator.validateWithResult(inputResource).toOperationOutcome()
        } catch (e: DataFormatException) {
            logger.error("Caught parser error", e)
            createOperationOutcome("Invalid JSON", null)
        }
    }
}
