package com.example.fhirvalidator.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class StatusController {
    @GetMapping("_status")
    fun validate(): String {
        return "Validator is alive"
    }
}

/*
@Component
class StatusController {

    @Operation(name = "status", manualResponse=true, manualRequest=true)
    @Throws(IOException::class)
    fun validate(theServletRequest: HttpServletRequest, theServletResponse: HttpServletResponse) {
        //val contentType = theServletRequest.contentType
        //val bytes: ByteArray = IOUtils.toByteArray(theServletRequest.inputStream)
        theServletResponse.contentType = "text/plain"
        theServletResponse.writer.write("Validator is alive")
        theServletResponse.writer.close()
    }
}

 */
