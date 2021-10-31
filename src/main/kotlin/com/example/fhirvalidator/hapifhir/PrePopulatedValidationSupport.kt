package com.example.fhirvalidator.hapifhir

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import org.hl7.fhir.instance.model.api.IBase
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.instance.model.api.IPrimitiveType
import java.util.*
import javax.annotation.Nonnull


open class PrePopulatedValidationSupport(
    theFhirContext: FhirContext,
    theStructureDefinitions: MutableMap<String?, IBaseResource?>,
    theValueSets: MutableMap<String?, IBaseResource?>,
    theCodeSystems: MutableMap<String?, IBaseResource?>,
    theOtherResources: MutableMap<String?, IBaseResource?>
) :
    BaseStaticResourceValidationSupport(theFhirContext), IValidationSupport {
    private val myCodeSystems: MutableMap<String?, IBaseResource?>
    private val myStructureDefinitions: MutableMap<String?, IBaseResource?>
    private val myValueSets: MutableMap<String?, IBaseResource?>
    private val otherResources: MutableMap<String?, IBaseResource?>

    constructor(theContext: FhirContext) : this(
        theContext,
        HashMap<String?, IBaseResource?>(),
        HashMap<String?, IBaseResource?>(),
        HashMap<String?, IBaseResource?>(),
        HashMap<String?, IBaseResource?>()
    ) {
    }

    init {
        Validate.notNull(theFhirContext, "theFhirContext must not be null", *arrayOfNulls(0))
        Validate.notNull<Map<String?, IBaseResource?>>(
            theStructureDefinitions,
            "theStructureDefinitions must not be null",
            *arrayOfNulls(0)
        )
        Validate.notNull<Map<String?, IBaseResource?>>(theValueSets, "theValueSets must not be null", *arrayOfNulls(0))
        Validate.notNull<Map<String?, IBaseResource?>>(
            theCodeSystems,
            "theCodeSystems must not be null",
            *arrayOfNulls(0)
        )
        myStructureDefinitions = theStructureDefinitions
        myValueSets = theValueSets
        myCodeSystems = theCodeSystems
        otherResources = theOtherResources
    }

    fun addCodeSystem(theCodeSystem: IBaseResource) {
        val url = processResourceAndReturnUrl(theCodeSystem, "CodeSystem")
        addToMap(theCodeSystem, myCodeSystems, url)
    }

    private fun processResourceAndReturnUrl(theCodeSystem: IBaseResource, theResourceName: String): String? {
        Validate.notNull(
            theCodeSystem,
            "the$theResourceName must not be null", *arrayOfNulls(0)
        )
        val resourceDef = this.fhirContext.getResourceDefinition(theCodeSystem)
        val actualResourceName = resourceDef.name
        Validate.isTrue(
            actualResourceName == theResourceName,
            "the$theResourceName must be a $theResourceName - Got: $actualResourceName", *arrayOfNulls(0)
        )
        val urlValue = resourceDef.getChildByName("url").accessor.getFirstValueOrNull<IBase>(theCodeSystem)
        val url = urlValue.map { t: IBase -> (t as IPrimitiveType<*>).valueAsString }.orElse(null as String?)
        Validate.notNull(
            url,
            "the$theResourceName.getUrl() must not return null", *arrayOfNulls(0)
        )
        Validate.notBlank(
            url,
            "the$theResourceName.getUrl() must return a value", *arrayOfNulls(0)
        )
        return url
    }

    fun addStructureDefinition(theStructureDefinition: IBaseResource) {
        val url = processResourceAndReturnUrl(theStructureDefinition, "StructureDefinition")
        addToMap(theStructureDefinition, myStructureDefinitions, url)
    }

    private fun <T : IBaseResource?> addToMap(theResource: T, theMap: MutableMap<String?, T>, theUrl: String?) {
        if (StringUtils.isNotBlank(theUrl)) {
            theMap[theUrl] = theResource
            val lastSlashIdx = theUrl!!.lastIndexOf(47.toChar())
            if (lastSlashIdx != -1) {
                theMap[theUrl.substring(lastSlashIdx + 1)] = theResource
                val previousSlashIdx = theUrl.lastIndexOf(47.toChar(), lastSlashIdx - 1)
                if (previousSlashIdx != -1) {
                    theMap[theUrl.substring(previousSlashIdx + 1)] = theResource
                }
            }
        }
    }

    fun addValueSet(theValueSet: IBaseResource) {
        val url = processResourceAndReturnUrl(theValueSet, "ValueSet")
        addToMap(theValueSet, myValueSets, url)
    }

    fun addOtherResource(resource: IBaseResource) {
        val url = processResourceAndReturnUrl(resource, resource.javaClass.simpleName)
        addToMap(resource, otherResources, url)
    }

    fun addResource(@Nonnull theResource: IBaseResource) {
        Validate.notNull(theResource, "theResource must not be null", *arrayOfNulls(0))
        val var2 = this.fhirContext.getResourceType(theResource)
        var var3: Int = -1
        when (var2.hashCode()) {
            -1345530543 -> if (var2 == "ValueSet") {
                var3 = 2
            }
            1076953756 -> if (var2 == "CodeSystem") {
                var3 = 1
            }
            1133777670 -> if (var2 == "StructureDefinition") {
                var3 = 0
            }
        }
        when (var3) {
            0 -> addStructureDefinition(theResource)
            1 -> addCodeSystem(theResource)
            2 -> addValueSet(theResource)
            else -> addOtherResource(theResource)
        }
    }

    override fun fetchAllConformanceResources(): List<IBaseResource> {
        val retVal: ArrayList<IBaseResource> = ArrayList<IBaseResource>()
        retVal.addAll(myCodeSystems.values)
        retVal.addAll(myStructureDefinitions.values)
        retVal.addAll(myValueSets.values)
        retVal.addAll(otherResources.values)
        return retVal
    }

    override fun <T : IBaseResource?> fetchResource(theClass: Class<T>?, theUri: String?): T? {
        var resource = otherResources[theUri];
        if (resource == null ) resource = theUri?.let { fetchCodeSystem(it) }
        if (resource == null ) resource = theUri?.let { fetchValueSet(it) }
        if (resource == null ) resource = theUri?.let { fetchStructureDefinition(it) }
        if (resource !== null ) return resource as T?;
        return null;
    }

    override fun <T : IBaseResource?> fetchAllStructureDefinitions(): List<T>? {
        return toList<T>(
            myStructureDefinitions
        )
    }

    override fun fetchCodeSystem(theSystem: String): IBaseResource? {
        return myCodeSystems[theSystem]
    }

    override fun fetchValueSet(theUri: String): IBaseResource? {
        return myValueSets[theUri]
    }

    override fun fetchStructureDefinition(theUrl: String): IBaseResource? {
        return myStructureDefinitions[theUrl]
    }

    override fun isCodeSystemSupported(
        theValidationSupportContext: ValidationSupportContext,
        theSystem: String
    ): Boolean {
        return myCodeSystems.containsKey(theSystem)
    }

    override fun isValueSetSupported(
        theValidationSupportContext: ValidationSupportContext,
        theValueSetUrl: String
    ): Boolean {
        return myValueSets.containsKey(theValueSetUrl)
    }


}

private fun <E> MutableList<E>.addAll(elements: MutableCollection<E?>) {

}
