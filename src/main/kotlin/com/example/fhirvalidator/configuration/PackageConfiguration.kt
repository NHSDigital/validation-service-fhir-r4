package com.example.fhirvalidator.configuration

import com.example.fhirvalidator.model.SimplifierPackage
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.utilities.cache.FilesystemPackageCacheManager
import org.hl7.fhir.utilities.cache.NpmPackage
import org.hl7.fhir.utilities.cache.ToolsVersion
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.InputStream
import java.net.URL
import java.util.*
import kotlin.streams.toList

@Configuration
class PackageConfiguration(val objectMapper: ObjectMapper) {
    companion object : KLogging()

    @Bean
    fun getPackages(applicationProperties: IgProperties): List<NpmPackage> {
        val inputStream = ClassPathResource("manifest.json").inputStream
        val packages = objectMapper.readValue(inputStream, Array<SimplifierPackage>::class.java)
        var packageList : MutableList<NpmPackage> = ArrayList<NpmPackage>();
        packageList.addAll(Arrays.stream(packages)
            .filter{applicationProperties.packageDownload == false}
            .map { "${it.packageName}-${it.version}.tgz" }
            .map { ClassPathResource(it).inputStream }
            .map { NpmPackage.fromPackage(it) }
            .toList())
        val pcm = FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
        packageList.addAll(Arrays.stream(packages)
            .filter{applicationProperties.packageDownload == true}
            .map { getGetPackage(pcm, it.packageName,  it.version, applicationProperties.packageDownload) }
            .toList())

        return packageList
    }

    fun getGetPackage(pcm : FilesystemPackageCacheManager, packageName : String, version : String, download : Boolean? ) : NpmPackage {
        if (download != null && !download) {
            return pcm.loadPackage( packageName,  version)
        } else {
            return getPackageFromUrl(pcm,packageName, version )
        }
    }

    @Throws(Exception::class)
    private fun getPackageFromUrl(pcm: FilesystemPackageCacheManager, packageName: String, version: String ): NpmPackage {
        val stream: InputStream?
        try {
            val url = "https://packages.simplifier.net/${packageName}/-/${packageName}-${version}.tgz"
            println(url)
            if (url.contains(".tgz")) {
                stream = fetchFromUrlSpecific(url, true)
                if (stream != null) {
                    return pcm.addPackageToCache(packageName, version, stream, url)
                }
            }
        } catch (ex: Exception) {
            println("ERROR")
        }
        throw FHIRException("Package not found")
    }

    @Throws(FHIRException::class)
    private fun fetchFromUrlSpecific(source: String, optional: Boolean): InputStream? {
        return try {
            val url = URL(source)
            val c = url.openConnection()
            c.getInputStream()
        } catch (var5: Exception) {
            if (optional) {
                null
            } else {
                throw FHIRException(var5.message, var5)
            }
        }
    }
}
