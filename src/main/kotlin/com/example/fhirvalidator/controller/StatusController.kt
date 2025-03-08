package com.example.fhirvalidator.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController {
    @GetMapping("_status")
    fun validate():  Map<String, String> {
        val validatorVersion = System.getenv("validatorVersion") ?: "unknown"
        val commitSha = System.getenv("commitSha") ?: "unknown"

        val map = hashMapOf<String, String>()
        map.put("status", "Validator is alive");
        map.put("validatorVersion", validatorVersion);
        map.put("commitSha", commitSha);
        return map
    }
}
