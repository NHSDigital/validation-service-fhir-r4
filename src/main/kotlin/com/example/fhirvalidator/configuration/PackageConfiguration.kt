package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import com.example.fhirvalidator.model.SimplifierPackage
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import kotlin.streams.toList


@Configuration
class PackageConfiguration(val objectMapper: ObjectMapper) {
    companion object : KLogging()

    @Bean
    fun getCoreSearchParamters(ctx: FhirContext) : Bundle? {

        // TODO could maybe get this from packages
        val u = URL("http://hl7.org/fhir/R4/search-parameters.json")
        try {
            val io: InputStream = u.openStream()
            val inputStreamReader = InputStreamReader(io, Charset.forName("UTF-8"))
            return ctx.newJsonParser().parseResource(inputStreamReader) as Bundle
        }
        catch (ex : Exception) {
            return null
        }
    }

    @Bean
    fun getPackages(): List<NpmPackage> {
        val inputStream = ClassPathResource("manifest.json").inputStream
        val packages = objectMapper.readValue(inputStream, Array<SimplifierPackage>::class.java)
        return Arrays.stream(packages)
            .map { "${it.packageName}-${it.version}.tgz" }
            .map { ClassPathResource(it).inputStream }
            .map { NpmPackage.fromPackage(it) }
            .toList()
    }

}
