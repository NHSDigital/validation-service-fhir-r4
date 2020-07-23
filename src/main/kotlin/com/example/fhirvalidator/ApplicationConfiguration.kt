package com.example.fhirvalidator

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

@Configuration
class ApplicationConfiguration {
    @Bean
    fun fhirContext(): FhirContext {
        return FhirContext.forR4()
    }

    @Bean
    fun ukDmPackage(): NpmPackage {
        //TODO - download latest package version from simplifier instead of including as a resource
        return NpmPackage.fromPackage(ClassPathResource("UK.DM.r4-0.0.18-dev.tgz").inputStream)
    }

    @Bean
    fun ukSpinePackage(): NpmPackage {
        //TODO - download latest package version from simplifier instead of including as a resource
        return NpmPackage.fromPackage(ClassPathResource("UK.Spine.r4-0.0.3-dev.tgz").inputStream)
    }

    @Bean
    fun ukCorePackage(): NpmPackage {
        //TODO - download latest package version from simplifier instead of including as a resource
        return NpmPackage.fromPackage(ClassPathResource("UK.Core.r4-1.2.0.tgz").inputStream)
    }
}