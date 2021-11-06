package com.example.fhirvalidator.model

data class ValidationConfig(var terminologyServer: String,
                            var useRemoteTerminology : Boolean,
                            var clientId : String,
                            var clientSecret : String)
