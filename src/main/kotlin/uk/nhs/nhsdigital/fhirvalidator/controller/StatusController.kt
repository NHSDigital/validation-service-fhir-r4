package uk.nhs.nhsdigital.fhirvalidator.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
class StatusController {
    @GetMapping("_status")
    fun validate(): String {
        return "Validator is alive"
    }
}
