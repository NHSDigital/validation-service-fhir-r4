package uk.nhs.nhsdigital.fhirvalidator.util

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.StringBuilder


class FHIRExamples {
    public fun loadExample(fileName :String, ctx : FhirContext): JsonNode {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream("Examples/"+fileName)

        val jsonStrings = inputStream.bufferedReader().readLines()
        var sb = StringBuilder()
        for (str in jsonStrings) sb.append(str)
        return ObjectMapper().readTree(sb.toString())

    }

}
