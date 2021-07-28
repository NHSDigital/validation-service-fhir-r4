package com.example.fhirvalidator.controller

import com.example.fhirvalidator.model.SchemaOrReference
import com.example.fhirvalidator.service.SchemaConversionService
import org.springframework.web.bind.annotation.*

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

    @GetMapping("convert2")
    fun convert2(@RequestParam name: String): SchemaOrReference? {
        return schemaConversionService.doSomethingCooler(name)
    }

    @GetMapping("convert3")
    fun convert3(): Map<String, SchemaOrReference> {
        return schemaConversionService.doSomethingCoolest()
    }
}
