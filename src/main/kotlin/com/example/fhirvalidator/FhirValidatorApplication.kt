package com.example.fhirvalidator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan

@SpringBootApplication
@ServletComponentScan
class FhirValidatorApplication

fun main(args: Array<String>) {
    runApplication<FhirValidatorApplication>(*args)
}
