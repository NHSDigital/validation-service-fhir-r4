package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
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
        val strictErrorHandler = StrictErrorHandler()
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(strictErrorHandler)
        return fhirContext
    }

    @Bean
    fun fhirServerR4Registration(
        fhirRestfulServer: FHIRRestfulServer
    ): ServletRegistrationBean<Servlet> {
        val registration: ServletRegistrationBean<Servlet> = ServletRegistrationBean(
            fhirRestfulServer, "/R4/*"
        )
        registration.setName("fhirR4Servlet")
        registration.setLoadOnStartup(1)
        return registration
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
