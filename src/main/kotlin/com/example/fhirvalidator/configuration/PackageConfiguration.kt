package com.example.fhirvalidator.configuration

import com.example.fhirvalidator.model.SimplifierPackage
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.util.*

@Configuration
class PackageConfiguration(val objectMapper: ObjectMapper) {
    private val logger = KotlinLogging.logger {} 

    fun baseGetPackages(manifestFile: String): List<NpmPackage>  {
        val inputStream = ClassPathResource(manifestFile).inputStream
        val packages = objectMapper.readValue(inputStream, Array<SimplifierPackage>::class.java)
        return Arrays.stream(packages)
            .map { "${it.packageName}-${it.version}.tgz" }
            .map { ClassPathResource(it).inputStream }
            .map { NpmPackage.fromPackage(it) }
            .toList()
    }

    @Bean("npmPackages")
    fun getPackages(): List<NpmPackage> {
        return baseGetPackages("manifest.json")
    }

    @Bean("npmPackagesNext")
    fun getNextPackages(): List<NpmPackage> {
        return baseGetPackages("manifest.next.json")
    }
}
