package com.example.fhirvalidator.model

import java.util.*

data class SimplifierPackageVersionListing(
        val name: String,
        val description: String,
        val versions: LinkedHashMap<String, SimplifierPackageVersion>
)

data class SimplifierPackageVersion(
        val name: String,
        val version: String,
        val description: String,
        val url: String
)