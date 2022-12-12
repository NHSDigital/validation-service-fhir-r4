package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.model.api.IElement
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.CodingSupport
import uk.nhs.nhsdigital.fhirvalidator.service.OpenAPIParser
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible


@Component
class FHIRtoTextProvider(@Qualifier("R4") private val fhirContext: FhirContext,
                         private val supportChain: ValidationSupportChain,
                         private val validationSupportContext: ValidationSupportContext,
                         private val codingSupport: CodingSupport
) {

    var sdf = SimpleDateFormat("dd/MM/yyyy HH:mm")
    @Operation(name = "\$convertToText", idempotent = true, manualResponse = true)
    fun convertOpenAPI(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        @ResourceParam resource: IBaseResource?
    ) {

        servletResponse.setContentType("text/plain")
        servletResponse.setCharacterEncoding("UTF-8")
        servletResponse.writer.write("")
        if (resource != null) {
           // if (resource is Bundle) throw UnprocessableEntityException("This server does not support FHIR Bundle or collections, please try again with a single resource.")
           val result = processObject(resource)
            servletResponse.writer.write(result)
        }

        servletResponse.writer.flush()
        return
    }
    fun processObject(resource: Any) : String {


        val stringBuilder = StringBuilder()

        if (resource is Meta) return ""


        if (resource is Bundle && (resource as Bundle).type.name.equals("MESSAGE")) {
            for (entry in (resource as Bundle).entry) {
                if (entry.hasResource() && entry.resource is MessageHeader) {
                        val messsageHeader = entry.resource as MessageHeader
                        stringBuilder.append(processObject(messsageHeader))
                        if (messsageHeader.hasFocus()) {
                            for (focus in messsageHeader.focus) {
                                if (focus.resource != null) {
                                    stringBuilder.append(processObject(focus.resource))
                                }
                        }
                    }
                }
            }
        }
        if (resource is Reference) {
            val reference = resource as Reference
            var str = ""
            if (reference.resource != null) {
                str += getReferenceSummary(reference.resource)
            }
            if (reference.hasDisplay()) str += reference.display + ' '
            if (reference.hasIdentifier()) {
                val identifier = reference.identifier as Identifier
                str += "("
                if (identifier.hasSystem()) str += getFieldName(identifier.system) + " "
                if (identifier.hasValue() && !identifier.value.startsWith("urn:uuid:")) str += identifier.value
                str += ")"
            }
            stringBuilder.append(str)
        }
        if (resource is Identifier) {
            val identifier = resource as Identifier
            var str = ""
            if (identifier.hasSystem()) str += getFieldName(identifier.system) + " "
            if (identifier.hasValue()) {
                str += identifier.value
                if (!identifier.value.startsWith("urn:uuid:")) {
                    stringBuilder.append(str)
                }
            } else {
                stringBuilder.append(str)
            }
        }
        if (resource is Extension) {
            val extension = resource as Extension
            var str = getFieldName(extension.url)
            if (extension.hasValue()) {
                str += " " + processObject(extension.value)
            }
            stringBuilder.append(str)
        }
        if (resource is Coding) {
                    val code = resource as Coding
                    var str = ""
                    var lookupCodeResult :IValidationSupport.LookupCodeResult? = null
                    if (code.hasDisplay()) {
                        str = code.display
                    } else {
                        if (code.hasSystem() && code.hasCode()) {
                            lookupCodeResult = codingSupport.lookupCode( code.system, code.code)
                            if (lookupCodeResult!=null) {
                                if (lookupCodeResult.codeDisplay != null) {
                                    str = lookupCodeResult.codeDisplay
                                }
                            }
                        }
                    }
                    if (code.hasSystem() || code.hasCode()) {
                        str += " ("
                        if (code.hasSystem()) {
                            if (lookupCodeResult != null && lookupCodeResult.codeSystemDisplayName != null) {
                                str += lookupCodeResult.codeSystemDisplayName + " "
                            } else {
                                str += getFieldName(code.system) + " "
                            }
                        }
                        if (code.hasCode()) str += code.code
                        str += ")"
                    }

                    stringBuilder.append(str)

                }
        if (resource is CodeableConcept) {
                    val concept = resource as CodeableConcept
                    var str = ""
                    if (concept.hasText()) str += concept.text
                    if (concept.hasCoding()) {
                        for(code in concept.coding) {
                            var lookupCodeResult :IValidationSupport.LookupCodeResult? = null
                            if (code.hasDisplay()) {
                                str = code.display
                            } else {
                                if (code.hasSystem() && code.hasCode()) {
                                    lookupCodeResult = codingSupport.lookupCode( code.system, code.code)
                                    if (lookupCodeResult!=null) {
                                        if (lookupCodeResult.codeDisplay != null) {
                                            str = lookupCodeResult.codeDisplay
                                        }
                                    }
                                }
                            }
                            if (code.hasSystem() || code.hasCode()) {
                                str += " ("
                                if (lookupCodeResult != null && lookupCodeResult.codeSystemDisplayName != null) {
                                    str += lookupCodeResult.codeSystemDisplayName + " "
                                } else {
                                    str += getFieldName(code.system) + " "
                                }
                                if (code.hasCode()) str += code.code
                                str += ")"
                            }
                        }
                    }
                    stringBuilder.append(str)
                }

        if (resource is StringType) {
            var str = (resource as StringType).value
            stringBuilder.append(str )
        }
        if (resource is DecimalType) {
            var str = (resource as DecimalType).value
            stringBuilder.append(str )
        }
        if (resource is PositiveIntType) {
            var str = (resource as PositiveIntType).value
            stringBuilder.append(str )
        }
        if (resource is BooleanType) {
            var str = (resource as BooleanType).value
            stringBuilder.append(str )
        }
        if (resource is IdType) {
            return ""
        }
        if (resource is CanonicalType) {
            var str = (resource as CanonicalType).value
            stringBuilder.append(getFieldName(str))
            return stringBuilder.toString()
        }
        if (resource is UriType) {
            var str = (resource as UriType).value
            stringBuilder.append(getFieldName(str))
            return stringBuilder.toString()
        }
        if (resource is Period) {
            var str = ""
            var period = resource as Period
            if (period.hasStart()) {
                str += " start "+sdf.format(period.start)
            }
            if (period.hasEnd()) {
                str += " end "+sdf.format(period.end)
            }
            stringBuilder.append(str )
        }
        if (resource is DateTimeType) {
            var str = ""
            var period = resource as DateTimeType

            str += " date "+sdf.format(period.value)

            stringBuilder.append(str )
        }
        if (resource::class.simpleName.equals("Enumeration")) {
            var str = ""
            var enum = resource as Enumeration<*>
            stringBuilder.append(enum.code)
        }
        if (resource is Quantity) {
            var quantity = resource as Quantity
            var str=""
            if (quantity.hasValue()) str += (quantity.value).toString() + " "
            if (quantity.hasUnit()) str += quantity.unit + " "
            if (quantity.hasCode()) str += quantity.code + " "
            if (quantity.hasSystem()) str += getFieldName(quantity.system) + " "
            stringBuilder.append(str )
        }
        // if FHIR type processed then return
        if (stringBuilder.length>0) return stringBuilder.toString()


        if (resource is Resource && resource.resourceType != null) {
            stringBuilder.append("\n\n"+(resource as Resource).resourceType.name + "\n\n")
        }

        resource::class.memberProperties.forEach { field ->
            val value = (resource::class as KClass<in Any>).memberProperties
                .first { it.name == field.name }
                .also { it.isAccessible = true }
                .getter(resource)
            if (value != null) {
                val className = value::class.simpleName
                // Disable contained.
                 if (className != "ArrayList") {
                    val str = processObject(value)

                    if (!str.isEmpty()) {
                        stringBuilder.append(getFieldName(field.name) + " " + str + '\n')
                    }
                } else {
                    if (!field.name.equals("contained")) {
                        val array = value as ArrayList<IElement>
                        array.forEach { element ->
                            val str = processObject(element)
                            if (!str.isEmpty()) {
                                stringBuilder.append(getFieldName(field.name) + " " + str + '\n')
                            }
                        }
                    }
                }
            }
        }

        return stringBuilder.toString()
    }
    fun getFieldName(name : String) : String {
        try {
            val result = supportChain.fetchResource(
                NamingSystem::class.java,
                java.net.URLDecoder.decode(name, StandardCharsets.UTF_8.name())
            )
            if ((result as NamingSystem).hasName()) return result.name
        } catch (e : Exception) {

        }

        if (name.startsWith("https://fhir.hl7.org.uk/")
            || name.startsWith("https://fhir.nhs.uk/")
            || name.startsWith("http://terminology.hl7.org")) {
            val uri = URI(name)
            val path: String = uri.getPath()
            val idStr = path.substring(path.lastIndexOf('/') + 1)
            return idStr
        }

        if (name.startsWith(("urn:uuid:"))) {
            return ""
        }
        return name
    }

    fun getReferenceSummary(resource : IBaseResource) : String {
        var resourceSummary = resource.fhirType() + " "
        if (resource is Organization) {
            val organization = resource as Organization
            if (organization.hasName()) resourceSummary += organization.name
            if (organization.hasIdentifier()) {
                resourceSummary += " ("
                for (identifier in organization.identifier) {
                    if (identifier.hasValue()) resourceSummary += identifier.value
                    if (identifier.hasSystem()) resourceSummary += " " + getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }
        }
        if (resource is Patient) {
            val patient = resource as Patient
            if (patient.hasName()) resourceSummary += patient.nameFirstRep.nameAsSingleString
            if (patient.hasIdentifier()) {
                resourceSummary += " ("
                for (identifier in patient.identifier) {
                    if (identifier.hasValue()) resourceSummary += identifier.value
                    if (identifier.hasSystem()) resourceSummary += " " + getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }
        }
        if (resource is Practitioner) {
            val practitioner = resource as Practitioner
            if (practitioner.hasName()) resourceSummary += practitioner.nameFirstRep.nameAsSingleString
            if (practitioner.hasIdentifier()) {
                resourceSummary += " ("
                for (identifier in practitioner.identifier) {
                    if (identifier.hasValue()) resourceSummary += identifier.value
                    if (identifier.hasSystem()) resourceSummary += " " + getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }
        }
        if (resource is PractitionerRole) {
            val practitionerRole = resource as PractitionerRole
            resourceSummary = ""
            if (practitionerRole.hasPractitioner()) {
                resourceSummary += " ( practitioner "
                if (practitionerRole.practitioner.resource != null) {
                    resourceSummary += getReferenceSummary(practitionerRole.practitioner.resource)
                }
                if (practitionerRole.practitioner.hasIdentifier()) {
                    val identifier = practitionerRole.practitioner.identifier
                    if (identifier.hasValue()) resourceSummary += identifier.value + " "
                    if (identifier.hasSystem()) resourceSummary += getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }
            if (practitionerRole.hasOrganization()) {
                resourceSummary += " ( organisation "
                if (practitionerRole.organization.resource != null) {
                    resourceSummary += getReferenceSummary(practitionerRole.organization.resource)
                }
                if (practitionerRole.organization.hasIdentifier()) {
                    val identifier = practitionerRole.organization.identifier
                    if (identifier.hasValue()) resourceSummary += identifier.value + " "
                    if (identifier.hasSystem()) resourceSummary += getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }
        }
        if (resource is ServiceRequest) {
            val serviceRequest = resource as ServiceRequest
            if (serviceRequest.hasCategory()) {
                for (category in serviceRequest.category) {
                    val coding= category.codingFirstRep
                    if (coding != null ) {
                        if (coding.hasDisplay()) resourceSummary += " " + coding.display
                        if (coding.hasCode()) {
                            resourceSummary += " (" + coding.code
                            if (coding.hasSystem()) resourceSummary += " " + getFieldName(coding.system)
                            resourceSummary += ")"
                        }

                    }
                }
            }
            if (serviceRequest.hasCode()) {
                val coding= serviceRequest.code.codingFirstRep
                if (coding != null ) {
                    if (coding.hasCode()) resourceSummary += " " + coding.code
                    if (coding.hasSystem()) resourceSummary += " " + getFieldName(coding.system)
                }
            }
        }
        if (resource is CarePlan) {
            var carePlan = resource as CarePlan
            var carePlanSummary = ""
            if (carePlan.hasTitle()) carePlanSummary += carePlan.title
            if (carePlan.hasCategory()) {
                carePlanSummary += " ("
                for (category in carePlan.category) {
                    val coding= category.codingFirstRep
                    if (coding != null ) {
                        if (coding.hasDisplay()) resourceSummary += " " + coding.display
                        if (coding.hasCode()) {
                            carePlanSummary +=  coding.code
                            if (coding.hasSystem()) carePlanSummary += " " + getFieldName(coding.system)
                        }
                    }
                }
                carePlanSummary += ")"
            }
            if (!carePlanSummary.isEmpty()) {
                resourceSummary += carePlanSummary
            } else {
                resourceSummary += "No summary provided"
            }
        }
        if (resource is Consent) {
            val consent = resource as Consent
            if (consent.hasCategory()) {
                for (category in consent.category) {
                    val coding= category.codingFirstRep
                    if (coding != null ) {
                        if (coding.hasCode()) resourceSummary += " " + coding.code
                        if (coding.hasSystem()) resourceSummary += " " + getFieldName(coding.system)
                    }
                }
            }
        }
        if (resource is MedicationRequest) {
            val medicationRequest = resource as MedicationRequest
            if (medicationRequest.hasMedicationCodeableConcept()) {
                if (medicationRequest.medicationCodeableConcept.hasCoding()) {
                    val meds = medicationRequest.medicationCodeableConcept.codingFirstRep
                    if (meds.hasDisplay()) resourceSummary += meds.display + " "
                    if (meds.hasSystem()) resourceSummary += meds.system + " "
                    if (meds.hasCode()) resourceSummary += meds.code + " "
                }
            }
        }
        if (resource is Flag) {
            val flag = resource as Flag
            if (flag.hasCode()) {
                val meds = flag.code.codingFirstRep
                if (meds.hasDisplay()) resourceSummary += meds.display + " "
                if (meds.hasSystem()) resourceSummary += meds.system + " "
                if (meds.hasCode()) resourceSummary += meds.code + " "
               // TODO if (flag.code.hasText()) resourceSummary += flag.text.div.value + " "
            }
        }
        if (resource is Encounter) {
            val encounter = resource as Encounter
            if (encounter.hasClass_()) {
                if (encounter.class_.hasDisplay()) {
                    resourceSummary += " " + encounter.class_.display
                } else if (encounter.class_.hasCode()) resourceSummary += " " + encounter.class_.code
            }
            if (encounter.hasPeriod()) {
                if (encounter.period.hasStart()) {
                    resourceSummary += " "+sdf.format(encounter.period.start)
                }
            }
            if (encounter.hasIdentifier()) {
                resourceSummary += " ("
                for (identifier in encounter.identifier) {
                    if (identifier.hasValue()) resourceSummary += identifier.value
                    if (identifier.hasSystem()) resourceSummary += " " + getFieldName(identifier.system)
                }
                resourceSummary += ")"
            }

        }
        return resourceSummary
    }
}
