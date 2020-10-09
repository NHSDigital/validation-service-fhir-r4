package com.example.fhirvalidator.configuration

import com.example.fhirvalidator.model.SimplifierPackageVersion
import com.example.fhirvalidator.model.SimplifierPackageVersionListing
import com.google.gson.Gson
import mu.KLogging
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.client.RestTemplate

@Configuration
class PackageConfiguration {
    companion object : KLogging()
    class Package(var packageName: String, val version: String){}

    @Bean
    fun getPackages(): Array<NpmPackage> {
        val packages = Gson().fromJson(
                String(ClassPathResource("manifest.json").inputStream.readAllBytes()),
                Array<Package>::class.java
        ).toList()

        val packageList = emptyArray<NpmPackage>()

        packages.forEach{
            val name = "${it.packageName}-${it.version}".replace(".", "-") + ".pkg"
            packageList.plus(NpmPackage.fromPackage(ClassPathResource(name).inputStream))
        }

        return packageList
    }
}