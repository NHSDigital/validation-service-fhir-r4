package software.nhs.fhirvalidator.webserver.configuration

import software.nhs.fhirvalidator.common.model.SimplifierPackage
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.util.*
import kotlin.streams.toList

@Configuration
class PackageConfiguration(val objectMapper: ObjectMapper) {
    private val logger = KotlinLogging.logger {} 

    @Bean
    fun npmPackages(): List<NpmPackage> {
        val inputStream = ClassPathResource("manifest.json").inputStream
        val packages = objectMapper.readValue(inputStream, Array<SimplifierPackage>::class.java)
        return Arrays.stream(packages)
            .map { "${it.packageName}-${it.version}.tgz" }
            .map { ClassPathResource(it).inputStream }
            .map { NpmPackage.fromPackage(it) }
            .toList()
    }
}
