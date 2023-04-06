package uk.nhs.nhsdigital.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.util.ParametersUtil
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.hl7.fhir.instance.model.api.IBaseParameters
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4b.model.CodeableConcept
import org.hl7.fhir.r4b.model.Coding
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.stereotype.Service
import uk.nhs.nhsdigital.fhirvalidator.configuration.TerminologyValidationProperties
import uk.nhs.nhsdigital.fhirvalidator.shared.LookupCodeResultUK
import uk.nhs.nhsdigital.fhirvalidator.util.AccessTokenInterceptor
import uk.nhs.nhsdigital.fhirvalidator.util.FhirSystems
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


@Service
class CodingSupport(@Qualifier("R4") private val ctx: FhirContext?,
                    private val npmPackages: List<NpmPackage>?,
                    @Qualifier("SupportChain") private val supportChain: IValidationSupport,
                    private val terminologyValidationProperties: TerminologyValidationProperties,
                    optionalAuthorizedClientManager: Optional<OAuth2AuthorizedClientManager>,
                    private val validationSupportContext : ValidationSupportContext
) {


    private val myClientInterceptors: MutableList<Any?> = ArrayList()

    init {
        if (optionalAuthorizedClientManager.isPresent) {
            val authorizedClientManager = optionalAuthorizedClientManager.get()
            val accessTokenInterceptor = AccessTokenInterceptor(authorizedClientManager)
            myClientInterceptors.add(accessTokenInterceptor)
        }
    }


    var cacheCoding: Cache<String, IValidationSupport.LookupCodeResult> = Caffeine.newBuilder()
        .expireAfterWrite(12, TimeUnit.HOURS)
        .maximumSize(5000)
        .build()

    fun lookupCode(code :String) : IValidationSupport.LookupCodeResult? {
        var lookupCodeResultUK = cacheCoding.getIfPresent(FhirSystems.SNOMED_CT + "-"+ code)
        if (lookupCodeResultUK != null) return lookupCodeResultUK
        lookupCodeResultUK = supportChain.lookupCode(this.validationSupportContext,  FhirSystems.SNOMED_CT, code) as LookupCodeResultUK
        if (lookupCodeResultUK!=null) {
            cacheCoding.put(FhirSystems.SNOMED_CT + "-"+ code,lookupCodeResultUK)
            return lookupCodeResultUK
        }
        return null
    }
    fun lookupCode(system :String, code: String) : IValidationSupport.LookupCodeResult? {
        var lookupCodeResultUK = cacheCoding.getIfPresent(system + "-"+ code)
        if (lookupCodeResultUK != null) return lookupCodeResultUK
        val result = supportChain.lookupCode(this.validationSupportContext,  system, code)
        if (result != null) lookupCodeResultUK = result
        if (lookupCodeResultUK!=null) {
            cacheCoding.put(system + "-"+ code,lookupCodeResultUK)
            return lookupCodeResultUK
        }
        return null
    }

    fun getTypeCoding(lookupCodeResult: IValidationSupport.LookupCodeResult) : Coding? {
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

    fun subsumes(
        codeA: String?,
        codeB: String?,
        system: String?,
    ) : Parameters? {
        val client = provideClient()

        if (client != null) {
            val input = ParametersUtil.newInstance(ctx)
            ParametersUtil.addParameterToParametersUri(ctx, input, "system", system)
            ParametersUtil.addParameterToParametersCode(ctx, input, "codeA", codeA)
            ParametersUtil.addParameterToParametersCode(ctx, input, "codeB", codeB)
            val output: IBaseParameters =
                client.operation().onType(CodeSystem::class.java).named("subsumes")
                    .withParameters<IBaseParameters>(input)
                    .execute()
            if (output is Parameters) {
                return output
            }
        }
        return null
    }

    fun search(filter: String?, count: IntegerType?,includeDesignations: BooleanType?): Parameters? {
        val client = provideClient()

        if (client != null) {
            val input = ParametersUtil.newInstance(ctx)
            // https://r4.ontoserver.csiro.au/fhir/ValueSet/$expand?_format=json&count=10&filter=metha&includeDesignations=true&url=http:%2F%2Fsnomed.info%2Fsct%3Ffhir_vs%3Disa%2F138875005
            ParametersUtil.addParameterToParametersString(ctx, input, "filter", filter)
            if (includeDesignations !=null) {
                ParametersUtil.addParameterToParametersBoolean(ctx, input, "includeDesignations", includeDesignations.value)
            }
            ParametersUtil.addParameterToParametersUri(ctx, input, "url", FhirSystems.SNOMED_CT+"?fhir_vs")
            if (count != null) {
                ParametersUtil.addParameterToParametersInteger(ctx, input, "count", count.value)
            }
            val output: IBaseParameters =
                client.operation().onType(ValueSet::class.java).named("expand")
                    .withParameters<IBaseParameters>(input)
                    .execute()
            if (output is Parameters) {
                return output
            }
        }
        return null
    }

    fun expandEcl(ecl: String?, count: IntegerType?): Parameters? {
        val client = provideClient()

        if (client != null) {
            val input = ParametersUtil.newInstance(ctx)
            // https://r4.ontoserver.csiro.au/fhir/ValueSet/$expand?_format=json&count=10&filter=metha&includeDesignations=true&url=http:%2F%2Fsnomed.info%2Fsct%3Ffhir_vs%3Disa%2F138875005

            ParametersUtil.addParameterToParametersUri(ctx, input, "url", FhirSystems.SNOMED_CT+"?fhir_vs=ecl/"+ecl)
            if (count != null) {
                ParametersUtil.addParameterToParametersInteger(ctx, input, "count", count.value)
            }
            val output: IBaseParameters =
                client.operation().onType(ValueSet::class.java).named("expand")
                    .withParameters<IBaseParameters>(input)
                    .execute()
            if (output is Parameters) {
                return output
            }
        }
        return null
    }

    fun getCodeableConcept(code :String) : CodeableConcept? {
        val codeResult = lookupCode(code)
        if (codeResult == null) return null;
        return CodeableConcept().addCoding(Coding()
            .setSystem(FhirSystems.SNOMED_CT)
            .setCode(code)
            .setDisplay(codeResult.codeDisplay))
    }

    private fun provideClient(): IGenericClient? {
        val retVal: IGenericClient? = this.ctx?.newRestfulGenericClient(terminologyValidationProperties.url)
        if (retVal != null) {
        val var2: Iterator<*> = this.myClientInterceptors.iterator()
        while (var2.hasNext()) {
            val next = var2.next()!!
            retVal.registerInterceptor(next)
        } }
        return retVal
    }


}
