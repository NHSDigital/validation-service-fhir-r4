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


@Configuration
open class PackageConfiguration(

  ) {
    companion object : KLogging()



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
