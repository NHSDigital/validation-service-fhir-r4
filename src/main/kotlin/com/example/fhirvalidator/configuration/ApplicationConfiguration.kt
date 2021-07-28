package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.LenientErrorHandler
import ca.uhn.fhir.parser.StrictErrorHandler
import com.example.fhirvalidator.server.FHIRRestfulServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.context.support.GenericWebApplicationContext
import javax.servlet.Servlet

@Configuration
class ApplicationConfiguration {

    @Autowired
    lateinit var applicationContext: GenericWebApplicationContext

    @Bean
    fun fhirContext(): FhirContext {
        // IG is no l
        val strictErrorHandler = StrictErrorHandler()
        ///lenientErrorHandler.isErrorOnInvalidValue = false
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(strictErrorHandler)
        return fhirContext
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }



    @Bean
    fun FHIRServerR4RegistrationBean(ctx: FhirContext? ): ServletRegistrationBean<*>? {
        val registration: ServletRegistrationBean<*> = ServletRegistrationBean<Servlet?>(FHIRRestfulServer(ctx, applicationContext), "/R4/*")
        val params: MutableMap<String, String> = HashMap()
        params["FhirVersion"] = "R4"
        params["ImplementationDescription"] = "FHIR Validation Server"
        registration.initParameters = params
        registration.setName("FhirR4Servlet")
        registration.setLoadOnStartup(1)
        return registration
    }
}
