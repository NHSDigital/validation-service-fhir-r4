package com.example.fhirvalidator.service

import org.hl7.fhir.r4.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MessageDefinitionApplierTest {

    @Test
    fun applyMessageDefinition_returns_null_when_resource_is_not_message_bundle() {
        val applier = createApplier(emptyList())
        val patient = Patient()

        assertNull(applier.applyMessageDefinition(patient))
    }

    @Test
    fun applyMessageDefinition_returns_error_when_message_header_missing() {
        val applier = createApplier(emptyList())
        val bundle = Bundle().apply {
            type = Bundle.BundleType.MESSAGE
            addEntry().resource = Patient()
        }

        val outcome = applier.applyMessageDefinition(bundle)

        assertNotNull(outcome)
        assertEquals("No MessageHeader found.", outcome!!.issueFirstRep.diagnostics)
    }

    @Test
    fun applyMessageDefinition_returns_error_for_unsupported_message_type() {
        val applier = createApplier(emptyList())
        val header = createMessageHeader("urn:test", "test-event")
        val bundle = createMessageBundle(header)

        val outcome = applier.applyMessageDefinition(bundle)

        assertNotNull(outcome)
        assertEquals("Unsupported message type urn:test#test-event.", outcome!!.issueFirstRep.diagnostics)
    }

    @Test
    fun applyMessageDefinition_returns_null_when_focus_constraints_satisfied() {
        val messageDefinition = createMessageDefinition(
            system = "urn:system",
            code = "event",
            url = "http://example.com/md",
            focusComponents = listOf(createFocus("Patient", min = 1, max = "1"))
        )
        val applier = createApplier(listOf(messageDefinition))
        val header = createMessageHeader("urn:system", "event", messageDefinition.url)
        val bundle = createMessageBundle(header, Patient())

        val outcome = applier.applyMessageDefinition(bundle)

        assertNull(outcome)
    }

    @Test
    fun applyMessageDefinition_returns_issue_when_focus_count_exceeds_max() {
        val messageDefinition = createMessageDefinition(
            system = "urn:system",
            code = "event",
            url = "http://example.com/md-max",
            focusComponents = listOf(createFocus("Observation", max = "1"))
        )
        val applier = createApplier(listOf(messageDefinition))
        val header = createMessageHeader("urn:system", "event", messageDefinition.url)
        val bundle = createMessageBundle(header, Observation(), Observation())

        val outcome = applier.applyMessageDefinition(bundle)

        assertNotNull(outcome)
        assertEquals(
            "Bundle contains too many resources of type Observation. Expected at most 1.",
            outcome!!.issueFirstRep.diagnostics
        )
    }

    @Test
    fun applyMessageDefinition_returns_issue_when_focus_count_below_min() {
        val messageDefinition = createMessageDefinition(
            system = "urn:system",
            code = "event",
            url = "http://example.com/md-min",
            focusComponents = listOf(createFocus("Observation", min = 2))
        )
        val applier = createApplier(listOf(messageDefinition))
        val header = createMessageHeader("urn:system", "event", messageDefinition.url)
        val bundle = createMessageBundle(header)

        val outcome = applier.applyMessageDefinition(bundle)

        assertNotNull(outcome)
        assertEquals(
            "Bundle contains too few resources of type Observation. Expected at least 2.",
            outcome!!.issueFirstRep.diagnostics
        )
    }

    private fun createApplier(definitions: List<MessageDefinition>): MessageDefinitionApplier {
        val parser = Mockito.mock(ImplementationGuideParser::class.java)
        val applier = MessageDefinitionApplier(parser, emptyList())
        val field = MessageDefinitionApplier::class.java.getDeclaredField("messageDefinitions")
        field.isAccessible = true
        field.set(applier, definitions)
        return applier
    }

    private fun createMessageDefinition(
        system: String,
        code: String,
        url: String,
        focusComponents: List<MessageDefinition.MessageDefinitionFocusComponent>
    ): MessageDefinition {
        val messageDefinition = MessageDefinition()
        val eventCoding = messageDefinition.eventCoding
        eventCoding.system = system
        eventCoding.code = code
        messageDefinition.url = url
        focusComponents.forEach { messageDefinition.addFocus(it) }
        return messageDefinition
    }

    private fun createFocus(
        code: String,
        min: Int? = null,
        max: String? = null
    ): MessageDefinition.MessageDefinitionFocusComponent {
        val focus = MessageDefinition.MessageDefinitionFocusComponent()
        focus.code = code
        min?.let { focus.min = it }
        max?.let { focus.max = it }
        return focus
    }

    private fun createMessageHeader(
        system: String,
        code: String,
        definition: String? = null
    ): MessageHeader {
        val header = MessageHeader()
        val eventCoding = header.eventCoding
        eventCoding.system = system
        eventCoding.code = code
        definition?.let { header.definition = it }
        return header
    }

    private fun createMessageBundle(vararg resources: Resource): Bundle {
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.MESSAGE
        resources.forEach { resource ->
            bundle.addEntry().resource = resource
        }
        return bundle
    }
}
