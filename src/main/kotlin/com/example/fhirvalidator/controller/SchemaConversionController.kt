package com.example.fhirvalidator.controller

import com.example.fhirvalidator.service.SchemaConversionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SchemaConversionController(
    val schemaConversionService: SchemaConversionService
) {
    @GetMapping("list")
    fun list(): String {
        return schemaConversionService.listCoolThings()
    }

    @GetMapping("convert")
    fun convert(@RequestParam name: String): String {
        return schemaConversionService.doSomethingCool(name)
    }
}
