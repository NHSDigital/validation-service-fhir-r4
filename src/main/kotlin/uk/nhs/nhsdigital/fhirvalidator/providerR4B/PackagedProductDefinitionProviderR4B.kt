package uk.nhs.nhsdigital.fhirvalidator.providerR4B

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.ValueSet
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r5.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.provider.ValueSetProvider
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import uk.nhs.nhsdigital.fhirvalidator.shared.LookupCodeResultUK
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems
import java.text.SimpleDateFormat

@Component
class PackagedProductDefinitionProviderR4B (@Qualifier("R5") private val fhirContext: FhirContext,
                                            private val supportChain: ValidationSupportChain,
                                            private val valueSetProvider: ValueSetProvider,
                                            private val npmPackages: List<NpmPackage>) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<PackagedProductDefinition> {
        return PackagedProductDefinition::class.java
    }
    private val validationSupportContext = ValidationSupportContext(supportChain)

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)

    var sdf = SimpleDateFormat("yyyyMMdd")

    @Read
    @Throws(Exception::class)
    fun getPatient(@IdParam internalId: IdType): PackagedProductDefinition? {

        var lookupCodeResult: IValidationSupport.LookupCodeResult? =
            supportChain.lookupCode(this.validationSupportContext,  FhirSystems.SNOMED_CT, internalId.getIdPart())
        if (lookupCodeResult != null) {
            var packagedProductDefinition = PackagedProductDefinition()
            packagedProductDefinition.id = lookupCodeResult.searchedForCode



            if (lookupCodeResult is LookupCodeResultUK ) {
                var lookupCodeResultUK = lookupCodeResult as LookupCodeResultUK
                for (property in lookupCodeResultUK.originalParameters.parameter) {
                    if (property.name.equals("code")) {
                        packagedProductDefinition.addIdentifier()
                            .setSystem(FhirSystems.DMandD)
                            .setValue(property.value.toString())
                        packagedProductDefinition.addIdentifier()
                            .setSystem(FhirSystems.SNOMED_CT)
                            .setValue(property.value.toString())
                    } else
                    if (property.name.equals("property") && property.hasPart() && property.part.size>1) {

                        if (property.part[0].name.equals("code")) {
                            if (property.part[0].value is CodeType) {
                                val valueType = property.part[0].value as CodeType
                                if (valueType.value.equals("inactive")) {
                                    if (property.part[1].value is BooleanType && (property.part[1].value as org.hl7.fhir.r4.model.BooleanType).value) {
                                        packagedProductDefinition.status =
                                            CodeableConcept().addCoding(Coding().setCode("retired"))
                                    } else {
                                        packagedProductDefinition.status =
                                            CodeableConcept().addCoding(Coding().setCode("active"))
                                    }
                                } else if (valueType.value.equals("effectiveTime")) {
                                    if (property.part[1].value is StringType) {
                                        packagedProductDefinition.statusDate = sdf.parse((property.part[1].value as StringType).value)
                                    }
                                }
                                else
                                if ((valueType.value.equals("parent") || valueType.value.equals("child")) && property.part[1].value is CodeType) {
                                    var code = property.part[1].value as CodeType

                                    when (code.value) {
                                        "10363801000001108" -> {
                                            packagedProductDefinition.addCharacteristic(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product")
                                            ))
                                        }
                                        "10363901000001102" -> {
                                            packagedProductDefinition.addCharacteristic(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product")
                                            ))
                                        }
                                        "10364001000001104" -> {
                                            packagedProductDefinition.addCharacteristic(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product pack")
                                            ))
                                        }
                                        "8653601000001108" -> {
                                            packagedProductDefinition.addCharacteristic(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product pack")
                                            ))
                                        }
                                        else -> {
                                            val reference = Reference()
                                            var lookupCode: IValidationSupport.LookupCodeResult? =
                                                supportChain.lookupCode(this.validationSupportContext,  FhirSystems.SNOMED_CT, code.value)
                                            if (lookupCode != null) {
                                                reference.display = lookupCode.codeDisplay
                                                if (isProduct(lookupCode)) {
                                                    reference.setReference("MedicinalProductDefinition/"+code.value)
                                                }
                                                reference.identifier = Identifier()
                                                    .setType(CodeableConcept().addCoding(setTypeCoding(lookupCode)))
                                                    .setSystem(FhirSystems.DMandD)
                                                    .setValue(code.value)
                                                packagedProductDefinition.addPackageFor(reference)
                                            }

                                        }
                                    }

                                } else {
                                    for (part in property.part) {
                                 //       System.out.println(property.name + " " +part.name + " = " + part.value )
                                    }
                                }
                            }
                        } else {
                            for (part in property.part) {
                               // System.out.println(property.name + " " +part.name + " = " + part.value )
                            }
                        }

                    } else if (property.name.equals("designation") && property.hasPart() && property.part.size>2) {
                        packagedProductDefinition.name = lookupCodeResult.codeDisplay
                    }
                    else {
                       // System.out.println(property.name)
                    }
                }
            }
            if (isPack(lookupCodeResult) )return packagedProductDefinition
        }
        return null;
    }
    @Search
    fun search(
       // @OptionalParam(name = packagedProductDefinition.SP_IDENTIFIER) identifier : TokenParam?,
        @OptionalParam(name = PackagedProductDefinition.SP_NAME) name : StringParam?
    ): List<PackagedProductDefinition> {
        val list = mutableListOf<PackagedProductDefinition>()
        if (name != null) {
            var valueSetR4: ValueSet? = null;

            var valueSets = valueSetProvider.search(TokenParam().setValue("https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationDispense-Code"))
            if (valueSets.size>0) valueSetR4= valueSets[0];

            if (valueSetR4 != null) {
                var valueSetExpansionOptions = ValueSetExpansionOptions();
                valueSetR4.expansion = null; // remove any previous expansion
                valueSetExpansionOptions.filter = name.value
                var expansion: IValidationSupport.ValueSetExpansionOutcome? =
                    supportChain.expandValueSet(this.validationSupportContext, valueSetExpansionOptions, valueSetR4)
                if (expansion != null) {
                    if (expansion.valueSet is ValueSet) {
                        var newValueSet = expansion.valueSet as ValueSet
                        valueSetR4.expansion = newValueSet.expansion
                    }
                    if (expansion?.error != null) {
                        throw UnprocessableEntityException(expansion?.error)
                    }
                    if (valueSetR4.hasExpansion() && valueSetR4.expansion.hasContains())
                    {
                        for (content in valueSetR4.expansion.contains) {
                            var packagedProductDefinition = PackagedProductDefinition()
                            packagedProductDefinition.id = content.code
                            packagedProductDefinition.name = content.display

                            packagedProductDefinition.addIdentifier()
                                .setSystem(FhirSystems.DMandD)
                                .setValue(content.code)
                            packagedProductDefinition.addIdentifier()
                                .setSystem(FhirSystems.SNOMED_CT)
                                .setValue(content.code)
                            var lookupCode: IValidationSupport.LookupCodeResult? =
                                supportChain.lookupCode(this.validationSupportContext,  FhirSystems.SNOMED_CT, content.code)
                            if (lookupCode != null && isPack(lookupCode) ) list.add(packagedProductDefinition)
                        }
                    }
                }
              // Convert response into Medicinal Product Definition
            } else {
                throw UnprocessableEntityException("ValueSet not found");
            }
        }
        return list
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

}
