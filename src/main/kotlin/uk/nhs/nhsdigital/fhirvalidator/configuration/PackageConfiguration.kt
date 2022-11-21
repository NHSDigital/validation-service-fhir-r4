package uk.nhs.nhsdigital.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonElement
import mu.KLogging
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import uk.nhs.nhsdigital.fhirvalidator.model.SimplifierPackage
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import kotlin.streams.toList


@Configuration
open class PackageConfiguration(val objectMapper: ObjectMapper, val fhirServerProperties: FHIRServerProperties,
val messageProperties: MessageProperties) {
    companion object : KLogging()

    @Bean
    open fun getPackages(): List<NpmPackage> {
        val configurationInputStream = ClassPathResource("manifest.json").inputStream
        val manifest = objectMapper.readValue(configurationInputStream, Array<SimplifierPackage>::class.java)
        val packages = arrayListOf<NpmPackage>()
        for (packageNpm in manifest ) {
            var packageName= packageNpm.packageName + "-" + packageNpm.version+ ".tgz"

            var inputStream: InputStream? = null
            try {
                inputStream = ClassPathResource(packageName).inputStream
            } catch (ex : Exception) {
                if (ex.message != null) logger.info(ex.message)
            }
            if (inputStream == null) {
                val downloadedPackages = downloadPackage(packageNpm.packageName,packageNpm.version)
                packages.addAll(downloadedPackages)
            } else {
                logger.info("Using local cache for {} - {}",packageNpm.packageName, packageNpm.version)
                packages.add(NpmPackage.fromPackage(inputStream))
            }
        }
        return packages
        /*
        if (fhirServerProperties.ig != null && !fhirServerProperties.ig!!.isEmpty()) {
            return downloadPackage(fhirServerProperties.ig!!)
        }
        return Arrays.stream(packages)
            .map { "${it.packageName}-${it.version}.tgz" }
            .map { ClassPathResource(it).inputStream }
            .map { NpmPackage.fromPackage(it) }
            .toList()

         */
    }

    open fun downloadPackage(name : String, version : String) : List<NpmPackage> {
        logger.info("Downloading from AWS Cache {} - {}",name, version)
        // Try self first
        var inputStream : InputStream
        try {
            val packUrl =  "https://fhir.nhs.uk/ImplementationGuide/" + name+"-" + version
            inputStream = readFromUrl(messageProperties.getNPMFhirServer() + "/FHIR/R4/ImplementationGuide/\$package?url="+packUrl )
        } catch (ex : Exception) {
            logger.info("Package not found in AWS Cache trying simplifier "+name+ "-"+version)
            if (ex.message!=null) logger.info(ex.message)
            inputStream = readFromUrl("https://packages.simplifier.net/" + name + "/" + version)
        }
        if (inputStream == null) logger.info("Failed to download  {} - {}",name, version)
        val packages = arrayListOf<NpmPackage>()
        val npmPackage = NpmPackage.fromPackage(inputStream)

        val dependency= npmPackage.npm.get("dependencies")

        if (dependency.isJsonArray) logger.info("isJsonArray")
        if (dependency.isJsonObject) {
            val obj = dependency.asJsonObject
            val entrySet: Set<Map.Entry<String?, JsonElement?>> = obj.entrySet()
            for (entry in entrySet) {
                logger.info(entry.key + " version =  " + entry.value)
                if (entry.key != "hl7.fhir.r4.core") {
                    val version = entry.value?.asString?.replace("\"","")
                    if (entry.key != null && version != null) {
                        val packs = downloadPackage(entry.key!!, version)
                        if (packs.size > 0) {
                            for (pack in packs) {
                                packages.add(pack)
                            }
                        }
                    }
                }
            }
        }
        packages.add(npmPackage)
        if (dependency.isJsonNull) logger.info("isNull")
        if (dependency.isJsonPrimitive) logger.info("isJsonPrimitive")

        return packages
    }

    fun readFromUrl(url: String): InputStream {

        var myUrl: URL =  URL(url)

        var retry = 2
        while (retry > 0) {
            val conn = myUrl.openConnection() as HttpURLConnection


            conn.requestMethod = "GET"

            try {
                conn.connect()
                return conn.inputStream
            } catch (ex: FileNotFoundException) {
                null
            } catch (ex: IOException) {
                retry--
                if (retry < 1) throw UnprocessableEntityException(ex.message)

            }
        }
        throw UnprocessableEntityException("Number of retries exhausted")
    }

    @Bean
    open fun getCoreSearchParamters(@Qualifier("R4") ctx: FhirContext) : Bundle? {
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
}
