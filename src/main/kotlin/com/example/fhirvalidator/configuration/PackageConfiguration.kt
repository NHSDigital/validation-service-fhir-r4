package com.example.fhirvalidator.configuration

import com.example.fhirvalidator.model.SimplifierPackageVersion
import com.example.fhirvalidator.model.SimplifierPackageVersionListing
import mu.KLogging
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.client.RestTemplate

@Configuration
class PackageConfiguration {
    companion object : KLogging()

    @Bean
    fun ukDmPackage(restTemplate: RestTemplate): NpmPackage? {
        return downloadSimplifierPackageLatestVersion(restTemplate, "UK.DM.r4")
    }

    @Bean
    fun ukSpinePackage(restTemplate: RestTemplate): NpmPackage? {
        return downloadSimplifierPackageLatestVersion(restTemplate, "UK.Spine.r4")
    }

    @Bean
    fun ukCorePackage(restTemplate: RestTemplate): NpmPackage? {
        return downloadSimplifierPackageLatestVersion(restTemplate, "UK.Core.r4")
    }

    private fun downloadSimplifierPackageLatestVersion(restTemplate: RestTemplate, packageName: String): NpmPackage? {
        logger.info("Downloading manifest for package $packageName")
        val manifest = restTemplate.getForObject("https://packages.simplifier.net/$packageName", SimplifierPackageVersionListing::class.java)
        val latestVersion = manifest?.versions?.values?.last()
        return latestVersion?.let { downloadSimplifierPackage(restTemplate, latestVersion) }
    }

    private fun downloadSimplifierPackage(restTemplate: RestTemplate, version: SimplifierPackageVersion): NpmPackage? {
        return downloadSimplifierPackage(restTemplate, version.name, version.version)
    }

    private fun downloadSimplifierPackage(restTemplate: RestTemplate, packageName: String, packageVersion: String): NpmPackage? {
        logger.info("Downloading package $packageName version $packageVersion")
        val responseEntity = restTemplate.getForEntity("https://packages.simplifier.net/$packageName/$packageVersion", Resource::class.java)
        return responseEntity.body?.inputStream?.let { NpmPackage.fromPackage(it) }
    }
}