package com.example.fhirvalidator.server

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.example.fhirvalidator.provider.ValidateProvider
import org.springframework.context.ApplicationContext
import java.util.*
import kotlin.collections.ArrayList

class FHIRRestfulServer (theCtx: FhirContext, applicationContext: ApplicationContext) : RestfulServer(theCtx) {

    val ctx= theCtx;
    val applicationContext = applicationContext

    @SuppressWarnings("unchecked")
    override fun initialize() {
        super.initialize()
        fhirContext = ctx;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val plainProviders: MutableList<Any?> = ArrayList()
        plainProviders.add(applicationContext.getBean(ValidateProvider::class.java))
        registerProviders(plainProviders)

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }

}
