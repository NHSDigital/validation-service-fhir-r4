package com.example.fhirvalidator.util

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CanonicalType
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Organization
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ProfileApplierTest {
    @Test
    fun getResourcesOfType_gets_top_level_resource() {
        val patient = Patient()

        assertEquals(getResourcesOfType(patient, "Patient").size, 1)
    }

    @Test
    fun getResourcesOfType_searches_in_bundle() {
        val bundle = Bundle()
        val entry = bundle.addEntry()
        entry.resource = Patient()

        assertEquals(getResourcesOfType(bundle, "Patient").size, 1)
    }

    @Test
    fun applyProfile_adds_profile_to_each_resource() {
        val patient = Patient()
        val organization = Organization()
        val resourceList = listOf(patient, organization, patient, organization)

        val profile = CanonicalType("test.com")

        applyProfile(resourceList, profile)
        resourceList.forEach { assertEquals(it.meta.profile.size, 1) }
    }

    @Test
    fun applyProfile_clears_previous_profiles() {
        val patient = Patient()
        val resourceList = listOf(patient)

        val profile1 = CanonicalType("test1.com")
        val profile2 = CanonicalType("test2.com")

        applyProfile(resourceList, profile1)
        applyProfile(resourceList, profile2)
        resourceList.forEach { assertEquals(it.meta.profile.size, 1) }
    }
}