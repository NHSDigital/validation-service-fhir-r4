package com.example.fhirvalidator.service

import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import com.example.fhirvalidator.model.*
import com.example.fhirvalidator.model.Reference
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Service
import java.util.*
import java.util.function.Predicate

val STRING_TYPES = setOf(
    "http://hl7.org/fhirpath/System.String", "http://hl7.org/fhirpath/System.DateTime",
    "http://hl7.org/fhirpath/System.Date", "http://hl7.org/fhirpath/System.Time",
    "string", "markdown", "xhtml", "id", "canonical", "code", "uri", "url", "uuid",
    "oid", "base64Binary", "instant", "dateTime", "date", "time"
)
val INTEGER_TYPES = setOf("http://hl7.org/fhirpath/System.Integer", "integer", "positiveInt", "unsignedInt")
val NUMBER_TYPES = setOf("http://hl7.org/fhirpath/System.Decimal", "decimal")
val BOOLEAN_TYPES = setOf("http://hl7.org/fhirpath/System.Boolean", "boolean")

@Service
class SchemaConversionService(
    private val validationSupportChain: ValidationSupportChain
) {
    companion object : KLogging()

    private val structureDefinitions = validationSupportChain.fetchAllStructureDefinitions()
        .filterIsInstance(StructureDefinition::class.java)

    fun listStructureDefinitions(): String {
        return structureDefinitions.joinToString("\n") { it.name }
    }

    fun prettyPrintStructureDefinition(name: String): String {
        val structureDefinition = structureDefinitions.find { it.name == name }
            ?: return "StructureDefinition not found"
        return structureDefinition.snapshot?.element?.let { convertSnapshotToString(it) }
            ?: return "StructureDefinition missing snapshot"
    }

    fun convertStructureDefinitionToOpenApiSchema(name: String): SchemaOrReference? {
        val structureDefinition = structureDefinitions.find { it.name == name } ?: return null
        val element = structureDefinition.snapshot.element
        return convertSnapshotToOpenApiSchema(element)
    }

    fun convertStructureDefinitionAndDependenciesToOpenApiSchema(firstStructureDefinitionName: String): Map<String, SchemaOrReference> {
        val structureDefinitionNamesToProcess = mutableListOf(firstStructureDefinitionName)
        val structureDefinitionNamesProcessed = mutableSetOf<String>()
        val output = mutableListOf<Pair<String, SchemaOrReference>>()
        while (structureDefinitionNamesToProcess.isNotEmpty()) {
            val structureDefinitionName = structureDefinitionNamesToProcess.removeFirst()
            if (!structureDefinitionNamesProcessed.contains(structureDefinitionName)) {
                val structureDefinition = structureDefinitions.find { it.name == structureDefinitionName }
                if (structureDefinition != null) {
                    val snapshotElements = structureDefinition.snapshot.element
                    val fhirTree = convertSnapshotToTree(snapshotElements)
                    val referencedTypes = findReferencedTypes(fhirTree)
                    structureDefinitionNamesToProcess.addAll(referencedTypes)
                    val openApiSchema = toOpenApiSchema(fhirTree)
                    val openApiModelName = getAllowedModelName(structureDefinitionName)
                    output.add(Pair(openApiModelName, openApiSchema))
                    structureDefinitionNamesProcessed.add(structureDefinitionName)
                } else {
                    logger.warn { "Structure definition not found: $structureDefinitionName" }
                }
            }
        }
        return output.toMap()
    }

    private fun findReferencedTypes(root: FhirTreeNode): Collection<String> {
        val types = mutableSetOf<String>()
        root.typeCode?.let { types.add(it) }
        types.addAll(root.profiles.map { getSchemaName(it) })
        root.children.map { types.addAll(findReferencedTypes(it)) }
        root.slices.map { types.addAll(findReferencedTypes(it)) }
        return types.filterNot { isPrimitiveType(it) }
    }

//    private fun findReferenceTargets(schemaOrReference: SchemaOrReference): Collection<String> {
//        return when (schemaOrReference) {
//            is Reference -> setOf(schemaOrReference.`$ref`)
//            is ObjectSchema -> schemaOrReference.properties.values.map { findReferenceTargets(it) }.flatten()
//            is ArraySchema -> findReferenceTargets(schemaOrReference.items)
//            is OneOfSchema -> schemaOrReference.oneOf.map { findReferenceTargets(it) }.flatten()
//            is AllOfSchema -> schemaOrReference.allOf.map { findReferenceTargets(it) }.flatten()
//            is AnyOfSchema -> schemaOrReference.anyOf.map { findReferenceTargets(it) }.flatten()
//            is NegatedSchema -> findReferenceTargets(schemaOrReference.not)
//            else -> emptySet()
//        }
//    }

    fun convertAllStructureDefinitionsToOpenApiSchema(): Map<String, SchemaOrReference> {
        return structureDefinitions
            .filter { it.snapshot?.element != null }
            .associate {
                Pair(
                    getAllowedModelName(it.name),
                    try {
                        convertSnapshotToOpenApiSchema(it.snapshot.element)
                    } catch (e: java.lang.IllegalStateException) {
                        StringSchema("ERROR DURING SCHEMA GENERATION: ${e.message}")
                    }
                )
            }
    }

    private fun convertSnapshotToString(snapshotElements: List<ElementDefinition>): String {
        val root = convertSnapshotToTree(snapshotElements)
        return root.toStringPretty()
    }

    private fun convertSnapshotToOpenApiSchema(snapshotElements: List<ElementDefinition>): SchemaOrReference {
        val root = convertSnapshotToTree(snapshotElements)
        return toOpenApiSchema(root)
    }

    private fun convertSnapshotToTree(snapshotElements: List<ElementDefinition>): FhirTreeNode {
        val treeNodes = snapshotElements.map { toTreeNode(it) }
        val root = linkNodes(treeNodes)
        val rootWithExplodedChoices = explodeTypeChoicesForDescendants(root)
        //TODO - hacky thing here - root node should not be a list
        return rootWithExplodedChoices.copy(isList = false)
    }

    private fun explodeTypeChoicesForDescendants(treeNode: FhirTreeNode): FhirTreeNode {
        return treeNode.copy(
            children = treeNode.children
                .map { explodeTypeChoicesForDescendants(it) }
                .flatMap { explodeTypeChoicesForNode(it) }
                .toMutableList(),
            slices = treeNode.slices
                .map { explodeTypeChoicesForDescendants(it) }
                .flatMap { explodeTypeChoicesForNode(it) }
                .toMutableList()
        )
    }

    private fun explodeTypeChoicesForNode(node: FhirTreeNode): List<FhirTreeNode> {
        return if (node.elementName.endsWith("[x]")) {
            if (node.types.isEmpty()) {
                throw IllegalStateException("Choice node with no choices")
            }
            node.types.map {
                val nodeWithType = copyNodeWithTypeForChoice(node, it)
                copySubtreeWithPathForChoice(nodeWithType, it)
            }
        } else {
            listOf(node)
        }
    }

    private fun copyNodeWithTypeForChoice(
        node: FhirTreeNode,
        type: ElementDefinition.TypeRefComponent
    ): FhirTreeNode {
        return node.copy(
            //TODO - hacky thing here - only one choice is required - could use oneOf, but the result would be horrible
            min = null,
            types = listOf(type)
        )
    }

    private fun copySubtreeWithPathForChoice(
        node: FhirTreeNode,
        type: ElementDefinition.TypeRefComponent
    ): FhirTreeNode {
        val typeChoice = type.code.replaceFirstChar { it.uppercase() }
        val index = node.path.lastIndexOf("[x]")
        val newPath = node.path.substring(0, index) + typeChoice + node.path.substring(index + "[x]".length)
        //Slices are intentionally not modified here as the paths seem to be correct.
        return node.copy(
            path = newPath,
            children = node.children.map { copySubtreeWithPathForChoice(it, type) }.toMutableList()
        )
    }

    private fun toTreeNode(elementDefinition: ElementDefinition): FhirTreeNode {
        val elementId = elementDefinition.id

        //TODO - discriminators are going to be difficult, do we need them?
        val discriminators = elementDefinition.slicing.discriminator
        val singleDiscriminator = discriminators.singleOrNull()
        val slicingDiscriminator = if (singleDiscriminator?.type == ElementDefinition.DiscriminatorType.VALUE) {
            singleDiscriminator.path
        } else {
            null
        }

        val baseMax = elementDefinition.base.max
        val isList = baseMax == "*" || baseMax.toInt() > 1

        val minAsOptionalInt = if (elementDefinition.min == 0) {
            null
        } else {
            elementDefinition.min
        }

        val maxAsOptionalInt = if (elementDefinition.max == "*") {
            null
        } else {
            elementDefinition.max.toInt()
        }

        //TODO - handle other binding strengths - add to description?
        val requiredBinding = elementDefinition.binding.takeIf { it.strength == Enumerations.BindingStrength.REQUIRED }
        val valueSetUrl = requiredBinding?.valueSet

        return FhirTreeNode(
            id = elementId,
            path = elementDefinition.path,
            isSliced = elementDefinition.hasSlicing(),
            slicingDiscriminator = slicingDiscriminator,
            sliceName = elementDefinition.sliceName,
            fixedValue = elementDefinition.fixed,
            valueSetUrl = valueSetUrl,
            isList = isList,
            min = minAsOptionalInt,
            max = maxAsOptionalInt,
            types = elementDefinition.type,
            contentReference = elementDefinition.contentReference,
            description = elementDefinition.definition
        )
    }

    private fun toOpenApiSchema(node: FhirTreeNode): SchemaOrReference {
        val schema = when {
            isObjectValuedNode(node) -> toOpenApiObjectSchema(node)
            isStringType(node.typeCode) -> toOpenApiStringSchema(node)
            isNumberType(node.typeCode) -> toOpenApiNumberSchema(node)
            isIntegerType(node.typeCode) -> toOpenApiIntegerSchema(node)
            isBooleanType(node.typeCode) -> toOpenApiBooleanSchema(node)
            node.profiles.isNotEmpty() -> toOpenApiProfileReferenceSchema(node)
            node.typeCode != null -> toOpenApiTypeReferenceSchema(node)
            node.contentReference != null -> toOpenApiPlaceholderContentReferenceSchema(node.contentReference)
            else -> throw IllegalStateException("No schema for node ${node.id}")
        }

        return if (node.isList) {
            toOpenApiArraySchema(node, schema)
        } else {
            schema
        }
    }

    private fun isObjectValuedNode(node: FhirTreeNode): Boolean {
        return node.children.isNotEmpty()
    }

    private fun isPrimitiveType(typeCode: String?): Boolean {
        return isStringType(typeCode) || isIntegerType(typeCode) || isNumberType(typeCode) || isBooleanType(typeCode)
    }

    private fun isStringType(typeCode: String?): Boolean {
        return STRING_TYPES.contains(typeCode)
    }

    private fun isNumberType(typeCode: String?): Boolean {
        return NUMBER_TYPES.contains(typeCode)
    }

    private fun isIntegerType(typeCode: String?): Boolean {
        return INTEGER_TYPES.contains(typeCode)
    }

    private fun isBooleanType(typeCode: String?): Boolean {
        return BOOLEAN_TYPES.contains(typeCode)
    }

    private fun toOpenApiObjectSchema(node: FhirTreeNode): ObjectSchema {
        if (node.fixedValue != null) {
            logger.warn { "Ignoring fixed value of type ${node.fixedValue.fhirType()} at path ${node.path}" }
        }
        val propertyPairs = node.children.map { Pair(it.elementName, toOpenApiSchema(it)) }
        val requiredProperties = node.children.filter { it.min != null && it.min > 0 }.map { it.elementName }
        return ObjectSchema(
            description = node.description,
            properties = propertyPairs.toMap(),
            required = requiredProperties
        )
    }

    private fun toOpenApiStringSchema(node: FhirTreeNode): StringSchema {
        val enum = when {
            node.fixedValue != null -> getFixedValueAsString(node.fixedValue)
            node.valueSetUrl != null -> getValueSetCodes(node.valueSetUrl)
            else -> null
        }
        return StringSchema(
            description = node.description,
            enum = enum,
            format = getStringFormatForType(node.typeCode)
        )
    }

    private fun getFixedValueAsString(fixedValue: Type): List<String> {
        return when {
            fixedValue is StringType -> listOf(fixedValue.value)
            fixedValue.hasPrimitiveValue() -> listOf(fixedValue.primitiveValue())
            else -> throw IllegalStateException("Unsupported fixed value type ${fixedValue.fhirType()}")
        }
    }

    private fun getValueSetCodes(valueSetUrl: String): List<String>? {
        val valueSet = validationSupportChain.fetchValueSet(valueSetUrl) as? ValueSet
        if (valueSet == null) {
            logger.warn { "No value set for URL $valueSetUrl" }
            return null
        }

        val expansionResult = validationSupportChain.expandValueSet(
            ValidationSupportContext(validationSupportChain),
            ValueSetExpansionOptions(),
            valueSet
        )
        val expandedValueSet = expansionResult?.valueSet as? ValueSet
        if (expandedValueSet == null || !expandedValueSet.hasExpansion()) {
            logger.warn { "Failed to expand value set $valueSetUrl" }
            return null
        }

        val valueSetContents = expandedValueSet.expansion
        if (valueSetContents.total > 50) {
            logger.warn("Too many codes for value set $valueSetUrl")
            return null
        }

        return valueSetContents.contains?.map { it.code }
    }

    private fun getStringFormatForType(typeCode: String?): String? {
        return when (typeCode) {
            "uri" -> "uri"
            "url" -> "uri"
            "canonical" -> "uri"
            "uuid" -> "uuid"
            "oid" -> "oid"
            "base64Binary" -> "byte"
            "instant" -> "date-time"
            "dateTime" -> "date-time"
            "date" -> "date"
            "time" -> "time"
            else -> null
        }
    }

    private fun toOpenApiNumberSchema(node: FhirTreeNode): NumberSchema {
        val enum = when {
            node.fixedValue == null -> null
            node.fixedValue is DecimalType -> listOf(node.fixedValue.value)
            else -> throw IllegalStateException("Unsupported fixed value type ${node.fixedValue.fhirType()}")
        }
        return NumberSchema(
            description = node.description,
            enum = enum
        )
    }

    private fun toOpenApiIntegerSchema(node: FhirTreeNode): IntegerSchema {
        val enum = when {
            node.fixedValue == null -> null
            node.fixedValue is IntegerType -> listOf(node.fixedValue.value)
            else -> throw IllegalStateException("Unsupported fixed value type ${node.fixedValue.fhirType()}")
        }
        return IntegerSchema(
            description = node.description,
            enum = enum,
            minimum = getMinimumValueForType(node.typeCode)
        )
    }

    private fun getMinimumValueForType(typeCode: String?): Int? {
        return when (typeCode) {
            "unsignedInt" -> 0
            "positiveInt" -> 1
            else -> null
        }
    }

    private fun toOpenApiBooleanSchema(node: FhirTreeNode): BooleanSchema {
        val enum = when (node.fixedValue) {
            null -> null
            is BooleanType -> listOf(node.fixedValue.booleanValue())
            else -> throw IllegalStateException("Unsupported fixed value type ${node.fixedValue.fhirType()}")
        }
        return BooleanSchema(
            description = node.description,
            enum = enum
        )
    }

    private fun toOpenApiProfileReferenceSchema(node: FhirTreeNode): SchemaOrReference {
        val references = node.profiles
            .map { getSchemaName(it) }
            .map { getAllowedModelName(it) }
            .map {
                referenceWithDescription(
                    "#/components/schemas/$it",
                    description = node.description
                )
            }
        return oneOf(references)
    }

    fun getSchemaName(url: String): String {
        return structureDefinitions.find { it.url == url }?.name
            ?: throw IllegalArgumentException("No StructureDefinition for URL $url")
    }

    private fun toOpenApiTypeReferenceSchema(node: FhirTreeNode): SchemaOrReference {
        if (node.typeCode == null) {
            throw IllegalArgumentException("No type for node")
        }
        val typeName = getAllowedModelName(node.typeCode)
        return referenceWithDescription(
            "#/components/schemas/$typeName",
            description = node.description
        )
    }

    fun getAllowedModelName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9.-_]"), "-")
    }

    fun referenceWithDescription(reference: String, description: String): SchemaOrReference {
        //TODO - hacky workaround - when OpenAPI 3.1.0 is supported, switch to the following:
        //return Reference(reference, description = description)
        val schemas = listOf(
            Reference(reference),
            Schema<Nothing>(description = description)
        )
        return allOf(schemas)
    }

    private fun toOpenApiPlaceholderContentReferenceSchema(contentReference: String): SchemaOrReference {
        //TODO - pull out any subtree which is the target of a content reference into its own model, update references
        //TODO - alternatively, fix the logic below
        return ObjectSchema(
            description = "PLACEHOLDER - For the schema of this element, see $contentReference.",
            properties = emptyMap()
        )
        //This doesn't work (need to specify the correct JSON pointer to the node within the OpenAPI schema)
//        val firstDotIndex = contentReference.indexOf(".")
//        if (!contentReference.startsWith("#") || firstDotIndex == -1) {
//            throw IllegalArgumentException("Unhandled format for content reference $contentReference")
//        }
//        val modelName = getAllowedModelName(contentReference.substring(1, firstDotIndex))
//        val pathToElement = contentReference.substring(firstDotIndex + 1).replace(".", "/")
//        return Reference("#/components/schemas/$modelName/$pathToElement")
    }

    private fun toOpenApiArraySchema(
        node: FhirTreeNode,
        itemSchema: SchemaOrReference
    ): ArraySchema {
        if (node.isSliced && node.slices.isNotEmpty()) {
            val sliceSchemas = node.slices
                //TODO - hacky thing here - items of sliced lists should not be lists
                .map { it.copy(isList = false) }
                .map { toOpenApiSchema(it) }
            val sliceChoiceSchema = oneOf(sliceSchemas)
            val commonAndSliceChoiceSchema = allOf(listOf(itemSchema, sliceChoiceSchema))
            return ArraySchema(
                items = commonAndSliceChoiceSchema,
                minItems = node.min,
                maxItems = node.max
            )
        } else {
            return ArraySchema(
                items = itemSchema,
                minItems = node.min,
                maxItems = node.max
            )
        }
    }

    fun oneOf(schemas: List<SchemaOrReference>): SchemaOrReference {
        return if (schemas.size > 1) {
            OneOfSchema(oneOf = schemas)
        } else {
            schemas.single()
        }
    }

    fun anyOf(schemas: List<SchemaOrReference>): SchemaOrReference {
        return if (schemas.size > 1) {
            AnyOfSchema(anyOf = schemas)
        } else {
            schemas.single()
        }
    }

    fun allOf(schemas: List<SchemaOrReference>): SchemaOrReference {
        return if (schemas.size > 1) {
            AllOfSchema(allOf = schemas)
        } else {
            schemas.single()
        }
    }

    fun linkNodes(nodes: List<FhirTreeNode>): FhirTreeNode {
        val root = nodes.first()
        val stack = Stack<FhirTreeNode>()
        for (node in nodes) {
            //Add the node to the appropriate slice group or parent
            if (node.sliceName != null) {
                val sliceGroupNode = popUntilFound(stack) { it.isSliced && it.path == node.path }
                    ?: throw IllegalStateException("No slice group node found for node ${node.path}")
                sliceGroupNode.slices.add(node)
            } else if (node != root) {
                val parentNode = popUntilFound(stack) { it.isParentOf(node) }
                    ?: throw IllegalStateException("No parent node found for node ${node.path}")
                parentNode.children.add(node)
            }

            //Add the node to the stack to be considered as the slice group or parent for the next node
            stack.push(node)
        }
        return root
    }

    fun <T> popUntilFound(stack: Stack<T>, predicate: Predicate<T>): T? {
        while (!stack.empty()) {
            val potentialResult = stack.peek()
            if (predicate.test(potentialResult)) {
                return potentialResult
            }
            stack.pop()
        }
        return null
    }
}

data class FhirTreeNode(
    val id: String,
    val path: String,
    val children: MutableList<FhirTreeNode> = mutableListOf(),
    val slices: MutableList<FhirTreeNode> = mutableListOf(),
    val isList: Boolean,
    val isSliced: Boolean,
    val slicingDiscriminator: String?,
    val sliceName: String?,
    val fixedValue: Type?,
    val valueSetUrl: String?,
    val min: Int?,
    val max: Int?,
    val types: List<ElementDefinition.TypeRefComponent>,
    val contentReference: String?,
    val description: String
) {
    private val pathParts: List<String> = path.split(".")
    val elementName = pathParts.last()

    private val type: ElementDefinition.TypeRefComponent? = types.singleOrNull()
    val typeCode = type?.code
    val profiles = type?.profile?.map { it.value }.orEmpty()
    //TODO - add these to description?
    val targetProfiles = type?.targetProfile?.map { it.value }.orEmpty()

    fun isParentOf(node: FhirTreeNode): Boolean {
        return node.pathParts.size == pathParts.size + 1
                && node.pathParts.subList(0, pathParts.size) == pathParts
    }

    private fun toStringPretty(indent: Int): String {
        val sb = StringBuilder()

        repeat(indent) { sb.append("|   ") }

        if (sliceName != null) {
            sb.append("SLICE ")
            sb.append(sliceName)
        } else {
            sb.append(pathParts.last())
            if (isList) {
                sb.append("[]")
            }
        }

        type?.let {
            sb.append(" (")
            sb.append(it)
            sb.append(")")
        }
        min?.let {
            sb.append(" min: ")
            sb.append(it)
        }
        max?.let {
            sb.append(" max: ")
            sb.append(it)
        }

        if (isSliced) {
            sb.append(" (SLICED")
            if (slicingDiscriminator != null) {
                sb.append(" on ")
                sb.append(slicingDiscriminator)
            }
            sb.append(")")
        }

        if (fixedValue != null) {
            sb.append(" \"")
            sb.append(fixedValue)
            sb.append("\"")
        }

        slices.forEach {
            sb.append("\n")
            sb.append(it.toStringPretty(indent + 1))
        }

        children.forEach {
            sb.append("\n")
            sb.append(it.toStringPretty(indent + 1))
        }

        return sb.toString()
    }
    fun toStringPretty(): String {
        return toStringPretty(0)
    }
}

fun getParentNodePath(nodePath: String): String {
    return nodePath.substringBeforeLast(".", "")
}
