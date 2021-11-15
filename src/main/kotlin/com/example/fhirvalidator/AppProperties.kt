package com.example.fhirvalidator

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "hapi.fhir")
@Configuration
@EnableConfigurationProperties
class AppProperties {

    public val terminology = Terminology()

    public class Terminology {
        var server: String? = null
        var use_remote = false
        var client_id: String? = null
        var client_secret: String? = null
        var codesystems: Map<String, String>? = mapOf<String, String>()
    }
}