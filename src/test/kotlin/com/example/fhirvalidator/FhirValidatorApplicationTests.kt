package com.example.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import com.example.fhirvalidator.service.ImplementationGuideParser
import mu.KLogging
import org.assertj.core.api.Assertions
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.utilities.cache.NpmPackage
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.*
import java.io.InputStreamReader
import java.io.Reader


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FhirValidatorApplicationTests(@Autowired val restTemplate: TestRestTemplate
) {

    @LocalServerPort
    private val port = 0

    lateinit var ctx : FhirContext

    companion object : KLogging()

    init {
        ctx = FhirContext.forR4()
    }

    private fun getFileResourceXML(fileName: String): IBaseResource? {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(
            "FHIRExamples/$fileName"
        )
        Assertions.assertThat(inputStream != null).isEqualTo(true)
        val reader: Reader = InputStreamReader(inputStream)
        return ctx.newXmlParser().parseResource(reader)
    }

    private fun getFileResourceJSON(fileName: String): IBaseResource? {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(
            "FHIRExamples/$fileName"
        )
        Assertions.assertThat(inputStream != null).isEqualTo(true)
        val reader: Reader = InputStreamReader(inputStream)
        return ctx.newJsonParser().parseResource(reader)
    }

    private fun validateResource(json: String,mediaType :  MediaType ): ResponseEntity<*>? {
        val headers = HttpHeaders()
        headers.contentType = mediaType
        val entity = HttpEntity<Any>(json, headers)
        return restTemplate.exchange(
            "http://localhost:$port/R4/\$validate", HttpMethod.POST, entity,
            String::class.java
        )
    }

    private fun hasErrors(operationOutcome: OperationOutcome) : Boolean {
        operationOutcome.issue.forEach{
            if (it.severity == OperationOutcome.IssueSeverity.ERROR) return true
        }
        return false
    }
    private fun getResource(response : ResponseEntity<*>?): IBaseResource? {
        if (response != null && response.body != null && response.body is String) {
            return ctx.newJsonParser().parseResource(response.body as String?)
        }
        return null
    }

    @Test
    @Throws(Exception::class)
    fun validateMedicationRequestMissingSNOMEDMedcation() {

        val resource = getFileResourceXML("MedicationRequest-missingSNOMEDMedcation.xml")
        val out: ResponseEntity<*>? =
            validateResource(ctx.newJsonParser().encodeResourceToString(resource), MediaType.APPLICATION_XML)

        Assertions.assertThat(out).isNotNull
        val responseResource = getResource(out)
        Assertions.assertThat(responseResource).isInstanceOf(OperationOutcome::class.java)
        if (responseResource is OperationOutcome) {

            Assertions.assertThat(hasErrors(responseResource)).isEqualTo(true)
        }
    }

    @Test
    @Throws(Exception::class)
    fun validateMedicationRequestDuplicateMedicationReferences() {

        val resource = getFileResourceJSON("MedicationRequest-bothMedicationReferenceAndCodeableConcept.json")
        val out: ResponseEntity<*>? =
            validateResource(ctx.newJsonParser().encodeResourceToString(resource), MediaType.APPLICATION_JSON)

        Assertions.assertThat(out).isNotNull
        val responseResource = getResource(out)
        Assertions.assertThat(responseResource).isInstanceOf(OperationOutcome::class.java)
        if (responseResource is OperationOutcome) {

            Assertions.assertThat(hasErrors(responseResource)).isEqualTo(true)
        }
    }

    @Test
    fun contextLoads() {
    }


}
