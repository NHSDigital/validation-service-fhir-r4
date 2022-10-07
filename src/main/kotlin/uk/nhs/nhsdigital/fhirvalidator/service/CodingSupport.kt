package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r5.model.CodeableConcept
import org.hl7.fhir.r5.model.Coding
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.nhs.nhsdigital.fhirvalidator.shared.LookupCodeResultUK
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems
import java.util.concurrent.TimeUnit




@Service
class CodingSupport(@Qualifier("R4") private val ctx: FhirContext?,
                    private val npmPackages: List<NpmPackage>?,
                    @Qualifier("SupportChain") private val supportChain: IValidationSupport
) {
    private val validationSupportContext = ValidationSupportContext(supportChain)

    var cacheCoding: Cache<String, LookupCodeResultUK> = Caffeine.newBuilder()
        .expireAfterWrite(12, TimeUnit.HOURS)
        .maximumSize(5000)
        .build()

    fun lookupCode(code :String) : LookupCodeResultUK? {
        var lookupCodeResultUK = cacheCoding.getIfPresent(code)
        if (lookupCodeResultUK != null) return lookupCodeResultUK
        lookupCodeResultUK = supportChain.lookupCode(this.validationSupportContext,  FhirSystems.SNOMED_CT, code) as LookupCodeResultUK
        if (lookupCodeResultUK!=null) {
            cacheCoding.put(code,lookupCodeResultUK)
            return lookupCodeResultUK
        }
        return null
    }

    fun setTypeCoding(lookupCodeResult: IValidationSupport.LookupCodeResult) : Coding? {
        if (lookupCodeResult is LookupCodeResultUK ) {
            var lookupCodeResultUK = lookupCodeResult as LookupCodeResultUK
            for (property in lookupCodeResultUK.originalParameters.parameter) {
                if (property.name.equals("property") && property.hasPart() && property.part.size>1) {
                    if (property.part[0].name.equals("code")) {
                        if (property.part[0].value is CodeType) {
                            val valueType = property.part[0].value as CodeType

                            if ((valueType.value.equals("parent") || valueType.value.equals("child")) && property.part[1].value is CodeType) {
                                var code = property.part[1].value as CodeType

                                when (code.value) {
                                    "10363801000001108" -> {
                                        return Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product")
                                    }
                                    "10363901000001102" -> {
                                        return Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product")
                                    }
                                    "10364001000001104" -> {

                                        return Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product pack")

                                    }
                                    "8653601000001108" -> {

                                        return Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product pack")

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    fun isProduct(lookupCodeResult: IValidationSupport.LookupCodeResult) : Boolean {
        if (lookupCodeResult is LookupCodeResultUK ) {
            var lookupCodeResultUK = lookupCodeResult as LookupCodeResultUK
            for (property in lookupCodeResultUK.originalParameters.parameter) {
                if (property.name.equals("property") && property.hasPart() && property.part.size>1) {
                    if (property.part[0].name.equals("code")) {
                        if (property.part[0].value is CodeType) {
                            val valueType = property.part[0].value as CodeType

                            if ((valueType.value.equals("parent") || valueType.value.equals("child")) && property.part[1].value is CodeType) {
                                var code = property.part[1].value as CodeType

                                when (code.value) {
                                    "10363801000001108" -> {
                                        return true
                                    }
                                    "10363901000001102" -> {
                                        return true
                                    }
                                    "10364001000001104" -> {
                                        return false
                                    }
                                    "8653601000001108" -> {
                                        return false
                                    }

                                }

                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    fun isPack(lookupCodeResult: IValidationSupport.LookupCodeResult) : Boolean {
        if (lookupCodeResult is LookupCodeResultUK ) {
            var lookupCodeResultUK = lookupCodeResult as LookupCodeResultUK
            for (property in lookupCodeResultUK.originalParameters.parameter) {
                if (property.name.equals("property") && property.hasPart() && property.part.size>1) {
                    if (property.part[0].name.equals("code")) {
                        if (property.part[0].value is CodeType) {
                            val valueType = property.part[0].value as CodeType

                            if ((valueType.value.equals("parent") || valueType.value.equals("child")) && property.part[1].value is CodeType) {
                                var code = property.part[1].value as CodeType

                                when (code.value) {
                                    "10363801000001108" -> {
                                        return false
                                    }
                                    "10363901000001102" -> {
                                        return false
                                    }
                                    "10364001000001104" -> {
                                        return true
                                    }
                                    "8653601000001108" -> {
                                        return true
                                    }

                                }

                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    fun getCodeableConcept(code :String) : CodeableConcept? {
        val codeResult = lookupCode(code)
        if (codeResult == null) return null;
        return CodeableConcept().addCoding(Coding()
            .setSystem(FhirSystems.SNOMED_CT)
            .setCode(code)
            .setDisplay(codeResult.codeDisplay))
    }
}
