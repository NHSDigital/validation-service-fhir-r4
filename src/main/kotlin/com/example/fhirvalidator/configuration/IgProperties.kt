package com.example.fhirvalidator.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "ig")
class IgProperties(var packageDownload : Boolean) {

}