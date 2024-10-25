package com.example.fhirvalidator.controller

import org.hl7.fhir.r4.model.OperationOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.opentest4j.AssertionFailedError
import java.io.File
import java.util.stream.Stream
import javax.print.attribute.standard.Severity

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FhirExamplesTest {
    val loader = Thread.currentThread().getContextClassLoader()

    @Autowired
    lateinit var testValidateController: ValidateController

    fun getExampleFhirFiles(): Stream<Arguments> {
        val dir = File( loader.getResource("examples").file)
        val exampleFiles = dir.walk()
            .filter { it.isFile }
            .toList()
            .map { Arguments.of(Named.of("parseAndValidateResource should return no errors for file ${it.absolutePath}", it)) }
            .stream()
        return exampleFiles
    }

    @DisplayName("Test all valid example files")
    @ParameterizedTest
    @MethodSource("getExampleFhirFiles")
    fun testFhirExamples(exampleFile: File) {
        println("Reading file ${exampleFile.absolutePath}")
        val lines = exampleFile.bufferedReader().readLines()
        val fileContent = lines.joinToString(" ")
        val actualResult = testValidateController.parseAndValidateResource(fileContent)
        for (issue in actualResult.issue) {
            if (issue.severity.equals(OperationOutcome.IssueSeverity.ERROR)) {
                throw AssertionFailedError("Error found checking file ${exampleFile.absolutePath}. Error: ${issue.diagnostics}")
            }
        }
    }
}
