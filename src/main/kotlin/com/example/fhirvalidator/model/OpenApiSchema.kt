package com.example.fhirvalidator.model

open class SchemaOrReference()

class Reference(
    val `$ref`: String
): SchemaOrReference()

abstract class AbstractSchema<ExampleType>(
    val type: String? = null,
    val description: String? = null,
    val nullable: Boolean? = null,
    val default: ExampleType? = null,
    val enum: List<ExampleType>? = null,
    val example: ExampleType? = null
): SchemaOrReference()

class Discriminator(
    val propertyName: String,
    val mapping: Map<String, SchemaOrReference>? = null
)

class OneOfSchema(
    description: String? = null,
    val oneOf: List<SchemaOrReference>,
    val discriminator: Discriminator? = null
): AbstractSchema<Any>(
    description = description
)

class AnyOfSchema(
    description: String? = null,
    val anyOf: List<SchemaOrReference>,
    val discriminator: Discriminator? = null
): AbstractSchema<Any>(
    description = description
)

class AllOfSchema(
    description: String? = null,
    val allOf: List<SchemaOrReference>
): AbstractSchema<Any>(
    description = description
)

class NegatedSchema(
    description: String? = null,
    val not: SchemaOrReference
): AbstractSchema<Any>(
    description = description
)

class NumberSchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: Number? = null,
    enum: List<Number>? = null,
    example: Number? = null,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val exclusiveMinimum: Boolean? = null,
    val exclusiveMaximum: Boolean? = null,
    val multipleOf: Number? = null
): AbstractSchema<Number>(
    "number",
    description,
    nullable,
    default,
    enum,
    example
)

class IntegerSchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: Int? = null,
    enum: List<Int>? = null,
    example: Int? = null,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val exclusiveMinimum: Boolean? = null,
    val exclusiveMaximum: Boolean? = null,
    val multipleOf: Number? = null
): AbstractSchema<Int>(
    "integer",
    description,
    nullable,
    default,
    enum,
    example
)

class StringSchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: String? = null,
    enum: List<String>? = null,
    example: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val format: String? = null,
    val pattern: String? = null
): AbstractSchema<String>(
    "string",
    description,
    nullable,
    default,
    enum,
    example
)

class BooleanSchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: Boolean? = null,
    enum: List<Boolean>? = null,
    example: Boolean? = null
): AbstractSchema<Boolean>(
    "boolean",
    description,
    nullable,
    default,
    enum,
    example
)

class ArraySchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: List<Any>? = null,
    enum: List<List<Any>>? = null,
    example: List<Any>? = null,
    val items: SchemaOrReference,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val uniqueItems: Boolean? = null
): AbstractSchema<List<Any>>(
    "array",
    description,
    nullable,
    default,
    enum,
    example
)

class ObjectSchema(
    description: String? = null,
    nullable: Boolean? = null,
    default: Map<String, Any>? = null,
    enum: List<Map<String, Any>>? = null,
    example: Map<String, Any>? = null,
    val properties: Map<String, SchemaOrReference>,
    val required: List<String>? = null
): AbstractSchema<Map<String, Any>>(
    "object",
    description,
    nullable,
    default,
    enum,
    example
)
