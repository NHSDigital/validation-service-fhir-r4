package uk.nhs.nhsdigital.fhirvalidator.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.model.api.IElement
import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.service.OpenAPIParser
import java.net.URI
import java.text.SimpleDateFormat
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible


@Component
class chatGPProvider(@Qualifier("R4") private val fhirContext: FhirContext,
                     private val oasParser : OpenAPIParser
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
            if (resource is Bundle) throw UnprocessableEntityException("This server does not support FHIR Bundle or collections, please try again with a single resource.")
           val result = processObject(resource)
            servletResponse.writer.write(result)
        }

        servletResponse.writer.flush()
        return
    }
    fun processObject(resource: Any) : String {


        val stringBuilder = StringBuilder()

        if (resource is Meta) return ""

        if (resource is Reference) {
            val reference = resource as Reference
            var str = ""
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
        if (resource is Coding) {
                    val code = resource as Coding
                    var str = ""
                    if (code.hasDisplay()) str = code.display
                    if (code.hasSystem() || code.hasCode()) {
                        str += " ("
                        if (code.hasSystem()) str += getFieldName(code.system) + " "
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
                            if (code.hasDisplay()) str = code.display
                            if (code.hasSystem() || code.hasCode()) {
                                str += " ("
                                if (code.hasSystem()) str += getFieldName(code.system) + " "
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

        println(resource::class.simpleName)

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
                 if (className != "ArrayList") {
                    val str = processObject(value)
                    //println(className)
                    if (value is CodeableConcept) println(field.name)
                    if (!str.isEmpty()) {
                        stringBuilder.append(getFieldName(field.name) + " " + str + '\n')
                    }
                } else {
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

        return stringBuilder.toString()
    }
    fun getFieldName(name : String) : String {
        if (name.equals("subject")) return "patient"
        if (name.equals("https://fhir.nhs.uk/Id/nhs-number")) return "NHS Number"
        if (name.equals("http://snomed.info/sct")) return "SNOMED"
        if (name.equals("https://fhir.nhs.uk/Id/ods-organization-code")) return "ODS Code"

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
}
