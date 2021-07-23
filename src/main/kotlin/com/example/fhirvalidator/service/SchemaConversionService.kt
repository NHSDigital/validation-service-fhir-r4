package com.example.fhirvalidator.service

import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.r4.model.ElementDefinition
import org.hl7.fhir.r4.model.StructureDefinition
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.StringBuilder
import java.util.*
import java.util.function.Predicate

@Service
class SchemaConversionService(
    validationSupportChain: ValidationSupportChain
) {
    companion object : KLogging()

    private val structureDefinitions = validationSupportChain.fetchAllStructureDefinitions()
        .filterIsInstance(StructureDefinition::class.java)

    fun listCoolThings(): String {
        return structureDefinitions.map { it.name }.joinToString("\n")
    }

    fun doSomethingCool(name: String): String {
        val structureDefinition = structureDefinitions.find { it.name == name }
            ?: return "StructureDefinition not found"
        return structureDefinition.snapshot?.element?.let { convertSnapshotToJsonSchema(it) }
            ?: return "StructureDefinition missing snapshot"
    }

    private fun convertSnapshotToJsonSchema(snapshotElements: List<ElementDefinition>): String {
        val treeNodes = snapshotElements.map { toTreeNode(it) }
        val root = linkNodes(treeNodes)
        return root.toStringPretty()
    }

    private fun toTreeNode(elementDefinition: ElementDefinition): FhirTreeNode {
        val elementId = elementDefinition.id

        val discriminators = elementDefinition.slicing.discriminator
        val slicingDiscriminator = if (discriminators.isNotEmpty()) {
            val discriminator = discriminators.single()
            if (discriminator.type != ElementDefinition.DiscriminatorType.VALUE) {
                throw IllegalArgumentException("Unsupported discriminator type")
            }
            discriminator.path
        } else {
            null
        }

        val baseMax = elementDefinition.base.max
        val isList = baseMax == "*" || baseMax.toInt() > 1

        val maxAsInt = if (elementDefinition.max == "*") {
            null
        } else {
            elementDefinition.max.toInt()
        }

        val types = elementDefinition.type
        val type = if (types.isNotEmpty()) {
            if (types.size > 1) {
                logger.warn { "More than one type for node ${elementId}" }
            }
            types.first()
        } else {
            null
        }

        val typeCode = type?.code
        val profile = type?.profile?.map { it.value }
        val targetProfile = type?.targetProfile?.map { it.value }

        return FhirTreeNode(
            id = elementId,
            path = elementDefinition.path,
            isSliced = elementDefinition.hasSlicing(),
            slicingDiscriminator = slicingDiscriminator,
            sliceName = elementDefinition.sliceName,
            fixedValue = elementDefinition.fixed,
            isList = isList,
            min = elementDefinition.min,
            max = maxAsInt,
            type = typeCode,
            profile = profile,
            targetProfile = targetProfile
        )
    }

    fun linkNodes(nodes: List<FhirTreeNode>): FhirTreeNode {
        val root = nodes.first()
        val stack = Stack<FhirTreeNode>()
        for (node in nodes) {
            //Add the node to the appropriate slice group or parent
            if (node.sliceName != null) {
                val sliceGroupNode = popUntilFound(stack, Predicate { it.isSliced && it.path == node.path })
                    ?: throw IllegalStateException("No slice group node found for node ${node.path}")
                sliceGroupNode.slices.add(node)
            } else if (node != root) {
                val parentNode = popUntilFound(stack, Predicate { it.isParentOf(node) })
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
    val children: MutableList<FhirTreeNode> = mutableListOf<FhirTreeNode>(),
    val slices: MutableList<FhirTreeNode> = mutableListOf<FhirTreeNode>(),
    val isList: Boolean,
    val isSliced: Boolean,
    val slicingDiscriminator: String?,
    val sliceName: String?,
    val fixedValue: Any?,
    val min: Int?,
    val max: Int?,
    val type: String?,
    val profile: List<String>?,
    val targetProfile: List<String>?
) {
    private val pathParts: List<String> = path.split(".")

    fun findChildren(nodes: List<FhirTreeNode>): List<FhirTreeNode> {
        return nodes.filter { it.isChildOf(this) }
    }

    fun isChildOf(node: FhirTreeNode): Boolean {
        return this.pathParts.size == node.pathParts.size + 1
                && this.pathParts.subList(0, node.pathParts.size) == node.pathParts
    }

    fun isParentOf(node: FhirTreeNode): Boolean {
        return node.isChildOf(this)
    }

    fun isSliceOf(node: FhirTreeNode): Boolean {
        return this.path == node.path && node.isSliced && this.sliceName != null
    }

    private fun toStringPretty(indent: Int): String {
        val sb = StringBuilder()

        repeat(indent) { sb.append("|   ") }

        if (sliceName != null) {
            sb.append("SLICE ")
            sb.append(sliceName)
        } else {
            sb.append(pathParts.last())
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
            sb.append(" (SLICED)")
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

