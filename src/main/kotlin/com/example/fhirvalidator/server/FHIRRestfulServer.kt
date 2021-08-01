package com.example.fhirvalidator.server

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.example.fhirvalidator.provider.CapabilityStatementProvider
import com.example.fhirvalidator.provider.ValidateProvider
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.context.ApplicationContext
import java.util.*

class FHIRRestfulServer (fhirContext: FhirContext, applicationContext: ApplicationContext, npmPackages: List<NpmPackage>) : RestfulServer(fhirContext) {


    val applicationContext = applicationContext

    val npmPackages = npmPackages

    @SuppressWarnings("unchecked")
    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val plainProviders: MutableList<Any?> = ArrayList()
        plainProviders.add(applicationContext.getBean(ValidateProvider::class.java))
        registerProviders(plainProviders)

        serverConformanceProvider = CapabilityStatementProvider(this, npmPackages )

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }

}
