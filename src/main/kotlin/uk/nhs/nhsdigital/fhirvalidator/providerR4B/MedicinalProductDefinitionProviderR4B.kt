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
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.ValueSet
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4b.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.fhirvalidator.provider.ValueSetProvider
import uk.nhs.nhsdigital.fhirvalidator.service.CodingSupport
import uk.nhs.nhsdigital.fhirvalidator.service.ImplementationGuideParser
import uk.nhs.nhsdigital.fhirvalidator.shared.LookupCodeResultUK
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems
import java.text.SimpleDateFormat

@Component
class MedicinalProductDefinitionProviderR4B (@Qualifier("R4B") private val fhirContext: FhirContext,
                                             private val supportChain: ValidationSupportChain,
                                             private val valueSetProvider: ValueSetProvider,
                                             private val coding: CodingSupport
                                             ) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<MedicinalProductDefinition> {
        return MedicinalProductDefinition::class.java
    }
    private val validationSupportContext = ValidationSupportContext(supportChain)

    var implementationGuideParser: ImplementationGuideParser? = ImplementationGuideParser(fhirContext)

    var sdf = SimpleDateFormat("yyyyMMdd")

    @Read
    @Throws(Exception::class)
    fun getPatient(@IdParam internalId: IdType): MedicinalProductDefinition? {

        var lookupCodeResult: IValidationSupport.LookupCodeResult? =
            coding.lookupCode(internalId.getIdPart())
        if (lookupCodeResult != null) {
            var medicinalProductDefinition = MedicinalProductDefinition()
            medicinalProductDefinition.id = lookupCodeResult.searchedForCode



            if (lookupCodeResult is LookupCodeResultUK ) {
                var lookupCodeResultUK = lookupCodeResult as LookupCodeResultUK
                for (property in lookupCodeResultUK.originalParameters.parameter) {
                    if (property.name.equals("code")) {
                        medicinalProductDefinition.addIdentifier()
                            .setSystem(FhirSystems.DMandD)
                            .setValue(property.value.toString())
                        medicinalProductDefinition.addIdentifier()
                            .setSystem(FhirSystems.SNOMED_CT)
                            .setValue(property.value.toString())
                    } else
                    if (property.name.equals("property") && property.hasPart() && property.part.size>1) {

                        if (property.part[0].name.equals("code")) {
                            if (property.part[0].value is CodeType) {
                                val valueType = property.part[0].value as CodeType
                                if (valueType.value.equals("inactive")) {
                                    if (property.part[1].value is BooleanType && (property.part[1].value as org.hl7.fhir.r4.model.BooleanType).value) {
                                        medicinalProductDefinition.status =
                                            CodeableConcept().addCoding(Coding().setCode("retired"))
                                    } else {
                                        medicinalProductDefinition.status =
                                            CodeableConcept().addCoding(Coding().setCode("active"))
                                    }
                                } else if (valueType.value.equals("effectiveTime")) {
                                    if (property.part[1].value is StringType) {
                                        medicinalProductDefinition.statusDate = sdf.parse((property.part[1].value as StringType).value)
                                    }
                                }
                                else if (valueType.value.equals("609096000")) {
                                    processSubProperty(property,medicinalProductDefinition)
                                }
                               /* else if (valueType.value.equals("10362801000001104")) {
                                    processIngredient(property,medicinalProductDefinition)
                                }*/
                                else
                                if ((valueType.value.equals("parent") || valueType.value.equals("child")) && property.part[1].value is CodeType) {
                                    var code = property.part[1].value as CodeType

                                    when (code.value) {
                                        "10363801000001108" -> {
                                            medicinalProductDefinition.setType(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product")
                                            ))
                                        }
                                        "10363901000001102" -> {
                                            medicinalProductDefinition.setType(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product")
                                            ))
                                        }
                                        "10364001000001104" -> {
                                            medicinalProductDefinition.setType(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Actual medicinal product pack")
                                            ))
                                        }
                                        "8653601000001108" -> {
                                            medicinalProductDefinition.setType(CodeableConcept().addCoding(
                                                Coding().setSystem(FhirSystems.SNOMED_CT).setCode(code.value).setDisplay("Virtual medicinal product pack")
                                            ))
                                        }
                                        else -> {
                                            val reference = Reference().setReference("MedicinalProductDefinition/"+code.value)
                                            var lookupCode: IValidationSupport.LookupCodeResult? =
                                                coding.lookupCode( code.value)
                                            if (lookupCode != null) reference.display = lookupCode.codeDisplay
                                            var crossReference = MedicinalProductDefinition.MedicinalProductDefinitionCrossReferenceComponent()
                                                .setProduct(CodeableReference().setReference(reference))
                                            crossReference.type = CodeableConcept()
                                                //.addCoding(Coding().setCode(valueType.value))
                                            if (lookupCode != null) {
                                                var codeType = coding.getTypeCoding(lookupCode)
                                                if (codeType != null) {
                                                    crossReference.type.addCoding(codeType)
                                                }
                                            }
                                            medicinalProductDefinition.addCrossReference(crossReference)
                                        }
                                    }

                                } else {
                                    for (part in property.part) {
                                      //  System.out.println(property.name + " " +part.name + " = " + part.value )
                                    }
                                }
                            }
                        } else {
                            for (part in property.part) {
                          //      System.out.println(property.name + " " +part.name + " = " + part.value )
                            }
                        }

                    } else if (property.name.equals("designation") && property.hasPart() && property.part.size>2) {
                        val name = MedicinalProductDefinition.MedicinalProductDefinitionNameComponent((property.part[2].value as StringType).value)
                        val type = property.part[1].value as org.hl7.fhir.r4.model.Coding
                        name.type = CodeableConcept().addCoding(Coding()
                            .setSystem(type.system)
                            .setCode(type.code)
                            .setDisplay(type.display)
                        )
                        medicinalProductDefinition.addName(name)
                    }
                    else {
                 //       System.out.println(property.name)
                    }
                }
            }
            if (coding.isProduct(lookupCodeResult) )return medicinalProductDefinition
        }
        return null;
    }



    @Search
    fun search(
       // @OptionalParam(name = MedicinalProductDefinition.SP_IDENTIFIER) identifier : TokenParam?,
        @OptionalParam(name = MedicinalProductDefinition.SP_NAME) name : StringParam?
    ): List<MedicinalProductDefinition> {
        val list = mutableListOf<MedicinalProductDefinition>()
        if (name != null) {
            var valueSetR4: ValueSet? = null;

            var valueSets = valueSetProvider.search(TokenParam().setValue("https://fhir.nhs.uk/ValueSet/NHSDigital-MedicationRequest-Code"))
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
                            var medicinalProductDefinition = MedicinalProductDefinition()
                            medicinalProductDefinition.id = content.code
                            medicinalProductDefinition.addName(MedicinalProductDefinition.MedicinalProductDefinitionNameComponent(content.display))

                            medicinalProductDefinition.addIdentifier()
                                .setSystem(FhirSystems.DMandD)
                                .setValue(content.code)
                            medicinalProductDefinition.addIdentifier()
                                .setSystem(FhirSystems.SNOMED_CT)
                                .setValue(content.code)
                            var lookupCode: IValidationSupport.LookupCodeResult? =
                                coding.lookupCode( content.code)
                            if (lookupCode != null && coding.isProduct(lookupCode) ) {
                                medicinalProductDefinition.setType(CodeableConcept().addCoding(coding.getTypeCoding(lookupCode)))
                                list.add(medicinalProductDefinition)
                            }
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


    private fun processSubProperty(
        property: Parameters.ParametersParameterComponent?,
        medicinalProductDefinition: MedicinalProductDefinition
    ) {
        if (property?.part?.size!! >1
            && property.part[1].name.equals("subproperty")
            && property.part[1].part.size>1) {

            when ((property.part[1].part[0].value as CodeType).code) {
                "10362801000001104" -> {

                    medicinalProductDefinition.addIngredient(
                        coding.getCodeableConcept((property.part[1].part[1].value as CodeType).code)
                    )
                }
                //VMP ontology form and route
                "13088401000001104" ->  {
                    medicinalProductDefinition.addRoute(
                        coding.getCodeableConcept((property.part[1].part[1].value as CodeType).code)
                    )
                }
                //VMP ontology form and route
                "13088501000001100"  ->  {
                    medicinalProductDefinition.setCombinedPharmaceuticalDoseForm(
                        coding.getCodeableConcept((property.part[1].part[1].value as CodeType).code)
                    )
                }
                // Has NHS dm+d controlled drug category
                "13089101000001102" ->  medicinalProductDefinition.addSpecialMeasures(
                    coding.getCodeableConcept((property.part[1].part[1].value as CodeType).code)
                )
                else -> {
                    //   System.out.println((property.part[1].part[0].value as CodeType).code)
                    medicinalProductDefinition.addCharacteristic(
                        MedicinalProductDefinition.MedicinalProductDefinitionCharacteristicComponent()
                            .setType(
                                coding.getCodeableConcept((property.part[1].part[0].value as CodeType).code))
                            .setValue(
                                coding.getCodeableConcept((property.part[1].part[1].value as CodeType).code))
                    )
                }
            }
        }
    }

}
